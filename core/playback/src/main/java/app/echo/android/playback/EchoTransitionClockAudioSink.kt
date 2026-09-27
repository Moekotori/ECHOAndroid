package app.echo.android.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import java.nio.ByteBuffer

/** Media3's sink flushes processors with DEFAULT metadata; retain the actual media timestamp. */
@UnstableApi
internal class EchoTransitionClockAudioSink(
    sink: AudioSink,
    private val processor: EchoDspAudioProcessor,
) : ForwardingAudioSink(sink) {
    private var streamOffsetUs = 0L

    override fun setOutputStreamOffsetUs(outputStreamOffsetUs: Long) {
        streamOffsetUs = outputStreamOffsetUs
        super.setOutputStreamOffsetUs(outputStreamOffsetUs)
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        processor.inputPositionUs = (presentationTimeUs - streamOffsetUs).coerceAtLeast(0)
        return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
    }
}
