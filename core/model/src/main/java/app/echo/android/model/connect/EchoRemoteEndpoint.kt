package app.echo.android.model.connect

data class EchoRemoteEndpoint(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val token: String,
    val scheme: String = "http",
    val protocolVersion: EchoProtocolVersion = EchoProtocolVersion.Current,
)
