package app.echo.android.model.settings

/** Stable preference IDs; unknown values from a newer backup fall back safely. */
enum class EchoLyricsPageStyle(
    val id: String,
    val defaultFontFamily: String,
    val defaultAlignment: String,
) {
    Mist("mist", "system", "start"),
    Paper("paper", "serif", "center");

    companion object {
        fun fromId(value: String?): EchoLyricsPageStyle =
            entries.firstOrNull { it.id == value } ?: Mist
    }
}
