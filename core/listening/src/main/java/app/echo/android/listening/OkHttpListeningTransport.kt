package app.echo.android.listening

import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

internal class OkHttpListeningTransport : EchoListeningTransport {
    private val client = OkHttpClient.Builder()
        .pingInterval(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()
    private val events = Channel<EchoListeningInbound>(capacity = 64)
    private val socket = AtomicReference<WebSocket?>(null)
    private val binaryListener = AtomicReference<(ByteArray) -> Unit> {}

    override val incoming = events.receiveAsFlow()

    override fun setBinaryListener(listener: (ByteArray) -> Unit) {
        binaryListener.set(listener)
    }

    override suspend fun open(url: String) {
        close()
        val opened = CompletableDeferred<Unit>()
        val webSocket = client.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    socket.set(webSocket)
                    if (!opened.complete(Unit)) webSocket.cancel()
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (webSocket !== socket.get()) return
                    events.trySend(EchoListeningInbound.Text(text))
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    if (webSocket !== socket.get() || bytes.size > EchoListeningPackets.MAX_PACKET) return
                    binaryListener.get().invoke(bytes.toByteArray())
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    finish(webSocket, reason.ifBlank { "connection_closed" }, opened)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    finish(webSocket, "connection_closed", opened)
                }
            },
        )
        socket.set(webSocket)
        try {
            withTimeout(8_000) { opened.await() }
        } catch (error: Exception) {
            socket.compareAndSet(webSocket, null)
            webSocket.cancel()
            throw error
        }
    }

    override suspend fun send(text: String) {
        val webSocket = socket.get() ?: throw IOException("not_connected")
        if (!webSocket.send(text)) throw IOException("not_connected")
    }

    override fun close() {
        // Drop the reference before cancel so the socket callback does not
        // look like a remote close and start a reconnect.
        socket.getAndSet(null)?.cancel()
    }

    private fun finish(webSocket: WebSocket, reason: String, opened: CompletableDeferred<Unit>) {
        if (socket.compareAndSet(webSocket, null)) {
            events.trySend(EchoListeningInbound.Closed(reason))
        }
        opened.completeExceptionally(IOException(reason))
    }
}
