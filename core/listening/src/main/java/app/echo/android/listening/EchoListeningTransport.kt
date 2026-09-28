package app.echo.android.listening

import kotlinx.coroutines.flow.Flow

internal sealed interface EchoListeningInbound {
    data class Text(val body: String) : EchoListeningInbound
    data class Binary(val body: ByteArray) : EchoListeningInbound
    data class Closed(val reason: String) : EchoListeningInbound
}

internal interface EchoListeningTransport {
    val incoming: Flow<EchoListeningInbound>
    fun setBinaryListener(listener: (ByteArray) -> Unit)
    suspend fun open(url: String)
    suspend fun send(text: String)
    fun close()
}
