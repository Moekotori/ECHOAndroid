package app.echo.android.playback

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import app.echo.android.model.playback.EchoBitPerfectState
import app.echo.android.usbaudio.UsbBitPerfectPacker
import app.echo.android.usbaudio.UsbExclusivePcmOutput
import app.echo.android.usbaudio.UsbExclusivePcmSession
import app.echo.android.usbaudio.UsbExclusiveOutputState
import app.echo.android.usbaudio.UsbPcmFormatSpec
import java.nio.ByteBuffer

/** Dedicated strict sink: no AudioTrack, processors, resampling or gain on its data path. */
@UnstableApi
internal class EchoBitPerfectAudioSink(context: Context) : ForwardingAudioSink(DefaultAudioSink.Builder(context).build()) {
    private val output = UsbExclusivePcmOutput(context)
    private var listener: AudioSink.Listener? = null
    private var session: UsbExclusivePcmSession? = null
    private var format: Format? = null
    private var nextFormat: Format? = null
    private var sourceBits = 0
    private var nextSourceBits = 0
    private var playing = false
    private var volume = 1f
    private var ended = false
    private var firstPts = C.TIME_UNSET
    private var submittedFrames = 0L
    private var discontinuity = false
    private val pending = ByteArray(32_768)
    private var pendingStart = 0
    private var pendingEnd = 0

    fun sourcePrecision(bits: Int) { nextSourceBits = bits }

