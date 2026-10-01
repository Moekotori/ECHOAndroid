package app.echo.android.model.settings

/** One saved style for both song details and lyrics; lyric presets remain backup-compatible. */
enum class EchoPlayerPageStyle(val id: String, val lyricsPreset: EchoLyricsPageStyle) {
    Classic("classic", EchoLyricsPageStyle.Mist),
    RecordSleeve("record_sleeve", EchoLyricsPageStyle.Paper),
    PixelHandheld("pixel_handheld", EchoLyricsPageStyle.Paper),
    TypePoster("type_poster", EchoLyricsPageStyle.Paper),
    AfterglowMist("afterglow_mist", EchoLyricsPageStyle.AfterglowMist),
    AfterglowNight("afterglow_night", EchoLyricsPageStyle.AfterglowNight);

    val isLight: Boolean get() = this != Classic && this != AfterglowNight
    val defaultFontFamily: String get() = if (lyricsPreset.isAfterglow) lyricsPreset.defaultFontFamily else "system"

    companion object {
        fun fromId(value: String?): EchoPlayerPageStyle = when (value) {
            "mist" -> Classic
            "paper" -> RecordSleeve
            else -> entries.firstOrNull { it.id == value } ?: Classic
        }

        /** Preserve old Afterglow selections, otherwise the song style owns the shared choice. */
        fun fromLegacy(player: String?, lyrics: String?): EchoPlayerPageStyle = when {
            EchoLyricsPageStyle.fromId(lyrics).isAfterglow -> fromId(lyrics)
            player != null -> fromId(player)
            else -> fromId(lyrics)
        }
    }
}
