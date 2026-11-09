package app.echo.android.playback

import android.media.AudioDeviceInfo
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.AudioOutputProvider
import app.echo.android.model.playback.EchoBitPerfectState
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
    private val destFrameBytes = (destBytesPerSample * channelCount).coerceAtLeast(1)
    private val pending = EchoUsbPendingPcm(sourceEncoding, channelCount, destBytesPerSample, PACKED_BUFFER_BYTES)
    private var playing = false
    private var drainingEndOfStream = false
    @Volatile private var released = false
    private var reportedPositionAdvancing = false
    private var playbackParameters = PlaybackParameters.DEFAULT
    @Volatile
    private var volume: Float = 1f

    private val updateVolume: (app.echo.android.model.playback.EchoUsbOutputOptions) -> Unit = { options ->
        if (!released) session.configureVolumeGain(options.volumeMode.id,
            app.echo.android.model.playback.EchoUsbFineVolume.gainForDevice(options, session.vendorId, session.productId))
    }
    init {
        updateVolume(EchoPlaybackProcessRuntime.usbOutputOptions)
        EchoPlaybackProcessRuntime.usbVolumeUpdater = updateVolume
    }

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
        drainingEndOfStream = false
        session.setPlaybackActive(true)
        if (!session.openResult.isReady) return
        session.setKeepAlive(false)
        publishOutputStatus(streaming = reportedPositionAdvancing)
        // Media3's play() does not declare WriteException.
        try {
            drainPending()
        } catch (error: AudioOutput.WriteException) {
            throw IllegalStateException("USB PCM resume failed", error)
        }
    }

    private fun publishOutputStatus(streaming: Boolean) {
        EchoPlaybackProcessRuntime.setUsbExclusiveSinkStatus(
            EchoUsbExclusiveSinkStatus(
                streaming = streaming,
                transferEvidence = session::transportEvidence,
                volumeEvidence = session::volumeEvidence,
                compatibilityRules = session.compatibilityEvidence(),
                transport = transport,
                sampleRateHz = sampleRateHz,
                bitDepth = session.bitResolution,
                message = "USB exclusive $transport ${sampleRateHz}Hz",
                bufferCapacityFrames = session.capacityFrames().takeIf {
                    session.transport == app.echo.android.usbaudio.UsbEndpointTransferType.Isochronous
                },
            ),
        )
    }

    override fun pause() {
        playing = false
        session.setPlaybackActive(false)
        session.setKeepAlive(true)
        if (reportedPositionAdvancing) publishOutputStatus(streaming = false)
    }

    override fun write(buffer: ByteBuffer, encodedAccessUnitCount: Int, presentationTimeUs: Long): Boolean {
        if (released || session.volumeFailed || !session.openResult.isReady) {
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
        val sourceOrder = if (isBigEndian(outputConfig.encoding)) ByteOrder.BIG_ENDIAN else originalOrder
        buffer.order(sourceOrder)
        if (!playing || !pending.empty) {
            // An incomplete USB packet needs samples from the next PCM block.
            pending.stash(buffer)
            buffer.order(originalOrder)
            if (playing) drainPending()
            return !buffer.hasRemaining()
        }
        val ramp = session.volumeRamp
        val packedBytes = UsbPcmPacker.pack(
            source = buffer,
            sourceEncoding = sourceEncoding,
            frames = frames,
            channelCount = channelCount,
            destBytesPerSample = destBytesPerSample,
            destination = packed,
            volume = mixedVolume(),
            gainRamp = ramp,
        )
        buffer.order(originalOrder)
        val framesWritten = writePacked(packedBytes) / destFrameBytes
        ramp?.commitWrittenFrames(framesWritten)
        if (framesWritten > 0) {
            maybeReportPositionAdvancing()
        }
        if (framesWritten < frames) {
            buffer.position(buffer.position() - (frames - framesWritten) * frameBytes)
            buffer.order(sourceOrder)
            pending.stash(buffer)
            buffer.order(originalOrder)
        }
        return !buffer.hasRemaining()
    }

    override fun flush() {
        reportedPositionAdvancing = false
        drainingEndOfStream = false
        pending.clear()
        session.flush()
        if (playing) {
            if (session.openResult.isReady) publishOutputStatus(streaming = false)
            session.setPlaybackActive(true)
            session.prime()
        } else {
            session.setKeepAlive(true)
        }
    }

    override fun stop() {
        playing = false
        drainingEndOfStream = !pending.empty
        session.setPlaybackActive(false)
        session.setKeepAlive(false)
    }

    override fun release() {
        if (released) return
        released = true
        playing = false
        drainingEndOfStream = false
        pending.clear()
        session.close()
        if (EchoPlaybackProcessRuntime.usbVolumeUpdater === updateVolume) {
            EchoPlaybackProcessRuntime.usbVolumeUpdater = null
            EchoPlaybackProcessRuntime.setUsbExclusiveSinkStatus(null)
            if (EchoPlaybackProcessRuntime.bitPerfectStatus.state == EchoBitPerfectState.Compatible) {
                EchoPlaybackProcessRuntime.bitPerfectStatus = EchoBitPerfectSnapshot()
            }
        }
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
        // Media3 polls this while waiting for the stopped output to finish its queued audio.
        if (drainingEndOfStream) {
            try {
                drainingEndOfStream = !drainPending(endOfStream = true)
            } catch (error: AudioOutput.WriteException) {
                throw IllegalStateException("USB PCM drain failed", error)
            }
        }
        val completed = session.completedFrames()
        if (completed < 0) {
            throw IllegalStateException("USB PCM transport failed", AudioOutput.WriteException(-1, false))
        }
        return completed * 1_000_000L / sampleRateHz
    }

    override fun getPlaybackParameters(): PlaybackParameters = playbackParameters

    override fun isStalled(): Boolean = playing && !released && session.isDisconnected()

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        this.playbackParameters = playbackParameters
    }

    override fun setOffloadDelayPadding(delayInFrames: Int, paddingInFrames: Int) = Unit

    override fun setOffloadEndOfStream() { session.setPlaybackActive(false) }

    override fun setPlayerId(playerId: PlayerId) = Unit

    override fun attachAuxEffect(effectId: Int) = Unit

    override fun setAuxEffectSendLevel(level: Float) = Unit

    override fun setPreferredDevice(preferredDevice: AudioDeviceInfo?) = Unit

    private fun mixedVolume(): Float =
        volume * EchoPlaybackProcessRuntime.exclusiveMakeupGain

    private fun drainPending(endOfStream: Boolean = false): Boolean {
        if (pending.empty) return true
        val ramp = session.volumeRamp
        val packedBytes = pending.pack(packed, mixedVolume(), ramp)
        val written = writePacked(packedBytes, endOfStream)
        ramp?.commitWrittenFrames(written / destFrameBytes)
        if (written > 0) {
            pending.consume(written)
            maybeReportPositionAdvancing()
        }
        return pending.empty
    }

    private fun writePacked(packedBytes: Int, endOfStream: Boolean = false): Int {
        if (session.volumeFailed) throw AudioOutput.WriteException(-1, false)
        val result = session.writePcm(packed, 0, packedBytes, endOfStream)
        if (result.state == UsbExclusiveOutputState.OpenFailed || result.state == UsbExclusiveOutputState.Closed ||
            result.state == UsbExclusiveOutputState.UnsupportedTransport ||
            result.bytesWritten !in 0..packedBytes || result.bytesWritten % destFrameBytes != 0) {
            val canRetry = result.state == UsbExclusiveOutputState.OpenFailed && !session.hasTransferError()
            throw AudioOutput.WriteException(-1, canRetry)
        }
        return result.bytesWritten
    }

    private fun maybeReportPositionAdvancing() {
        if (reportedPositionAdvancing) return
        reportedPositionAdvancing = true
        if (playing) publishOutputStatus(streaming = true)
        val nowMs = SystemClock.elapsedRealtime()
        listeners.forEach { listener -> listener.onPositionAdvancing(nowMs) }
    }

    override fun canReuseAudioOutput(
        currentOutputConfig: AudioOutputProvider.OutputConfig,
        newFormatConfig: AudioOutputProvider.FormatConfig,
        newOutputConfig: AudioOutputProvider.OutputConfig,
    ): Boolean =
        // Keeping the same USB session across a track transition is what makes
        // gapless playback possible: rebuilding it re-claims the streaming
        // interface and re-programs the clock, which is audible.
        !released && !session.isDisconnected() && newOutputConfig == currentOutputConfig

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
    val bufferCapacityFrames: Long? = null,
    val transferEvidence: (() -> app.echo.android.model.playback.EchoUsbTransportDiagnostics?)? = null,
    val compatibilityRules: List<app.echo.android.model.playback.EchoUsbCompatibilityRule> = emptyList(),
    val volumeEvidence: (() -> app.echo.android.model.playback.EchoUsbVolumeDiagnostics?)? = null,
)
