package app.echo.android.model.settings

/** How the word being sung lights up. Stable IDs shared with ECHOSteam; unknown values fall back to [Sweep]. */
enum class EchoLyricsWordHighlightStyle(val id: String) {
    Sweep("sweep"),
    Fade("fade"),
    Lift("lift"),
    Hop("hop"),
    Swell("swell"),
    Halo("halo"),
    Spot("spot"),
    Wave("wave"),
    Lens("lens"),
    Flip("flip"),
    Spark("spark"),
    Scan("scan"),
    Underline("underline"),
    Dots("dots"),
    ;

    /** Styles that also read how far each word is from the word being sung. */
    val usesFocus: Boolean
        get() = this == Spot || this == Wave || this == Lens

    companion object {
        fun fromId(value: String?): EchoLyricsWordHighlightStyle = entries.firstOrNull { it.id == value } ?: Sweep
    }
}
