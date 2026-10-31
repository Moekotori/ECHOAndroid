package app.echo.android.model.playback

/** App PCM volume; independent of Android media volume and USB DAC hardware volume. */
data class EchoVolumeSettings(
    val levelDb: Float = 0f,
    val gainDb: Float = 0f,
    val limitDb: Float = 30f,
    val stepDb: Float = 0.5f,
    val muted: Boolean = false,
) {
    val normalized: EchoVolumeSettings get() = copy(
        levelDb = levelDb.safe(-60f, 0f, 0f),
        gainDb = gainDb.safe(-24f, 12f, 0f),
        limitDb = limitDb.safe(-24f, 30f, 30f),
        stepDb = stepDb.takeIf { it in Steps } ?: 0.5f,
    )

    /** Ceiling applies to the combined volume, preamp and ReplayGain, before peak limiting. */
    fun combinedGainDb(replayGainDb: Float = 0f): Float =
        (levelDb + gainDb + replayGainDb.safe(-36f, 30f, 0f)).coerceAtMost(limitDb)

    val changesSamples: Boolean get() = muted || combinedGainDb() != 0f

    fun adjusted(direction: Int): EchoVolumeSettings = normalized.let {
        it.copy(levelDb = (it.levelDb + direction.coerceIn(-1, 1) * it.stepDb).coerceIn(-60f, 0f))
    }

    companion object {
        val Steps = listOf(0.1f, 0.5f, 1f, 2f)
        private fun Float.safe(min: Float, max: Float, fallback: Float): Float =
            takeIf { it.isFinite() }?.coerceIn(min, max) ?: fallback
    }
}
