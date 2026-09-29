package app.echo.android.connect

import app.echo.android.model.connect.EchoLanRenderer
import app.echo.android.model.connect.EchoRemoteStreamItem
import java.io.BufferedInputStream
import java.io.OutputStream
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

class EchoChromecastClient {
    @Volatile private var socket: SSLSocket? = null
    @Volatile private var transportId: String? = null
    @Volatile private var mediaSessionId: Int? = null
    @Volatile private var requestId: Int = 1
    @Volatile private var paused: Boolean = false
    private val lock = Any()

    /**
     * 接收端状态回调，在读线程上调用，调用方自行切线程。
     * 会话断开时以 [EchoCastMediaStatus.playerState] = "CLOSED" 通知一次。
     */
    @Volatile var onMediaStatus: ((EchoCastMediaStatus) -> Unit)? = null
    @Volatile var onVolume: ((Float) -> Unit)? = null

    fun isActive(): Boolean = socket?.isClosed == false

    fun play(
        renderer: EchoLanRenderer,
        item: EchoRemoteStreamItem,
        positionMs: Long = 0L,
    ) {
        EchoChromecastCastPolicy.rejectReason(renderer, item)?.let { reason ->
            throw EchoDlnaException(415, reason.code)
        }
        synchronized(lock) {
            closeLocked()
            val port = renderer.port.takeIf { it > 0 } ?: EchoChromecastCastPolicy.DefaultPort
            val opened = openSocket(renderer.host, port)
            socket = opened
            val reader = BufferedInputStream(opened.inputStream)
            val output = opened.outputStream
            requestId = 1
            write(output, EchoCastProtocol.ReceiverId, EchoCastProtocol.ConnectionNamespace, EchoCastProtocol.connectJson())
            write(
                output,
                EchoCastProtocol.ReceiverId,
                EchoCastProtocol.ReceiverNamespace,
                EchoCastProtocol.launchJson(nextRequestId(), EchoChromecastCastPolicy.DefaultReceiverAppId),
            )
            val transport = awaitTransportId(reader, output) ?: throw EchoDlnaException(500, "chromecast_unsupported")
            transportId = transport
            write(output, transport, EchoCastProtocol.ConnectionNamespace, EchoCastProtocol.connectJson())
            sendLoadLocked(item, positionMs, output)
            mediaSessionId = awaitMediaSessionId(reader, output) ?: mediaSessionId
            paused = false
            Thread({ readLoop(opened, reader) }, "echo-cast-reader").apply { isDaemon = true }.start()
            Thread({ heartbeatLoop(opened) }, "echo-cast-heartbeat").apply { isDaemon = true }.start()
        }
    }

    fun pause() = mediaCommand { id, session -> EchoCastProtocol.pauseJson(id, session) }.also { paused = true }

    fun resume() = mediaCommand { id, session -> EchoCastProtocol.resumeJson(id, session) }.also { paused = false }

    fun togglePause() {
        if (paused) resume() else pause()
    }

    fun seek(positionMs: Long) = mediaCommand { id, session -> EchoCastProtocol.seekJson(id, session, positionMs) }

    /** 设备音量，0..1。Chromecast 的音量属于接收端，不属于媒体会话。 */
    fun setVolume(level: Float) {
        synchronized(lock) {
            val output = socket?.outputStream ?: return
            write(
                output,
                EchoCastProtocol.ReceiverId,
                EchoCastProtocol.ReceiverNamespace,
                EchoCastProtocol.setVolumeJson(nextRequestId(), level),
            )
        }
    }

    fun stop() {
        runCatching { mediaCommand { id, session -> EchoCastProtocol.stopJson(id, session) } }
        close()
    }

    /** 切到另一首。新的 mediaSessionId 由读线程从 MEDIA_STATUS 里取得。 */
    fun playNext(item: EchoRemoteStreamItem) {
        EchoChromecastCastPolicy.rejectReason(item)?.let { reason ->
            throw EchoDlnaException(415, reason.code)
        }
        synchronized(lock) {
            val opened = socket ?: throw EchoDlnaException(500, "chromecast_closed")
            sendLoadLocked(item, 0L, opened.outputStream)
            paused = false
        }
    }

    fun close() {
        synchronized(lock) { closeLocked() }
    }

    private fun sendLoadLocked(
        item: EchoRemoteStreamItem,
        positionMs: Long,
        output: OutputStream,
    ) {
        val transport = transportId ?: return
        write(
            output,
            transport,
            EchoCastProtocol.MediaNamespace,
            EchoCastProtocol.loadJson(
                requestId = nextRequestId(),
                contentId = item.streamUrl,
                contentType = EchoDlnaCastPolicy.offeredMime(item),
                positionMs = positionMs,
                title = item.title,
                artist = item.artist,
            ),
        )
    }

    private fun mediaCommand(body: (requestId: Int, sessionId: Int) -> String) {
        synchronized(lock) {
            val output = socket?.outputStream ?: return
            val transport = transportId ?: return
            val session = mediaSessionId ?: return
            write(output, transport, EchoCastProtocol.MediaNamespace, body(nextRequestId(), session))
        }
    }

