package app.echo.android.playback

/** Uses the existing USB percent scale; the DAC controller owns hardware quantization. */
fun usbKeyVolumePercent(current: Int, direction: Int, repeatCount: Int): Int {
    val step = when { repeatCount >= 16 -> 5; repeatCount >= 6 -> 2; else -> 1 }
    return (current.coerceIn(0, 100) + direction.coerceIn(-1, 1) * step).coerceIn(0, 100)
}
