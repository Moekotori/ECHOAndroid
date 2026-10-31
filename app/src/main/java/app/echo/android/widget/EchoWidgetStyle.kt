package app.echo.android.widget

/** System picker entries; no per-instance player or progress timer. */
internal enum class EchoWidgetStyle {
    Player, Mini, Cover, Lyrics, Record, Banner, Typography;

    val usesArtwork: Boolean get() = this != Typography

    fun needsRefresh(previous: EchoPlaybackWidgetChrome?, next: EchoPlaybackWidgetChrome): Boolean =
        previous == null || when (this) {
            Player, Lyrics, Typography -> previous != next
            Mini, Cover, Record, Banner -> previous.copy(lyricLine = next.lyricLine) != next
        }
}
