package app.echo.android.model.playback

enum class EchoEqBandScope(val id: String) {
    Stereo("stereo"), Left("left"), Right("right"), Mid("mid"), Side("side");
    companion object { fun fromId(value: String?) = entries.firstOrNull { it.id == value } ?: Stereo }
}
enum class EchoEqDynamicMode(val id: String) {
    Off("off"), Down("down"), Up("up");
    companion object { fun fromId(value: String?) = entries.firstOrNull { it.id == value } ?: Off }
}
data class EchoEqOptions(val strengthPercent: Float = 100f, val tiltDb: Float = 0f) {
    val normalized: EchoEqOptions get() = copy(
        strengthPercent = strengthPercent.takeIf(Float::isFinite)?.coerceIn(0f, 100f) ?: 100f,
        tiltDb = tiltDb.takeIf(Float::isFinite)?.coerceIn(-6f, 6f) ?: 0f,
    )
    fun effectiveGain(band: OpraEqBand): Float {
        val scaled = band.gainDb * strengthPercent / 100f
        if (band.normalizedType() !in GainTypes) return scaled
        val tilt = tiltDb * (2 * kotlin.math.ln(band.frequencyHz.coerceIn(20f, 20000f) / 20f) / kotlin.math.ln(1000f) - 1)
        // Imported legacy/correction gain ranges remain readable; new editor values follow PC.
        val high = if (band.gainDb > 12) 30f else 12f
        return (scaled + tilt).coerceIn(if (band.gainDb < -24) -60f else -24f, high)
    }
    companion object { val GainTypes = setOf(EchoEqFilterType.PeakDip, EchoEqFilterType.LowShelf, EchoEqFilterType.HighShelf) }
}
data class EchoEqAudition(val active: Boolean = false, val frequencyHz: Float = 1000f, val q: Float = 1f, val revision: Long = 0)
enum class EchoEqProcessingState { Idle, Preparing, Native, Compatibility, Failed }
