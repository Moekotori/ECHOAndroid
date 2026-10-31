package app.echo.android.model.playback

/** App PCM volume; independent of Android media volume and USB DAC hardware volume. */
data class EchoVolumeSettings(
    val levelDb: Float = 0f,
    val gainDb: Float = 0f,
    val limitDb: Float = 30f,
    val stepDb: Float = 0.5f,
    val muted: Boolean = false,
    val deviceKey: String? = null,
    val deviceLevels: Map<String, EchoVolumeDeviceLevel> = emptyMap(),
    val rememberPerDevice: Boolean = false,
) {
    val normalized: EchoVolumeSettings get() = copy(
        levelDb = levelDb.safe(-60f, 0f, 0f),
        gainDb = gainDb.safe(-24f, 12f, 0f),
        limitDb = limitDb.safe(-24f, 30f, 30f),
        stepDb = stepDb.takeIf { it in Steps } ?: 0.5f,
        deviceKey = deviceKey?.takeIf { rememberPerDevice && EchoVolumeDeviceIdentity.validKey(it) },
        deviceLevels = deviceLevels.entries.filter { EchoVolumeDeviceIdentity.validKey(it.key) }
            .takeLast(EchoVolumeDeviceIdentity.MaxDevices).associate { it.key to it.value.normalized },
    )

    /** Ceiling applies to the combined volume, preamp and ReplayGain, before peak limiting. */
    fun combinedGainDb(replayGainDb: Float = 0f): Float =
        (levelDb + gainDb + replayGainDb.safe(-36f, 30f, 0f)).coerceAtMost(limitDb)

    val changesSamples: Boolean get() = muted || combinedGainDb() != 0f

    fun adjusted(direction: Int): EchoVolumeSettings = normalized.let {
        // Quantize the sum to avoid cumulative float drift with 0.1 dB key repeats.
        val next = kotlin.math.round((it.levelDb + direction.coerceIn(-1, 1) * it.stepDb) * 10f) / 10f
        it.copy(levelDb = next.coerceIn(-60f, 0f))
    }

    fun rememberDevice(): EchoVolumeSettings {
        if (!rememberPerDevice) return this
        val key = deviceKey?.takeIf(EchoVolumeDeviceIdentity::validKey) ?: return this
        val levels = deviceLevels.filterKeys { it != key } + (key to EchoVolumeDeviceLevel(levelDb, gainDb).normalized)
        return copy(deviceLevels = levels.entries.toList().takeLast(EchoVolumeDeviceIdentity.MaxDevices)
            .associate { it.toPair() })
    }

    fun forDevice(key: String?): EchoVolumeSettings {
        if (!rememberPerDevice) return if (deviceKey == null) this else copy(deviceKey = null)
        if (key == null || !EchoVolumeDeviceIdentity.validKey(key) || key == deviceKey) return this
        val saved = normalized.rememberDevice()
        // An unseen output inherits the current level instead of jumping back to unity gain.
        val level = saved.deviceLevels[key] ?: EchoVolumeDeviceLevel(saved.levelDb, saved.gainDb)
        return saved.copy(deviceKey = key, levelDb = level.levelDb, gainDb = level.gainDb).rememberDevice()
    }

    companion object {
        val Steps = listOf(0.1f, 0.5f, 1f, 2f)
        private fun Float.safe(min: Float, max: Float, fallback: Float): Float =
            takeIf { it.isFinite() }?.coerceIn(min, max) ?: fallback
    }
}
