package app.echo.android.model.playback

/** Snapshot totals for one USB session. Supply gaps/bytes are host-side estimates. */
data class EchoUsbTransportDiagnostics(
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
    val vendorId: Int?,
    val productId: Int?,
    val sampledAtElapsedMs: Long,
    val sessionClosed: Boolean = false,
) {
    val queuedBytes: Long get() = queuedFrames * bytesPerFrame
    val supplyGapBytes: Long get() = supplyGapFrames * bytesPerFrame
    fun milliseconds(frames: Long): Long = if (sampleRateHz > 0) frames * 1_000L / sampleRateHz else 0L
}

data class EchoUsbCompatibilityRule(
    val id: String,
    val reason: String,
    val verification: String,
)
