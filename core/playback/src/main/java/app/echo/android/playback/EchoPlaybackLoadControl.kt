package app.echo.android.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection

@UnstableApi
internal class EchoPlaybackLoadControl(
    private val delegate: DefaultLoadControl = defaultDelegate(),
) : LoadControl {
    // Java default interface methods are not forwarded by Kotlin's `by` delegation.
    // Forward the current Media3 callbacks explicitly to avoid its throwing legacy defaults.
    override fun onPrepared(playerId: PlayerId) = delegate.onPrepared(playerId)

    override fun onTracksSelected(
        parameters: LoadControl.Parameters,
        trackGroups: TrackGroupArray,
        trackSelections: Array<out ExoTrackSelection?>,
    ) = delegate.onTracksSelected(parameters, trackGroups, trackSelections)

    override fun onStopped(playerId: PlayerId) = delegate.onStopped(playerId)

    override fun onReleased(playerId: PlayerId) = delegate.onReleased(playerId)

    override fun getAllocator(playerId: PlayerId) = delegate.getAllocator(playerId)

    override fun getBackBufferDurationUs(playerId: PlayerId) = delegate.getBackBufferDurationUs(playerId)

    override fun retainBackBufferFromKeyframe(playerId: PlayerId) = delegate.retainBackBufferFromKeyframe(playerId)

    override fun shouldContinuePreloading(
        playerId: PlayerId,
        timeline: Timeline,
        mediaPeriodId: MediaPeriodId,
        bufferedDurationUs: Long,
    ) = delegate.shouldContinuePreloading(playerId, timeline, mediaPeriodId, bufferedDurationUs)

    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean {
        val budget = EchoPlaybackLoadControlPolicy.forMode(EchoPlaybackCachePolicy.effectiveMode)
        if (!EchoPlaybackLoadControlPolicy.shouldContinueLoading(parameters.bufferedDurationUs, budget.maxBufferMs)) {
            return false
        }
        return delegate.shouldContinueLoading(parameters)
    }

    override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean {
        val budget = EchoPlaybackLoadControlPolicy.forMode(EchoPlaybackCachePolicy.effectiveMode)
        return EchoPlaybackLoadControlPolicy.shouldStartPlayback(
            bufferedDurationUs = parameters.bufferedDurationUs,
            playbackSpeed = parameters.playbackSpeed,
            rebuffering = parameters.rebuffering,
            budget = budget,
        )
    }

    private companion object {
        fun defaultDelegate(): DefaultLoadControl {
            val high = EchoPlaybackLoadControlPolicy.HighPerformance
            return DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    high.minBufferMs,
                    high.maxBufferMs,
                    high.bufferForPlaybackMs,
                    high.bufferForPlaybackAfterRebufferMs,
                )
                .setBufferDurationsMsForLocalPlayback(
                    high.minBufferMs,
                    high.maxBufferMs,
                    high.bufferForPlaybackMs,
                    high.bufferForPlaybackAfterRebufferMs,
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        }
    }
}
