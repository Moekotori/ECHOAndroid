package app.echo.android.playback

import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import java.nio.ByteBuffer

/** Trim inside decoded blocks before DSP/USB packing. Never copy PCM or recreate the output here. */
@UnstableApi
internal class EchoCuePcmAudioSink(sink: AudioSink) : ForwardingAudioSink(sink) {
    private var clip: EchoCueClip? = null
    private var rate = 0
    private var frameBytes = 0
    private var offsetUs = 0L
    private var input: ByteBuffer? = null
    private var originalLimit = 0
    private var clippedPtsUs = 0L

    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        clip = when (val data = inputFormat.customData) {
            is EchoCueClip -> data
            is EchoCueFormat -> data.clip
            else -> null
        }
        rate = inputFormat.sampleRate
        frameBytes = if (Util.isEncodingLinearPcm(inputFormat.pcmEncoding))
            Util.getPcmFrameSize(inputFormat.pcmEncoding, inputFormat.channelCount) else 0
        super.configure(inputFormat, specifiedBufferSize, outputChannels)
    }

    override fun setOutputStreamOffsetUs(outputStreamOffsetUs: Long) {
        offsetUs = outputStreamOffsetUs
        super.setOutputStreamOffsetUs(outputStreamOffsetUs)
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        val range = clip
        if (range == null || rate <= 0 || frameBytes <= 0) return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        if (input !== buffer) {
            val frames = buffer.remaining() / frameBytes
            val sourceTimeUs = presentationTimeUs - offsetUs
            val skip = cueFramesBefore(range.startUs - sourceTimeUs, rate, frames)
            val end = if (range.endUs > 0) cueFramesBefore(range.endUs - sourceTimeUs, rate, frames) else frames
            if (end <= skip) { buffer.position(buffer.limit()); return true }
            originalLimit = buffer.limit()
            val start = buffer.position()
            buffer.limit(start + end * frameBytes)
            buffer.position(start + skip * frameBytes)
            clippedPtsUs = presentationTimeUs + skip * 1_000_000L / rate
            input = buffer
        }
        if (!super.handleBuffer(buffer, clippedPtsUs, encodedAccessUnitCount)) return false
        buffer.limit(originalLimit)
        buffer.position(originalLimit)
        input = null
        return true
    }

    private fun clearInput() {
        input?.limit(originalLimit)
        input = null
    }

    override fun flush() { clearInput(); super.flush() }
    override fun reset() { clearInput(); clip = null; super.reset() }
}