    /** 唯一的读者。play() 的同步握手结束后才启动，避免两处同时读同一个流。 */
    private fun readLoop(owner: SSLSocket, reader: BufferedInputStream) {
        while (socket === owner && !owner.isClosed) {
            val message = try {
                EchoCastProtocol.read(reader)
            } catch (_: SocketTimeoutException) {
                continue
            } catch (_: Exception) {
                break
            }
            if (message.namespace == EchoCastProtocol.HeartbeatNamespace && message.payloadUtf8.contains("PING")) {
                synchronized(lock) {
                    if (socket === owner) {
                        runCatching {
                            write(owner.outputStream, message.sourceId, EchoCastProtocol.HeartbeatNamespace, """{"type":"PONG"}""")
                        }
                    }
                }
                continue
            }
            EchoCastStatusParser.media(message.payloadUtf8)?.let { status ->
                status.mediaSessionId?.let { mediaSessionId = it }
                when (status.playerState) {
                    "PAUSED" -> paused = true
                    "PLAYING", "BUFFERING" -> paused = false
                }
                onMediaStatus?.invoke(status)
            }
            EchoCastStatusParser.receiverVolume(message.payloadUtf8)?.let { level -> onVolume?.invoke(level) }
        }
        if (socket === owner) {
            close()
            onMediaStatus?.invoke(EchoCastMediaStatus(null, "CLOSED", null, null, null))
        }
    }

    /** 心跳顺带拉一次接收端状态（音量）和媒体状态，接收端只在变化时才主动推送。 */
    private fun heartbeatLoop(owner: SSLSocket) {
        while (true) {
            try {
                Thread.sleep(HEARTBEAT_MS)
                synchronized(lock) {
                    if (socket !== owner || owner.isClosed) return
                    val output = owner.outputStream
                    write(output, EchoCastProtocol.ReceiverId, EchoCastProtocol.HeartbeatNamespace, EchoCastProtocol.pingJson())
                    write(output, EchoCastProtocol.ReceiverId, EchoCastProtocol.ReceiverNamespace, EchoCastProtocol.statusRequestJson(nextRequestId()))
                    transportId?.let { transport ->
                        write(output, transport, EchoCastProtocol.MediaNamespace, EchoCastProtocol.statusRequestJson(nextRequestId()))
                    }
                }
            } catch (_: Exception) {
                break
            }
        }
    }

    private fun nextRequestId(): Int {
        val next = requestId
        requestId = next + 1
        return next
    }

    private fun closeLocked() {
        val current = socket
        socket = null
        transportId = null
        mediaSessionId = null
        runCatching { current?.close() }
    }

    private fun awaitMediaSessionId(input: BufferedInputStream, output: OutputStream): Int? {
        val deadline = System.nanoTime() + 8_000_000_000L
        while (System.nanoTime() < deadline) {
            val message = try {
                EchoCastProtocol.read(input)
            } catch (_: Exception) {
                return null
            }
            if (message.namespace == EchoCastProtocol.HeartbeatNamespace && message.payloadUtf8.contains("PING")) {
                write(output, message.sourceId, EchoCastProtocol.HeartbeatNamespace, """{"type":"PONG"}""")
            }
            EchoCastProtocol.mediaSessionId(message.payloadUtf8)?.let { return it }
        }
        return null
    }

    private fun awaitTransportId(input: BufferedInputStream, output: OutputStream): String? {
        val deadline = System.nanoTime() + 8_000_000_000L
        while (System.nanoTime() < deadline) {
            val message = try {
                EchoCastProtocol.read(input)
            } catch (_: Exception) {
                return null
            }
            if (message.namespace == EchoCastProtocol.HeartbeatNamespace && message.payloadUtf8.contains("PING")) {
                write(output, message.sourceId, EchoCastProtocol.HeartbeatNamespace, """{"type":"PONG"}""")
            }
            EchoCastProtocol.transportId(message.payloadUtf8)?.let { return it }
        }
        return null
    }

    private fun write(output: OutputStream, destinationId: String, namespace: String, payload: String) {
        output.write(
            EchoCastProtocol.encode(
                EchoCastMessage(
                    sourceId = EchoCastProtocol.SenderId,
                    destinationId = destinationId,
                    namespace = namespace,
                    payloadUtf8 = payload,
                ),
            ),
        )
        output.flush()
    }

    private fun openSocket(host: String, port: Int): SSLSocket {
        val context = SSLContext.getInstance("TLS")
        context.init(null, arrayOf(LanTrustManager), SecureRandom())
        val socket = context.socketFactory.createSocket(host, port) as SSLSocket
        socket.soTimeout = 8_000
        socket.startHandshake()
        return socket
    }

    private companion object {
        const val HEARTBEAT_MS = 5_000L
    }

    private object LanTrustManager : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}
