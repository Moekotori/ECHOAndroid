package app.echo.android.usbaudio

/** One owner consumes frames. Control thread only changes target; no per-frame objects or locks. */
class UsbPcmGainRamp(initial: Float, sampleRate: Int) {
    @Volatile var target = initial.coerceIn(0f, 1f)
    private var current = target
    private val reset = java.util.concurrent.atomic.AtomicInteger()
    private var appliedReset = 0
    private val step = 1f / (sampleRate.coerceAtLeast(1) * .03f)
    /** Only after hardware mute has been verified, so idle/prebuffered playback also starts quietly. */
    fun resetWhileMuted() { target = 0f; reset.incrementAndGet() }
    fun next(): Float {
        val revision = reset.get()
        if (revision != appliedReset) { current = 0f; appliedReset = revision }
        val wanted = target
        current = when { wanted > current -> minOf(wanted, current + step)
            wanted < current -> maxOf(wanted, current - step)
            else -> current }
        return current
    }
    fun unity() = target == 1f && current == 1f && reset.get() == appliedReset
}
