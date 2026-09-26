package app.echo.android.plugin

enum class PluginCapability(val wire: String) {
    PlaybackRead("playback.read"),
    PlaybackControl("playback.control"),
    LibrarySearch("library.search"),
    Network("network"),
    Storage("storage"),
    UiPage("ui.page"),
    ;

    companion object {
        fun fromWire(value: String): PluginCapability? = entries.firstOrNull { it.wire == value }
    }
}
