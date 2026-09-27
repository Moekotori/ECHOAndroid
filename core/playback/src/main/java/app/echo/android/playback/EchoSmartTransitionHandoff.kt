package app.echo.android.playback

/** Only an automatic transition to the armed queue occurrence may consume mixed audio. */
internal object EchoSmartTransitionHandoff {
    fun continuationMs(automatic: Boolean, expectedIndex: Int, actualIndex: Int,
        expectedId: String, actualId: String?, startMs: Long, mixedFrames: Int, sampleRateHz: Int): Long? {
        if (!automatic || expectedIndex != actualIndex || expectedId != actualId ||
            mixedFrames <= 0 || sampleRateHz <= 0) return null
        return startMs.coerceAtLeast(0) + mixedFrames.toLong() * 1000 / sampleRateHz
    }
}
