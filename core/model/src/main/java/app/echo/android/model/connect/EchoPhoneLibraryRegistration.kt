package app.echo.android.model.connect

/** Session-only LAN capability. Never persist the token or expose local content URIs. */
data class EchoPhoneLibraryRegistration(
    val sessionId: String,
    val baseUrl: String,
    val token: String,
    val name: String,
)
