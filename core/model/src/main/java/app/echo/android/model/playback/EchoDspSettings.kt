package app.echo.android.model.playback

/** User settings for optional processing on the normal PCM route. */
data class EchoDspSettings(
    val limiterEnabled: Boolean = false,
    val limiterCeilingDb: Float = -1f,
    val crossfeedEnabled: Boolean = false,
    val crossfeedAmount: Float = 0.3f,
    val compressor: EchoCompressorMode = EchoCompressorMode.Off,
    val hrtfEnabled: Boolean = false,
    val hrtfProfile: Int = 1,
    val hrtfAmount: Float = 1f,
    val equalLoudnessEnabled: Boolean = false,
    val equalLoudnessReferenceDb: Float = -10f,
    val equalLoudnessBassDb: Float = 6f,
    val equalLoudnessTrebleDb: Float = 3f,
    val soundEffect: EchoSoundEffectSettings = EchoSoundEffectSettings(),
) {
    val normalized: EchoDspSettings get() = copy(
        crossfeedEnabled = crossfeedEnabled && !hrtfEnabled,
        hrtfProfile = hrtfProfile.coerceIn(0, 2),
        hrtfAmount = hrtfAmount.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 1f,
        equalLoudnessReferenceDb = equalLoudnessReferenceDb.takeIf(Float::isFinite)?.coerceIn(-30f, 0f) ?: -10f,
        equalLoudnessBassDb = equalLoudnessBassDb.takeIf(Float::isFinite)?.coerceIn(0f, 12f) ?: 6f,
        equalLoudnessTrebleDb = equalLoudnessTrebleDb.takeIf(Float::isFinite)?.coerceIn(0f, 6f) ?: 3f,
        limiterCeilingDb = limiterCeilingDb.takeIf(Float::isFinite)?.coerceIn(-6f, -0.1f) ?: -1f,
        crossfeedAmount = crossfeedAmount.takeIf(Float::isFinite)?.coerceIn(0f, 0.6f) ?: 0.3f,
    )
}
