package app.echo.android.model.settings

/** 悬浮歌词。窗口水平居中，只记录纵向位置。 */
data class EchoFloatingLyricsSettings(
    val enabled: Boolean = false,
    /** 锁定后窗口不接收触摸，点击会穿透到下面的应用。 */
    val locked: Boolean = false,
    val fontScale: Float = 1f,
    /** 距屏幕顶部的像素；负数表示用默认位置。 */
    val offsetY: Int = -1,
) {
    val normalized: EchoFloatingLyricsSettings
        get() = copy(fontScale = fontScale.coerceIn(MinFontScale, MaxFontScale))

    companion object {
        const val MinFontScale = 0.8f
        const val MaxFontScale = 1.6f
    }
}
