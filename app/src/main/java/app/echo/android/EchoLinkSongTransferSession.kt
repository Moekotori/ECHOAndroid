package app.echo.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import app.echo.android.connect.*
import app.echo.android.data.EchoLinkSongFiles
import app.echo.android.data.EchoLinkSongReceiver
import app.echo.android.data.WifiTransferDirectoryStore
import app.echo.android.model.connect.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** The transfer panel owns all work; a reconnect always requires a new server lease. */
internal class EchoLinkSongTransferSession(context: Context, private val client: EchoRemoteClient) : AutoCloseable {
    private val application = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val directories = WifiTransferDirectoryStore(application)
    private val files = EchoLinkSongFiles(application)
    private val mutable = MutableStateFlow(EchoSongTransferState())
    val state = mutable.asStateFlow()
    var directoryUri: Uri? = null
        private set
    private var transport: EchoLinkSongTransferTransport? = null
    private var sessionId: String? = null
    private var connectionId = 0L
    @Volatile private var generation = 0
    private var sessionJob: Job? = null
    private var uploadJob: Job? = null
    private var downloadJob: Job? = null

    init {
        scope.launch {
            try {
                val saved = directories.load() ?: return@launch
                val uri = Uri.parse(saved)
                val name = withContext(Dispatchers.IO) { EchoLinkSongReceiver(application, uri).directoryName() }
                if (directoryUri == null) { directoryUri = uri; mutable.update { it.copy(directory = name) } }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(error = "directory") } }
        }
        scope.launch {
            client.status.map { Triple(it.connectionId, it.connectionState, it.endpoint?.libraryOnly == true) }
                .distinctUntilChanged().collect { (id, status, libraryOnly) ->
                    if ((mutable.value.active || mutable.value.starting) &&
                        (id != connectionId || status != EchoRemoteConnectionState.Connected || libraryOnly)) stop("connection")
                }
        }
    }

    fun chooseDirectory(uri: Uri) {
        if (mutable.value.current != null || mutable.value.sending) return
        scope.launch {
            try {
                val name = withContext(Dispatchers.IO) {
                    application.contentResolver.takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    EchoLinkSongReceiver(application, uri).directoryName()
                }
                directories.save(uri.toString()); directoryUri = uri
                mutable.update { it.copy(directory = name, error = null) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(error = "directory") } }
        }
    }

    fun start() {
        val status = client.status.value
        val endpoint = status.endpoint
        if (status.connectionState != EchoRemoteConnectionState.Connected || endpoint == null || endpoint.libraryOnly) {
            mutable.update { it.copy(error = "connection") }; return
        }
        stop()
        connectionId = status.connectionId
        val version = generation
        val network = EchoLinkSongTransferTransport(endpoint)
        transport = network
        mutable.update { it.copy(starting = true, error = null) }
        sessionJob = scope.launch {
            try {
                val id = network.start(Build.MODEL.ifBlank { "ECHO Android" }, directoryUri != null)
                ensureCurrent(version)
                sessionId = id
                mutable.update { it.copy(starting = false, active = true) }
                while (isActive) {
                    ensureCurrent(version)
                    val items = network.poll(id, directoryUri != null)
                    ensureCurrent(version)
                    mutable.update { it.copy(items = items) }
                    val destination = directoryUri
                    if (destination != null && uploadJob?.isActive != true && downloadJob?.isActive != true) {
                        items.firstOrNull { it.direction == "to-phone" && it.state == "queued" }?.let {
                            receive(network, id, it, destination, version)
                        }
                    }
                    delay(2000)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { if (generation == version) stop(errorCode(error)) }
        }
    }

    fun send(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (uris.size > EchoSongTransferPolicy.MaxBatchFiles) { mutable.update { it.copy(error = "files") }; return }
        val network = transport ?: return; val id = sessionId ?: return; val version = generation
        if (uploadJob?.isActive == true || downloadJob?.isActive == true || !mutable.value.active) return
        mutable.update { it.copy(sending = true, error = null) }
        uploadJob = scope.launch {
            try {
                for (uri in uris) {
                    ensureCurrent(version)
                    val file = withContext(Dispatchers.IO) { files.describe(uri) }
                    val item = EchoSongTransferItem("upload", file.name, file.size, direction = "to-pc", state = "transferring")
                    mutable.update { it.copy(current = item) }
                    network.upload(id, file.name, file.size, { files.open(file) }, progress(item, version))
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { if (generation == version) mutable.update { it.copy(error = errorCode(error)) } }
            finally { if (generation == version) mutable.update { it.copy(sending = false, current = null) } }
        }
    }

    private fun receive(network: EchoLinkSongTransferTransport, id: String, item: EchoSongTransferItem, destination: Uri, version: Int) {
        mutable.update { it.copy(current = item.copy(state = "transferring")) }
        downloadJob = scope.launch {
            try {
                val update = progress(item.copy(state = "transferring"), version)
                network.download(id, item) { name, size, input, check ->
                    EchoLinkSongReceiver(application, destination).receive(name, size, input, check, update)
                }
                ensureCurrent(version)
                mutable.update { it.copy(received = it.received + 1) }
                network.acknowledge(id, item.id, true)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (generation == version) {
                    mutable.update { it.copy(error = errorCode(error)) }
                    try { network.acknowledge(id, item.id, false) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { /* The connection lease also expires without an acknowledgement. */ }
                }
            } finally { if (generation == version) mutable.update { it.copy(current = null) } }
        }
    }
    private fun progress(item: EchoSongTransferItem, version: Int): (Long) -> Unit {
        var last = 0L
        return { bytes ->
            val now = SystemClock.elapsedRealtime()
            if (now - last >= 200 || bytes == item.size) {
                last = now
                mutable.update { if (generation == version && it.active) it.copy(current = item.copy(bytes = bytes)) else it }
            }
        }
    }
    private fun ensureCurrent(version: Int) {
        val status = client.status.value
        check(version == generation && status.connectionId == connectionId && status.connectionState == EchoRemoteConnectionState.Connected) { "connection" }
    }
    private fun errorCode(error: Exception): String = when {
        error is EchoSongTransferException && error.status in listOf(404, 405, 501) -> "unsupported"
        error is EchoSongTransferException && error.status in listOf(401, 403, 410) -> "connection"
        error is IllegalArgumentException -> "files"
        error.message == "connection" -> "connection"
        else -> "transfer"
    }
    fun stop(error: String? = null) {
        generation++
        sessionJob?.cancel(); uploadJob?.cancel(); downloadJob?.cancel()
        sessionJob = null; uploadJob = null; downloadJob = null
        val old = transport; val id = sessionId
        transport = null; sessionId = null
        old?.cancelActive()
        mutable.update { it.copy(active = false, starting = false, sending = false, current = null, error = error,
            items = it.items.map { item -> if (item.state in listOf("queued", "transferring")) item.copy(state = "cancelled") else item }) }
        if (old != null) scope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                try { if (id != null) withTimeoutOrNull(1500) {
                    try { old.end(id) } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { /* Lost connections expire on the PC within the lease window. */ }
                } }
                finally { old.close() }
            }
        }
    }
    override fun close() { stop(); scope.cancel() }
}