    override fun setListener(listener: AudioSink.Listener) { this.listener = listener }
    override fun getFormatSupport(format: Format): Int =
        if (format.sampleMimeType == MimeTypes.AUDIO_RAW && format.pcmEncoding in INTEGER_ENCODINGS)
            AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY else AudioSink.SINK_FORMAT_UNSUPPORTED
    override fun supportsFormat(format: Format): Boolean = getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED
    override fun getFormatOffloadSupport(format: Format): AudioOffloadSupport = AudioOffloadSupport.DEFAULT_UNSUPPORTED

    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        if (!supportsFormat(inputFormat) || nextSourceBits !in listOf(16, 24) || inputFormat.channelCount !in 1..2 ||
            inputFormat.sampleRate <= 0 || outputChannels != null || inputFormat.encoderDelay != 0 || inputFormat.encoderPadding != 0) {
            fail(EchoBitPerfectState.UnsupportedSource)
            throw AudioSink.ConfigurationException("Strict USB requires 16/24-bit lossless PCM, mono/stereo", inputFormat)
        }
        nextFormat = inputFormat
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        if (buffer.hasRemaining()) ended = false
        val wanted = nextFormat ?: format ?: return false
        if (!EchoPlaybackProcessRuntime.usbExclusiveEnabled) initializationFailure(EchoBitPerfectState.UsbUnavailable, wanted)
        if (volume != 1f) initializationFailure(EchoBitPerfectState.VolumeChanged, wanted)
        if (session != null && (wanted.sampleRate != format?.sampleRate ||
            wanted.channelCount != format?.channelCount || wanted.pcmEncoding != format?.pcmEncoding ||
            nextSourceBits != sourceBits)) {
            drain()
            if (hasPendingData()) return false
            closeSession()
        }
        if (session == null) {
            val opened = output.open(UsbPcmFormatSpec(wanted.sampleRate, wanted.channelCount, nextSourceBits), bitPerfect = true)
            if (!opened.openResult.isReady) {
                val state = when {
                    opened.openResult.message?.contains("clock-unverified") == true -> EchoBitPerfectState.ClockUnverified
                    opened.openResult.state == UsbExclusiveOutputState.FormatUnavailable -> EchoBitPerfectState.UnsupportedFormat
                    else -> EchoBitPerfectState.UsbUnavailable
                }
                opened.close()
                initializationFailure(state, wanted)
            }
            session = opened
            format = wanted
            sourceBits = nextSourceBits
            firstPts = C.TIME_UNSET
            submittedFrames = 0
            ended = false
        }
        val current = format!!
        if (firstPts == C.TIME_UNSET || discontinuity) {
            firstPts = presentationTimeUs - completedFrames() * 1_000_000L / current.sampleRate
            discontinuity = false
            listener?.onPositionDiscontinuity()
        }
        drain()
        if (pendingEnd > pendingStart) return false
        val bytes = pcmBytes(current.pcmEncoding)
        if (buffer.remaining() % (bytes * current.channelCount) != 0) {
            initializationFailure(EchoBitPerfectState.UnsupportedSource, current)
        }
        try {
            pendingEnd = UsbBitPerfectPacker.pack(buffer, bytes, sourceBits, isBigEndian(current.pcmEncoding),
                session!!.bitResolution!!, session!!.bytesPerSample, pending, current.channelCount)
            pendingStart = 0
        } catch (error: IllegalArgumentException) {
            initializationFailure(EchoBitPerfectState.UnsupportedSource, current)
        }
        drain()
        return !buffer.hasRemaining()
    }

    private fun drain() {
        val current = session ?: return
        if (current.hasTransferError()) {
            val failedFormat = format!!
            fail(EchoBitPerfectState.TransportError)
            throw AudioSink.WriteException(-1, failedFormat, false)
        }
        if (!playing || pendingStart == pendingEnd) return
        if (volume != 1f) initializationFailure(EchoBitPerfectState.VolumeChanged, format!!)
        val result = current.writePcm(pending, pendingStart, pendingEnd - pendingStart)
        val frameBytes = current.bytesPerSample * format!!.channelCount
        if (current.isDisconnected() || result.bytesWritten < 0 || result.bytesWritten % frameBytes != 0 ||
            (result.state != UsbExclusiveOutputState.Streaming && result.state != UsbExclusiveOutputState.Ready)) {
            val failedFormat = format!!
            fail(EchoBitPerfectState.TransportError)
            throw AudioSink.WriteException(-1, failedFormat, false)
        }
        pendingStart += result.bytesWritten
        submittedFrames += result.bytesWritten / frameBytes
        if (result.bytesWritten > 0 && EchoPlaybackProcessRuntime.bitPerfectStatus.state != EchoBitPerfectState.Direct) {
            EchoPlaybackProcessRuntime.setUsbExclusiveSinkStatus(EchoUsbExclusiveSinkStatus(
                true, "isochronous", format!!.sampleRate, current.bitResolution, "Strict integer PCM",
            ))
            EchoPlaybackProcessRuntime.bitPerfectStatus = EchoBitPerfectSnapshot(
                EchoBitPerfectState.Direct, sourceBits, pcmBytes(format!!.pcmEncoding) * 8,
                current.bitResolution, format!!.sampleRate,
            )
        }
        if (pendingStart == pendingEnd) { pendingStart = 0; pendingEnd = 0 }
    }

    private fun completedFrames(): Long = session?.completedFrames()?.coerceAtMost(submittedFrames) ?: 0
    override fun getCurrentPositionUs(sourceEnded: Boolean): Long =
        if (firstPts == C.TIME_UNSET) AudioSink.CURRENT_POSITION_NOT_SET
        else firstPts + completedFrames() * 1_000_000L / format!!.sampleRate
    override fun hasPendingData(): Boolean = pendingEnd > pendingStart || completedFrames() < submittedFrames
    override fun isEnded(): Boolean = ended && !hasPendingData()
    override fun playToEndOfStream() { ended = true; drain() }
    override fun play() { playing = true }
    override fun pause() { playing = false; waiting() }
    override fun handleDiscontinuity() { discontinuity = true }
    override fun setVolume(volume: Float) { this.volume = volume }
    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) = Unit
    override fun getPlaybackParameters(): PlaybackParameters = PlaybackParameters.DEFAULT
    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) { listener?.onSkipSilenceEnabledChanged(false) }
    override fun getSkipSilenceEnabled(): Boolean = false
    override fun flush() { closeSession(); ended = false; waiting() }
    override fun reset() { flush(); nextFormat = null; format = null; super.reset() }
    override fun release() { reset(); super.release() }

    private fun closeSession() {
        session?.close(); session = null
        pendingStart = 0; pendingEnd = 0; submittedFrames = 0; firstPts = C.TIME_UNSET
        EchoPlaybackProcessRuntime.setUsbExclusiveSinkStatus(null)
        waiting()
    }
    private fun waiting() {
        if (EchoPlaybackProcessRuntime.bitPerfectStatus.state == EchoBitPerfectState.Direct) {
            EchoPlaybackProcessRuntime.bitPerfectStatus = EchoPlaybackProcessRuntime.bitPerfectStatus.copy(state = EchoBitPerfectState.Waiting)
        }
    }
    private fun fail(state: EchoBitPerfectState) {
        closeSession()
        EchoPlaybackProcessRuntime.bitPerfectStatus = EchoBitPerfectSnapshot(state)
    }
    private fun initializationFailure(state: EchoBitPerfectState, format: Format): Nothing {
        fail(state)
        throw AudioSink.InitializationException("Strict USB: $state", 0, format, false, null)
    }

    companion object {
        val INTEGER_ENCODINGS = setOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT,
            C.ENCODING_PCM_16BIT_BIG_ENDIAN, C.ENCODING_PCM_24BIT_BIG_ENDIAN, C.ENCODING_PCM_32BIT_BIG_ENDIAN)
        fun pcmBytes(encoding: Int): Int = when (encoding) {
            C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 2
            C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> 3
            else -> 4
        }
        private fun isBigEndian(encoding: Int) = encoding == C.ENCODING_PCM_16BIT_BIG_ENDIAN ||
            encoding == C.ENCODING_PCM_24BIT_BIG_ENDIAN || encoding == C.ENCODING_PCM_32BIT_BIG_ENDIAN
    }
}

internal typealias EchoBitPerfectSnapshot = app.echo.android.model.playback.EchoBitPerfectStatus
