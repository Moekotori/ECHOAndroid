package app.echo.android.playback

/** Only an automatic transition to the armed queue occurrence may consume mixed audio. */
internal object EchoSmartTransitionHandoff {
    /** Pausing changes eligibility for a new mix, but must not discard an existing PCM cursor. */
    fun retainArmed(reason: EchoSmartTransitionPolicy.BypassReason?, sameStream: Boolean): Boolean =
        sameStream && (reason == null || reason == EchoSmartTransitionPolicy.BypassReason.NotPlaying ||
            reason == EchoSmartTransitionPolicy.BypassReason.ShortTrack)

    fun continuationMs(automatic: Boolean, expectedIndex: Int, actualIndex: Int,
        expectedId: String, actualId: String?, startMs: Long, mixedFrames: Int, sampleRateHz: Int): Long? {
        if (!automatic || expectedIndex != actualIndex || expectedId != actualId ||
            mixedFrames <= 0 || sampleRateHz <= 0) return null
        return startMs.coerceAtLeast(0) + mixedFrames.toLong() * 1000 / sampleRateHz
    }
}
