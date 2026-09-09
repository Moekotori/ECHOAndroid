package app.echo.android.model.connect

data class EchoRemoteEndpoint(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val token: String,
    val scheme: String = "http",
    val protocolVersion: EchoProtocolVersion = EchoProtocolVersion.Current,
    val pairingId: String? = null,
    val pairingSecret: String? = null,
    val supportsV2Events: Boolean = false,
) {
    val needsV2PairExchange: Boolean
        get() = !pairingId.isNullOrBlank() && !pairingSecret.isNullOrBlank()
}
