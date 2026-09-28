package app.echo.android.listening

import app.echo.android.model.listening.EchoListeningAudio
import app.echo.android.model.listening.EchoListeningConnection
import app.echo.android.model.listening.EchoListeningError
import app.echo.android.model.listening.EchoListeningRoom
import app.echo.android.model.listening.EchoListeningState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

internal class ListeningRequestException(val code: EchoListeningError) : Exception()

class EchoListeningController internal constructor(
    private val scope: CoroutineScope,
    private val transport: EchoListeningTransport,
    private val sink: EchoListeningAudioSink,
    private val notifier: EchoListeningNotifier = EchoListeningNoNotifier,
    private val outputAllowed: () -> Boolean = { true },
    private val wait: suspend (Long) -> Unit = { delay(it) },
) {
    private val stateFlow = MutableStateFlow(EchoListeningState())
    val state: StateFlow<EchoListeningState> = stateFlow.asStateFlow()

    private val requestMutex = Mutex()
    private var waiter: Waiter? = null
    private var nextId = 0
    private var generation = 0
    private var established = false
    private var resumeToken: String? = null
    private var serverPassword: String? = null
    private var socketUrl: String? = null
    private var displayName: String = "ECHO"
    private var pendingInvitation: String? = null
    private var audioEpoch = 0L
    private var yielded = false
    private var acceptLocalYield = true
    private var localActive = false
    private var refreshJob: Job? = null
    private var reconnectJob: Job? = null

    init {
        transport.setBinaryListener(::onBinary)
        scope.launch {
            transport.incoming.collect { inbound ->
                when (inbound) {
                    is EchoListeningInbound.Text -> onText(inbound.body)
                    is EchoListeningInbound.Binary -> onBinary(inbound.body)
                    is EchoListeningInbound.Closed -> onClosed()
                }
            }
        }
        scope.launch {
            sink.phase.collect { phase ->
                val room = stateFlow.value.room
                if (yielded || room?.playing != true) return@collect
                update {
                    it.copy(
                        audio = phase,
                        error = when {
                            phase == EchoListeningAudio.Error -> EchoListeningError.PlaybackFailed
                            it.error == EchoListeningError.PlaybackFailed &&
                                (phase == EchoListeningAudio.Buffering || phase == EchoListeningAudio.Receiving) -> null
                            else -> it.error
                        },
                    )
                }
            }
        }
    }

    suspend fun connect(input: String, name: String, serverPassword: String?) {
        val invite = EchoListeningCodes.parse(input)
        val url = invite?.let { EchoListeningCodes.socketUrl(it.server) }
        val safeName = EchoListeningCodes.sanitizeName(name)
        if (invite == null || url == null || safeName == null) {
            reject(EchoListeningError.InvalidInput)
            return
        }
        val gen = ++generation
        reconnectJob?.cancel()
        established = false
        yielded = false
        acceptLocalYield = true
        this.serverPassword = serverPassword?.takeIf { it.isNotEmpty() }
        displayName = safeName
        socketUrl = url
        pendingInvitation = invite.invitation
        resumeToken = null
        stopAudio(hideNotification = true)
        update {
            EchoListeningState(
                connection = EchoListeningConnection.Connecting,
                server = EchoListeningCodes.displayServer(url),
                volume = it.volume,
            )
        }
        transport.close()
        try {
            transport.open(url)
            if (gen != generation) return
            val hello = requestObject("hello", helloBody(resume = false))
            if (gen != generation) return
            val capabilities = EchoListeningJson.capabilities(hello)
            if (!capabilities.acceptsGuestAudio) {
                reject(EchoListeningError.ServerTooOld)
                return
            }
            resumeToken = hello.optString("resumeToken").takeIf { it.isNotEmpty() }
            established = true
            update {
                it.copy(
                    connection = EchoListeningConnection.Online,
                    serverName = hello.optString("name").take(80),
                    peerId = hello.optString("peerId").take(80),
                    chatEnabled = capabilities.chat,
                    error = null,
                )
            }
            refreshRooms()
            val roomId = invite.roomId
            if (roomId != null) join(roomId, password = null, invitation = invite.invitation)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: ListeningRequestException) {
            if (gen == generation) reject(error.code)
        } catch (_: Exception) {
            if (gen == generation) reject(EchoListeningError.ServerUnreachable)
        }
    }

    suspend fun refreshRooms() {
        if (!established || stateFlow.value.room != null) return
        val result = requestAny("rooms", JSONObject())
        update { it.copy(rooms = EchoListeningJson.roomSummaries(result)) }
    }

    suspend fun join(roomId: String, password: String?, invitation: String?) {
        if (!established) {
            update { it.copy(error = EchoListeningError.NotConnected) }
            return
        }
        val data = JSONObject().put("roomId", roomId)
        if (!password.isNullOrEmpty()) data.put("password", password)
        if (!invitation.isNullOrEmpty()) data.put("invitation", invitation)
        try {
            val result = requestAny("join", data)
            pendingInvitation = null
            applyRoom(EchoListeningJson.room(result, stateFlow.value.peerId), clearChat = true)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: ListeningRequestException) {
            update {
                it.copy(
                    error = error.code,
                    passwordRoomId = if (error.code == EchoListeningError.WrongPassword) roomId else it.passwordRoomId,
                )
            }
        }
    }

    fun dismissPassword() {
        update {
            it.copy(
                passwordRoomId = null,
                error = if (it.error == EchoListeningError.WrongPassword) null else it.error,
            )
        }
    }

    suspend fun leave() {
        if (!established || stateFlow.value.room == null) return
        yielded = false
        stopAudio(hideNotification = true)
        try {
            requestAny("leave", JSONObject())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: ListeningRequestException) {
            // A failed leave still drops the local room. The server push is authoritative.
        }
        update {
            it.copy(room = null, chat = emptyList(), audio = EchoListeningAudio.Off, yieldedToLocal = false)
        }
    }

    fun disconnect() {
        generation++
        reconnectJob?.cancel()
        established = false
        resumeToken = null
        serverPassword = null
        pendingInvitation = null
        stopAudio(hideNotification = true)
        transport.close()
        update { EchoListeningState(volume = it.volume) }
    }

    suspend fun sendChat(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > 500 || stateFlow.value.room == null || !established) {
            if (trimmed.length > 500) update { it.copy(error = EchoListeningError.InvalidChat) }
            return
        }
        try {
            requestAny("chat", JSONObject().put("text", trimmed))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: ListeningRequestException) {
            update { it.copy(error = error.code) }
        }
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        sink.setVolume(clamped)
        update { it.copy(volume = clamped) }
    }

    fun noteLocalPlayback(active: Boolean) {
        localActive = active
        if (!active) {
            acceptLocalYield = true
            return
        }
        if (acceptLocalYield) suspendForLocalPlayback()
    }

    fun suspendForLocalPlayback() {
        if (!acceptLocalYield || stateFlow.value.room == null) return
        yielded = true
        stopAudio(hideNotification = true)
        update { it.copy(audio = EchoListeningAudio.Off, yieldedToLocal = true) }
    }

    fun resumeAfterLocalPlayback() {
        acceptLocalYield = !localActive
        yielded = false
        update { it.copy(yieldedToLocal = false, error = null) }
        val room = stateFlow.value.room ?: return
        startAudio(room)
    }

    private fun onText(body: String) {
        val json = try {
            JSONObject(body)
        } catch (_: Exception) {
            return
        }
        if (json.has("id")) {
            deliver(json)
            return
        }
        when (json.optString("type")) {
            "room" -> applyRoom(
                room = if (json.isNull("room")) null else EchoListeningJson.room(json.opt("room"), stateFlow.value.peerId),
                clearChat = false,
            )
            "chat" -> {
                val message = EchoListeningJson.chat(json.opt("message"), stateFlow.value.peerId) ?: return
                if (message.roomId != stateFlow.value.room?.id) return
                update { it.copy(chat = (it.chat + message).takeLast(MAX_CHAT)) }
            }
            "rooms-changed" -> scheduleRoomRefresh()
        }
    }

    private fun deliver(json: JSONObject) {
        val pending = waiter ?: return
        if (pending.id != json.optString("id")) return
        waiter = null
        if (json.has("error") && !json.isNull("error")) {
            pending.deferred.completeExceptionally(
                ListeningRequestException(EchoListeningJson.errorCode(json.optString("error"))),
            )
        } else if (!json.has("result") || json.isNull("result")) {
            pending.deferred.complete(null)
        } else {
            pending.deferred.complete(json.get("result"))
        }
    }

    private fun onBinary(body: ByteArray) {
        if (yielded) return
        val room = stateFlow.value.room ?: return
        if (!room.playing || audioEpoch == 0L) return
        val packet = EchoListeningPackets.parse(body) ?: return
        if (packet.epoch != room.streamEpoch || packet.epoch != audioEpoch) return
        sink.offer(packet)
    }

    private fun onClosed() {
        waiter?.deferred?.completeExceptionally(ListeningRequestException(EchoListeningError.ConnectionClosed))
        waiter = null
        if (!established) return
        val gen = generation
        established = false
        stopAudio(hideNotification = false)
        scheduleReconnect(gen)
    }

    private fun scheduleReconnect(gen: Int) {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            update {
                it.copy(connection = EchoListeningConnection.Reconnecting, audio = EchoListeningAudio.Off, error = null)
            }
            for (pause in longArrayOf(1_000, 3_000, 8_000)) {
                wait(pause)
                if (gen != generation) return@launch
                val url = socketUrl ?: break
                try {
                    transport.open(url)
                    if (gen != generation) return@launch
                    val hello = requestObject("hello", helloBody(resume = true))
                    if (!EchoListeningJson.capabilities(hello).acceptsGuestAudio) {
                        reject(EchoListeningError.ServerTooOld)
                        return@launch
                    }
                    resumeToken = hello.optString("resumeToken").takeIf { it.isNotEmpty() } ?: resumeToken
                    established = true
                    update {
                        it.copy(
                            connection = EchoListeningConnection.Online,
                            peerId = hello.optString("peerId").take(80).ifEmpty { it.peerId },
                            chatEnabled = EchoListeningJson.capabilities(hello).chat,
                            error = null,
                        )
                    }
                    if (stateFlow.value.room == null) refreshRooms()
                    return@launch
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    established = false
                    transport.close()
                }
            }
            if (gen == generation) {
                established = false
                resumeToken = null
                stopAudio(hideNotification = true)
                update {
                    it.copy(
                        connection = EchoListeningConnection.Offline,
                        room = null,
                        chat = emptyList(),
                        audio = EchoListeningAudio.Off,
                        error = EchoListeningError.ConnectionClosed,
                    )
                }
            }
        }
    }

    private fun scheduleRoomRefresh() {
        if (stateFlow.value.room != null || !established) return
        refreshJob?.cancel()
        refreshJob = scope.launch {
            delay(400)
            try {
                refreshRooms()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The next rooms-changed hint can refresh the lobby again.
            }
        }
    }

    private fun applyRoom(room: EchoListeningRoom?, clearChat: Boolean) {
        val previousId = stateFlow.value.room?.id
        update {
            it.copy(
                room = room,
                chat = if (room == null || clearChat || room.id != previousId) emptyList() else it.chat,
                error = if (room == null) it.error else null,
                passwordRoomId = if (room == null) it.passwordRoomId else null,
            )
        }
        if (room == null) {
            yielded = false
            stopAudio(hideNotification = true)
            update { it.copy(audio = EchoListeningAudio.Off, yieldedToLocal = false) }
            return
        }
        if (!yielded) {
            val title = room.track?.title?.ifBlank { null } ?: room.title.ifBlank { room.name }
            notifier.show(title, room.track?.artist.orEmpty())
        }
        startAudio(room)
    }

    private fun startAudio(room: EchoListeningRoom) {
        if (!room.playing || yielded) {
            val hide = yielded
            if (audioEpoch != 0L || hide) stopAudio(hideNotification = hide)
            audioEpoch = 0L
            update {
                it.copy(
                    audio = when {
                        yielded -> EchoListeningAudio.Off
                        else -> EchoListeningAudio.Paused
                    },
                )
            }
            return
        }
        if (!outputAllowed()) {
            stopAudio(hideNotification = false)
            audioEpoch = 0L
            update { it.copy(audio = EchoListeningAudio.Error, error = EchoListeningError.OutputBusy) }
            return
        }
        val title = room.track?.title?.ifBlank { null } ?: room.title.ifBlank { room.name }
        val audio = stateFlow.value.audio
        val alreadyPlaying = audioEpoch == room.streamEpoch &&
            (audio == EchoListeningAudio.Buffering || audio == EchoListeningAudio.Receiving)
        if (alreadyPlaying) {
            notifier.show(title, room.track?.artist.orEmpty())
            return
        }
        audioEpoch = room.streamEpoch
        sink.setVolume(stateFlow.value.volume)
        sink.startEpoch(room.streamEpoch)
        notifier.show(title, room.track?.artist.orEmpty())
        update { it.copy(audio = EchoListeningAudio.Buffering, error = null) }
    }

    private fun stopAudio(hideNotification: Boolean) {
        audioEpoch = 0L
        sink.stop()
        if (hideNotification) notifier.hide()
    }

    private fun helloBody(resume: Boolean): JSONObject {
        val body = JSONObject().put("protocol", 1).put("name", displayName)
        serverPassword?.let { body.put("password", it) }
        if (resume) resumeToken?.let { body.put("resumeToken", it) }
        return body
    }

    private suspend fun requestObject(type: String, data: JSONObject): JSONObject =
        requestAny(type, data) as? JSONObject ?: JSONObject()

    private suspend fun requestAny(type: String, data: JSONObject): Any? {
        val encoded = JSONObject().put("type", type).put("data", data)
        return requestMutex.withLock {
            if (!established && type != "hello") throw ListeningRequestException(EchoListeningError.NotConnected)
            val id = (++nextId).toString()
            encoded.put("id", id)
            val text = encoded.toString()
            if (text.length > 8192) throw ListeningRequestException(EchoListeningError.InvalidInput)
            val deferred = CompletableDeferred<Any?>()
            waiter = Waiter(id, deferred)
            try {
                transport.send(text)
                try {
                    withTimeout(10_000) { deferred.await() }
                } catch (timeout: TimeoutCancellationException) {
                    throw ListeningRequestException(EchoListeningError.RequestTimeout)
                }
            } finally {
                if (waiter?.id == id) waiter = null
            }
        }
    }

    private fun reject(code: EchoListeningError) {
        generation++
        reconnectJob?.cancel()
        established = false
        stopAudio(hideNotification = true)
        transport.close()
        update {
            EchoListeningState(
                connection = EchoListeningConnection.Offline,
                server = it.server,
                volume = it.volume,
                error = code,
            )
        }
    }

    private fun update(block: (EchoListeningState) -> EchoListeningState) {
        stateFlow.value = block(stateFlow.value)
    }

    private data class Waiter(
        val id: String,
        val deferred: CompletableDeferred<Any?>,
    )

    private companion object {
        const val MAX_CHAT = 50
    }
}
