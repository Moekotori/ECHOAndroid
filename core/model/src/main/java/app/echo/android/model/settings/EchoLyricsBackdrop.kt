package app.echo.android.model.settings

/** Which cover-driven surface sits behind the lyrics. Stable IDs; unknown values fall back to [Fluid]. */
enum class EchoLyricsBackdrop(val id: String) {
    Fluid("fluid"),
    Mesh("mesh"),
    Halftone("halftone"),
    Ridge("ridge"),
    Vinyl("vinyl"),
    Iso("iso"),
    ;

    companion object {
        fun fromId(value: String?): EchoLyricsBackdrop = entries.firstOrNull { it.id == value } ?: Fluid
    }
}
