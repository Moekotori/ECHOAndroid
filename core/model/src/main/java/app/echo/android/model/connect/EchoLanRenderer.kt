package app.echo.android.model.connect

enum class EchoLanRendererKind {
    Dlna,
    Chromecast,
}

data class EchoDlnaService(
    val serviceType: String,
    val controlUrl: String,
)

data class EchoLanRenderer(
    val id: String,
    val name: String,
    val host: String,
    val port: Int = 0,
    val kind: EchoLanRendererKind,
    val manufacturer: String? = null,
    val model: String? = null,
    val location: String? = null,
    val avTransport: EchoDlnaService? = null,
    val renderingControl: EchoDlnaService? = null,
    val connectionManager: EchoDlnaService? = null,
    val sinkMimeTypes: List<String> = emptyList(),
)
