package app.echo.android.usbaudio

/** One owner consumes frames. Control thread only changes target; no per-frame objects or locks. */
class UsbPcmGainRamp(initial: Float, sampleRate: Int) {
    @Volatile var target = initial.coerceIn(0f, 1f)
    private var current = target
    private val reset = java.util.concurrent.atomic.AtomicInteger()
    private var appliedReset = 0
    private val step = 1f / (sampleRate.coerceAtLeast(1) * .03f)
    private var writeStart = current
    private var writeTarget = target
    /** Only after hardware mute has been verified, so idle/prebuffered playback also starts quietly. */
    fun resetWhileMuted() { target = 0f; reset.incrementAndGet() }
    fun beginWrite() {
        val revision = reset.get()
        if (revision != appliedReset) { current = 0f; appliedReset = revision }
        writeStart = current
        writeTarget = target
    }

    fun gainForWrite(frames: Int): Float =
        writeStart + (writeTarget - writeStart).coerceIn(-step * frames, step * frames)

    fun commitWrittenFrames(frames: Int) {
        current = gainForWrite(frames)
    }

    fun writeIsUnity(): Boolean = writeStart == 1f && writeTarget == 1f
}
