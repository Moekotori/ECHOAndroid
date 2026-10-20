package app.echo.android.model.settings

/** Persisted setting values shared by the settings store, theme resolution and settings UI. */
object EchoBackgroundMode {
    const val Default = "default"
    const val Image = "image"
    const val Video = "video"
}

object EchoFontFamilyMode {
    const val System = "system"
    const val Outfit = "outfit"
    const val Serif = "serif"
    const val Monospace = "monospace"
    const val Imported = "imported"
}

object EchoLyricsMotionMode {
    const val Calm = "calm"
    const val Smooth = "smooth"
    const val Stage = "stage"
}

object EchoThemeMode {
    const val System = "system"
    const val Light = "light"
    const val Dark = "dark"
}
