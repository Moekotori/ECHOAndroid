package app.echo.android.model.playback

/** A prepared transition on the outgoing media timeline; never a UI-owned playback clock. */
data class EchoSmartTransitionVisual(
    val currentTrackId: String,
    val nextTrack: EchoTrackRef,
    val startPositionMs: Long,
    val durationMs: Long,
) {
    fun progressAt(positionMs: Long): Float =
        ((positionMs - startPositionMs).toDouble() / durationMs.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f)
}
