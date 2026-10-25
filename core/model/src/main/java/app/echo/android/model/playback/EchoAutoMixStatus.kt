package app.echo.android.model.playback

enum class EchoAutoMixStage { Idle, Bypassed, Analyzing, Waiting, Preparing, Armed, Mixing, Failed }

data class EchoAutoMixStatus(val stage: EchoAutoMixStage = EchoAutoMixStage.Idle,
    val reason: String? = null, val mode: String? = null, val outgoingBpm: Double? = null,
    val incomingBpm: Double? = null, val tempoConfidence: Double? = null,
    val startPositionMs: Long = 0, val durationMs: Long = 0, val playing: Boolean = false) {
    fun normalized() = copy(outgoingBpm = outgoingBpm?.takeIf { it.isFinite() && it > 0 },
        incomingBpm = incomingBpm?.takeIf { it.isFinite() && it > 0 },
        tempoConfidence = tempoConfidence?.takeIf { it.isFinite() && it in 0.0..1.0 },
        mode = mode?.take(120), reason = reason?.take(64))
    /** Predecoded PCM may run ahead of the DAC. Only the audible player position starts the display. */
    fun audibleProgress(positionMs: Long, isPlaying: Boolean, mixedFrames: Int) = copy(playing = isPlaying,
        stage = if (stage == EchoAutoMixStage.Armed && isPlaying && positionMs >= startPositionMs && mixedFrames > 0)
            EchoAutoMixStage.Mixing else stage)
}
