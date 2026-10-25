package app.echo.android.usbaudio

/** Session totals; unavailable values stay null. Host supply gaps are estimates, not DAC telemetry. */
data class UsbTransferSnapshot(
    val queuedFrames: Long,
    val minQueuedFrames: Long?,
    val capacityFrames: Long,
    val sampleRateHz: Int,
    val bytesPerFrame: Int,
    val audioUrbs: Long,
    val feedbackUrbs: Long,
    val audioPacketErrors: Long,
    val feedbackPacketErrors: Long,
    val submitFailures: Long,
    val reapFailures: Long,
    val supplyGapCount: Long,
    val supplyGapFrames: Long,
    val invalidFeedbackCount: Long,
    val lastErrorSource: String?,
    val lastErrorCode: Int?,
    val fatal: Boolean,
) {
    companion object {
        internal fun fromNative(values: LongArray): UsbTransferSnapshot {
            require(values.size == 17)
            val source = when (values[14].toInt()) {
                1 -> "submit"; 2 -> "reap"; 3 -> "audio-urb"; 4 -> "audio-packet"
                5 -> "feedback-urb"; 6 -> "feedback-packet"; 7 -> "feedback-value"
                else -> null
            }
            return UsbTransferSnapshot(values[0], values[1].takeIf { it >= 0 }, values[2],
                values[3].toInt(), values[4].toInt(), values[5], values[6], values[7], values[8],
                values[9], values[10], values[11], values[12], values[13], source,
                values[15].toInt().takeIf { source != null }, values[16] != 0L)
        }
    }
}
