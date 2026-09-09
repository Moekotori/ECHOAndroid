package app.echo.android.model.connect

data class EchoSavedPcEndpoint(
    val address: String,
    val name: String,
    val supportsV2Events: Boolean = false,
    val token: String? = null,
) {
    val id: String
        get() = address.trim().trimEnd('/').lowercase()
}
