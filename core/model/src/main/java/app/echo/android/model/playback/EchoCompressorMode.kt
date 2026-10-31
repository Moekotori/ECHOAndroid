package app.echo.android.model.playback

enum class EchoCompressorMode(val id: String) {
    Off("off"), Gentle("gentle"), Night("night");
    companion object { fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: Off }
}
