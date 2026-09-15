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
    fun play(
        renderer: EchoLanRenderer,
        item: EchoRemoteStreamItem,
        positionMs: Long = 0L,
    ) {
        EchoChromecastCastPolicy.rejectReason(renderer, item)?.let { reason ->
            throw EchoDlnaException(415, reason.code)
        }
        val port = renderer.port.takeIf { it > 0 } ?: EchoChromecastCastPolicy.DefaultPort
        val socket = openSocket(renderer.host, port)
        try {
            val input = BufferedInputStream(socket.inputStream)
            val output = socket.outputStream
            var requestId = 1
            write(output, EchoCastProtocol.ReceiverId, EchoCastProtocol.ConnectionNamespace, EchoCastProtocol.connectJson())
            write(
                output,
                EchoCastProtocol.ReceiverId,
                EchoCastProtocol.ReceiverNamespace,
                EchoCastProtocol.launchJson(requestId++, EchoChromecastCastPolicy.DefaultReceiverAppId),
            )
            val transportId = awaitTransportId(input, output) ?: throw EchoDlnaException(500, "chromecast_unsupported")
            write(output, transportId, EchoCastProtocol.ConnectionNamespace, EchoCastProtocol.connectJson())
            val mime = EchoDlnaCastPolicy.offeredMime(item)
            write(
                output,
                transportId,
                EchoCastProtocol.MediaNamespace,
                EchoCastProtocol.loadJson(
                    requestId = requestId,
                    contentId = item.streamUrl,
                    contentType = mime,
                    positionMs = positionMs,
                    title = item.title,
                    artist = item.artist,
                ),
            )
        } finally {
            runCatching { socket.close() }
        }
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
