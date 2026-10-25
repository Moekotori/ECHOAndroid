package app.echo.android.playback

internal data class EchoAudioResumeIdentity(val mediaId: String?, val index: Int, val route: String)

/** A transient focus pause grants one resume ticket; explicit user actions revoke it. */
internal class EchoAudioResumeGuard {
    private var identity: EchoAudioResumeIdentity? = null
    private var sleepDeadlineMs: Long? = null
    val pending: Boolean get() = identity != null
    fun remember(value: EchoAudioResumeIdentity, sleepDeadlineMs: Long?) {
        identity = value; this.sleepDeadlineMs = sleepDeadlineMs
    }
    fun cancel() { identity = null; sleepDeadlineMs = null }
    fun consume(current: EchoAudioResumeIdentity, canPlay: Boolean, nowMs: Long): Boolean {
        val allowed = identity == current && canPlay && (sleepDeadlineMs?.let { nowMs < it } ?: true)
        cancel()
        return allowed
    }
}
