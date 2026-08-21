package app.echo.android.model.connect

data class EchoLinkLanDevice(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val version: Int = 1,
    val requiresPairing: Boolean = true,
    val serviceName: String = "",
)
