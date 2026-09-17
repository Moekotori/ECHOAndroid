package app.echo.android.model.lyrics

data class EchoLyricDisplaySnapshot(
    val trackId: String? = null,
    val previous: EchoLyricLine? = null,
    val current: EchoLyricLine? = null,
    val next: EchoLyricLine? = null,
    val currentStartMs: Long = 0L,
    val positionMs: Long = 0L,
    val publishedAtElapsedRealtimeMs: Long = 0L,
    val isPlaying: Boolean = false,
    val speed: Float = 1f,
) {
    fun interpolatedPositionMs(nowElapsedRealtimeMs: Long): Long {
        if (!isPlaying) return positionMs
        val rate = speed.takeIf { it.isFinite() && it > 0f } ?: 1f
        val elapsed = ((nowElapsedRealtimeMs - publishedAtElapsedRealtimeMs).coerceAtLeast(0L) * rate).toLong()
        return positionMs + elapsed
    }
}
