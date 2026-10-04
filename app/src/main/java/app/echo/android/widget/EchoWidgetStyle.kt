package app.echo.android.widget

/** System picker entries; no per-instance player or progress timer. */
internal enum class EchoWidgetStyle {
    Player, Mini, Cover, Lyrics;

    fun needsRefresh(previous: EchoPlaybackWidgetChrome?, next: EchoPlaybackWidgetChrome): Boolean =
        previous == null || when (this) {
            Player, Lyrics -> previous != next
            Mini, Cover -> previous.copy(lyricLine = next.lyricLine) != next
        }
}
