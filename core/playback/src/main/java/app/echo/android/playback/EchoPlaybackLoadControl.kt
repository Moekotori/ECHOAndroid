package app.echo.android.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl

@UnstableApi
internal class EchoPlaybackLoadControl(
    private val delegate: DefaultLoadControl = defaultDelegate(),
) : LoadControl by delegate {
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
