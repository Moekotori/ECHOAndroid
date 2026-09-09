package app.echo.android.connect

object EchoLinkRemoteControlHold {
    const val PositionToleranceMs = 2_000L
    const val VolumeTolerance = 0.04f
    const val HoldTimeoutMs = 5_000L

    fun shouldHoldCommittedPosition(
        committedPositionMs: Long?,
        committedAtElapsedMs: Long?,
        remotePositionMs: Long,
        nowElapsedMs: Long,
    ): Boolean {
        val committed = committedPositionMs ?: return false
        val committedAt = committedAtElapsedMs ?: return false
        if (nowElapsedMs - committedAt >= HoldTimeoutMs) return false
        return kotlin.math.abs(remotePositionMs - committed) > PositionToleranceMs
    }

    fun displayedPositionMs(
        remotePositionMs: Long,
        livePositionMs: Long,
        committedPositionMs: Long?,
        committedAtElapsedMs: Long?,
        nowElapsedMs: Long,
        draggingPositionMs: Long?,
    ): Long {
        if (draggingPositionMs != null) return draggingPositionMs
        if (
            shouldHoldCommittedPosition(
                committedPositionMs = committedPositionMs,
                committedAtElapsedMs = committedAtElapsedMs,
                remotePositionMs = remotePositionMs,
                nowElapsedMs = nowElapsedMs,
            )
        ) {
            return committedPositionMs ?: livePositionMs
        }
        return livePositionMs
    }

    fun shouldHoldCommittedVolume(
        committedVolume: Float?,
        committedAtElapsedMs: Long?,
        remoteVolume: Float,
        nowElapsedMs: Long,
    ): Boolean {
        val committed = committedVolume ?: return false
        val committedAt = committedAtElapsedMs ?: return false
        if (nowElapsedMs - committedAt >= HoldTimeoutMs) return false
        return kotlin.math.abs(remoteVolume - committed) > VolumeTolerance
    }

    fun displayedVolume(
        remoteVolume: Float,
        committedVolume: Float?,
        committedAtElapsedMs: Long?,
        nowElapsedMs: Long,
        draggingVolume: Float?,
    ): Float {
        if (draggingVolume != null) return draggingVolume
        if (
            shouldHoldCommittedVolume(
                committedVolume = committedVolume,
                committedAtElapsedMs = committedAtElapsedMs,
                remoteVolume = remoteVolume,
                nowElapsedMs = nowElapsedMs,
            )
        ) {
            return committedVolume ?: remoteVolume
        }
        return remoteVolume
    }
}
