package app.echo.android.model.settings

/** Stable preference IDs; unknown values from a newer backup fall back safely. */
enum class EchoLyricsPageStyle(
    val id: String,
    val defaultFontFamily: String,
    val defaultAlignment: String,
) {
    Mist("mist", "system", "start"),
    Paper("paper", "serif", "center"),
    AfterglowMist("afterglow_mist", "serif", "center"),
    AfterglowNight("afterglow_night", "system", "center");

    val isAfterglow: Boolean
        get() = this == AfterglowMist || this == AfterglowNight

    companion object {
        fun fromId(value: String?): EchoLyricsPageStyle =
            entries.firstOrNull { it.id == value } ?: Mist
    }
}
