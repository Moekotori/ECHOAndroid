package app.echo.android.model.playback

/** User settings for optional processing on the normal PCM route. */
data class EchoDspSettings(
    val limiterEnabled: Boolean = false,
    val limiterCeilingDb: Float = -1f,
    val crossfeedEnabled: Boolean = false,
    val crossfeedAmount: Float = 0.3f,
) {
    val normalized: EchoDspSettings get() = copy(
        limiterCeilingDb = limiterCeilingDb.takeIf(Float::isFinite)?.coerceIn(-6f, -0.1f) ?: -1f,
        crossfeedAmount = crossfeedAmount.takeIf(Float::isFinite)?.coerceIn(0f, 0.6f) ?: 0.3f,
    )
}
