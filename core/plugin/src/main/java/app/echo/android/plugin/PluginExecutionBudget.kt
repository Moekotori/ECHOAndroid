package app.echo.android.plugin

/** Shared with the Android HTTP adapter so blocking reads honor the script deadline. */
object PluginExecutionBudget {
    private val deadline = ThreadLocal<Long>()
    internal fun enter(timeoutMs: Long) { deadline.set(System.nanoTime() + timeoutMs * 1_000_000) }
    internal fun leave() { deadline.remove() }
    fun remainingMillis(): Int {
        if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException("cancelled")
        val end = deadline.get() ?: return 10_000
        val remaining = (end - System.nanoTime()) / 1_000_000
        if (remaining <= 0) throw java.io.InterruptedIOException("timeout")
        return remaining.coerceIn(1, 10_000).toInt()
    }
}
