package app.echo.android.playback

import android.media.AudioDeviceInfo
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.AudioOutputProvider
import app.echo.android.usbaudio.UsbExclusiveOutputState
import app.echo.android.usbaudio.UsbExclusivePcmSession
import app.echo.android.usbaudio.UsbPcmPacker
import app.echo.android.usbaudio.UsbPcmSourceEncoding
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CopyOnWriteArrayList

@UnstableApi
internal class EchoUsbExclusiveAudioOutput(
    private val session: UsbExclusivePcmSession,
    private val outputConfig: AudioOutputProvider.OutputConfig,
    private val sourceEncoding: UsbPcmSourceEncoding,
    private val destBytesPerSample: Int,
) : AudioOutput {
    private val listeners = CopyOnWriteArrayList<AudioOutput.Listener>()
    private val packed = ByteArray(PACKED_BUFFER_BYTES)
    private val channelCount = Integer.bitCount(outputConfig.channelMask).coerceAtLeast(1)
    private val sampleRateHz = outputConfig.sampleRate
    private var playing = false
    private var released = false
    private var playbackParameters = PlaybackParameters.DEFAULT

    val transport: String
        get() = session.transport?.name?.lowercase() ?: "usb"

    override fun addListener(listener: AudioOutput.Listener) {
        listeners += listener
    }

    override fun removeListener(listener: AudioOutput.Listener) {
        listeners -= listener
    }

    override fun play() {
        playing = true
        EchoPlaybackProcessRuntime.setUsbExclusiveSinkStatus(
            EchoUsbExclusiveSinkStatus(
                streaming = true,
                transport = transport,
                sampleRateHz = sampleRateHz,
                bitDepth = destBytesPerSample * 8,
                message = "USB exclusive $transport ${sampleRateHz}Hz",
            ),
        )
    }

    override fun pause() {
        playing = false
    }

    override fun write(buffer: ByteBuffer, encodedAccessUnitCount: Int, presentationTimeUs: Long): Boolean {
        if (released) {
            throw AudioOutput.WriteException(-1, false)
        }
        if (!playing) return false
        val frameBytes = UsbPcmPacker.sourceBytesPerFrame(sourceEncoding, channelCount)
        if (frameBytes <= 0 || buffer.remaining() < frameBytes) return true
        val frames = (buffer.remaining() / frameBytes).coerceAtMost(packed.size / (destBytesPerSample * channelCount))
        if (frames <= 0) return false
        val originalOrder = buffer.order()
        if (isBigEndian(outputConfig.encoding)) {
            buffer.order(ByteOrder.BIG_ENDIAN)
        }
        val packedBytes = UsbPcmPacker.pack(
            source = buffer,
            sourceEncoding = sourceEncoding,
            frames = frames,
            channelCount = channelCount,
            destBytesPerSample = destBytesPerSample,
            destination = packed,
        )
        buffer.order(originalOrder)
        val result = session.writePcm(packed, 0, packedBytes)
        if (result.state == UsbExclusiveOutputState.OpenFailed) {
            throw AudioOutput.WriteException(-1, true)
        }
        val destFrameBytes = destBytesPerSample * channelCount
        val framesWritten = if (destFrameBytes <= 0) 0 else result.bytesWritten / destFrameBytes
        if (framesWritten < frames) {
            buffer.position(buffer.position() - (frames - framesWritten) * frameBytes)
            return false
        }
        return !buffer.hasRemaining()
    }

    override fun flush() {
        playing = false
    }

    override fun stop() {
        playing = false
    }

    override fun release() {
        if (released) return
        released = true
        playing = false
        session.close()
        EchoPlaybackProcessRuntime.setUsbExclusiveSinkStatus(null)
        listeners.forEach { it.onReleased() }
    }

    override fun setVolume(volume: Float) = Unit

    override fun isOffloadedPlayback(): Boolean = false

    override fun getAudioSessionId(): Int = 0

    override fun getSampleRate(): Int = sampleRateHz

    override fun getBufferSizeInFrames(): Long = session.queuedFrames().coerceAtLeast(1L)

    override fun getPositionUs(): Long {
        if (sampleRateHz <= 0) return 0L
        return session.completedFrames() * 1_000_000L / sampleRateHz
    }

    override fun getPlaybackParameters(): PlaybackParameters = playbackParameters

    override fun isStalled(): Boolean = false

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        this.playbackParameters = PlaybackParameters.DEFAULT
    }

    override fun setOffloadDelayPadding(delayInFrames: Int, paddingInFrames: Int) = Unit

    override fun setOffloadEndOfStream() = Unit

    override fun setPlayerId(playerId: PlayerId) = Unit

    override fun attachAuxEffect(effectId: Int) = Unit

    override fun setAuxEffectSendLevel(level: Float) = Unit

    override fun setPreferredDevice(preferredDevice: AudioDeviceInfo?) = Unit

    override fun canReuseAudioOutput(
        currentOutputConfig: AudioOutputProvider.OutputConfig,
        newFormatConfig: AudioOutputProvider.FormatConfig,
        newOutputConfig: AudioOutputProvider.OutputConfig,
    ): Boolean = false

    private fun isBigEndian(encoding: Int): Boolean =
        encoding == C.ENCODING_PCM_16BIT_BIG_ENDIAN ||
            encoding == C.ENCODING_PCM_24BIT_BIG_ENDIAN ||
            encoding == C.ENCODING_PCM_32BIT_BIG_ENDIAN

    private companion object {
        const val PACKED_BUFFER_BYTES = 32_768
    }
}

data class EchoUsbExclusiveSinkStatus(
    val streaming: Boolean,
    val transport: String?,
    val sampleRateHz: Int?,
    val bitDepth: Int?,
    val message: String?,
)
