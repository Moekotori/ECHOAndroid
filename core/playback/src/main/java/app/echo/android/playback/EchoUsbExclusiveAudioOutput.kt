package app.echo.android.playback

import android.media.AudioDeviceInfo
import android.os.SystemClock
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
    private val pendingPacked = ByteArray(PACKED_BUFFER_BYTES)
    private val channelCount = Integer.bitCount(outputConfig.channelMask).coerceAtLeast(1)
    private val sampleRateHz = outputConfig.sampleRate
    private val destFrameBytes = (destBytesPerSample * channelCount).coerceAtLeast(1)
    private var pendingBytes = 0
    private var playing = false
    private var released = false
    private var reportedPositionAdvancing = false
    private var playbackParameters = PlaybackParameters.DEFAULT
    @Volatile
    private var volume: Float = 1f

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
        session.setKeepAlive(false)
        runCatching { drainPending() }
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
        session.setKeepAlive(true)
    }

    override fun write(buffer: ByteBuffer, encodedAccessUnitCount: Int, presentationTimeUs: Long): Boolean {
        if (released) {
            throw AudioOutput.WriteException(-1, false)
        }
        val frameBytes = UsbPcmPacker.sourceBytesPerFrame(sourceEncoding, channelCount)
        if (frameBytes <= 0) return true
        if (buffer.remaining() < frameBytes) {
            buffer.position(buffer.limit())
            return true
        }
        val frames = (buffer.remaining() / frameBytes).coerceAtMost(packed.size / destFrameBytes)
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
            volume = mixedVolume(),
        )
        buffer.order(originalOrder)
        if (!playing) {
            val stashed = stashPending(packedBytes)
            val framesStashed = stashed / destFrameBytes
            if (framesStashed < frames) {
                buffer.position(buffer.position() - (frames - framesStashed) * frameBytes)
                return false
            }
            return !buffer.hasRemaining()
        }
        if (!drainPending()) {
            buffer.position(buffer.position() - frames * frameBytes)
            return false
        }
        val result = session.writePcm(packed, 0, packedBytes)
        if (
            result.state == UsbExclusiveOutputState.OpenFailed ||
            result.state == UsbExclusiveOutputState.Closed ||
            result.state == UsbExclusiveOutputState.UnsupportedTransport
        ) {
            throw AudioOutput.WriteException(
                -1,
                result.state == UsbExclusiveOutputState.OpenFailed,
            )
        }
        val framesWritten = result.bytesWritten / destFrameBytes
        if (framesWritten > 0) {
            maybeReportPositionAdvancing()
        }
        if (framesWritten < frames) {
            buffer.position(buffer.position() - (frames - framesWritten) * frameBytes)
            return false
        }
        return !buffer.hasRemaining()
    }

    override fun flush() {
        reportedPositionAdvancing = false
        pendingBytes = 0
        session.flush()
        if (playing) {
            session.prime()
        } else {
            session.setKeepAlive(true)
        }
    }

    override fun stop() {
        playing = false
        session.setKeepAlive(false)
    }

    override fun release() {
        if (released) return
        released = true
        playing = false
        session.close()
        EchoPlaybackProcessRuntime.setUsbExclusiveSinkStatus(null)
        listeners.forEach { it.onReleased() }
    }

    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
    }

    override fun isOffloadedPlayback(): Boolean = false

    override fun getAudioSessionId(): Int = 0

    override fun getSampleRate(): Int = sampleRateHz

    override fun getBufferSizeInFrames(): Long = session.capacityFrames().coerceAtLeast(1L)

    override fun getPositionUs(): Long {
        if (sampleRateHz <= 0) return 0L
        return session.completedFrames() * 1_000_000L / sampleRateHz
    }

    override fun getPlaybackParameters(): PlaybackParameters = playbackParameters

    override fun isStalled(): Boolean = playing && !released && session.isDisconnected()

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        this.playbackParameters = playbackParameters
    }

    override fun setOffloadDelayPadding(delayInFrames: Int, paddingInFrames: Int) = Unit

    override fun setOffloadEndOfStream() = Unit

    override fun setPlayerId(playerId: PlayerId) = Unit

    override fun attachAuxEffect(effectId: Int) = Unit

    override fun setAuxEffectSendLevel(level: Float) = Unit

    override fun setPreferredDevice(preferredDevice: AudioDeviceInfo?) = Unit

    private fun mixedVolume(): Float =
        volume * EchoPlaybackProcessRuntime.exclusiveMakeupGain

    private fun drainPending(): Boolean {
        if (pendingBytes <= 0) return true
        val result = session.writePcm(pendingPacked, 0, pendingBytes)
        if (
            result.state == UsbExclusiveOutputState.OpenFailed ||
            result.state == UsbExclusiveOutputState.Closed ||
            result.state == UsbExclusiveOutputState.UnsupportedTransport
        ) {
            throw AudioOutput.WriteException(
                -1,
                result.state == UsbExclusiveOutputState.OpenFailed,
            )
        }
        val written = result.bytesWritten.coerceAtLeast(0)
        if (written <= 0) return false
        if (written >= pendingBytes) {
            pendingBytes = 0
            if (written > 0) maybeReportPositionAdvancing()
            return true
        }
        System.arraycopy(pendingPacked, written, pendingPacked, 0, pendingBytes - written)
        pendingBytes -= written
        maybeReportPositionAdvancing()
        return false
    }

    private fun stashPending(packedBytes: Int): Int {
        val space = pendingPacked.size - pendingBytes
        val copy = packedBytes.coerceAtMost(space)
        if (copy <= 0) return 0
        System.arraycopy(packed, 0, pendingPacked, pendingBytes, copy)
        pendingBytes += copy
        return copy
    }

    private fun maybeReportPositionAdvancing() {
        if (reportedPositionAdvancing) return
        reportedPositionAdvancing = true
        val nowMs = SystemClock.elapsedRealtime()
        listeners.forEach { listener -> listener.onPositionAdvancing(nowMs) }
    }

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
