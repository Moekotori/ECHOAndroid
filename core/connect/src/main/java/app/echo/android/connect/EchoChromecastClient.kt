package app.echo.android.connect

import app.echo.android.model.connect.EchoLanRenderer
import app.echo.android.model.connect.EchoRemoteStreamItem
import java.io.BufferedInputStream
import java.io.OutputStream
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
            val input = BufferedInputStream(opened.inputStream)
            val output = opened.outputStream
            requestId = 1
            write(output, EchoCastProtocol.ReceiverId, EchoCastProtocol.ConnectionNamespace, EchoCastProtocol.connectJson())
            write(
                output,
                EchoCastProtocol.ReceiverId,
                EchoCastProtocol.ReceiverNamespace,
                EchoCastProtocol.launchJson(nextRequestId(), EchoChromecastCastPolicy.DefaultReceiverAppId),
            )
            val transport = awaitTransportId(input, output) ?: throw EchoDlnaException(500, "chromecast_unsupported")
            transportId = transport
            write(output, transport, EchoCastProtocol.ConnectionNamespace, EchoCastProtocol.connectJson())
            loadLocked(item, positionMs, input, output)
            Thread(::heartbeatLoop, "echo-cast-heartbeat").apply { isDaemon = true }.start()
        }
    }

    fun pause() = mediaCommand { id, session -> EchoCastProtocol.pauseJson(id, session) }.also { paused = true }

    fun resume() = mediaCommand { id, session -> EchoCastProtocol.resumeJson(id, session) }.also { paused = false }

    fun togglePause() {
        if (paused) resume() else pause()
    }

    fun stop() {
        runCatching { mediaCommand { id, session -> EchoCastProtocol.stopJson(id, session) } }
        close()
    }

    fun playNext(item: EchoRemoteStreamItem) {
        EchoChromecastCastPolicy.rejectReason(item)?.let { reason ->
            throw EchoDlnaException(415, reason.code)
        }
        synchronized(lock) {
            val opened = socket ?: throw EchoDlnaException(500, "chromecast_closed")
            loadLocked(item, 0L, BufferedInputStream(opened.inputStream), opened.outputStream)
        }
    }

    fun close() {
        synchronized(lock) { closeLocked() }
    }

    private fun loadLocked(
        item: EchoRemoteStreamItem,
        positionMs: Long,
        input: BufferedInputStream,
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
        mediaSessionId = awaitMediaSessionId(input, output) ?: mediaSessionId
        paused = false
    }

    private fun mediaCommand(body: (requestId: Int, sessionId: Int) -> String) {
        val output = socket?.outputStream ?: return
        val transport = transportId ?: return
        val session = mediaSessionId ?: return
        synchronized(lock) {
            write(output, transport, EchoCastProtocol.MediaNamespace, body(nextRequestId(), session))
        }
    }

    private fun heartbeatLoop() {
        while (true) {
            try {
                Thread.sleep(5_000)
                synchronized(lock) {
                    val current = socket ?: return
                    if (current.isClosed) return
                    write(
                        current.outputStream,
                        EchoCastProtocol.ReceiverId,
                        EchoCastProtocol.HeartbeatNamespace,
                        EchoCastProtocol.pingJson(),
                    )
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

    private object LanTrustManager : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}
