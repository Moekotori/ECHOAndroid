package app.echo.android.listening

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import app.echo.android.model.listening.EchoListeningAudio
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal class EchoListeningAndroidPlayer(
    context: android.content.Context,
) : EchoListeningAudioSink {
    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val jitter = EchoListeningJitter()
    private val phaseFlow = MutableStateFlow(EchoListeningAudio.Off)
    override val phase: StateFlow<EchoListeningAudio> = phaseFlow

    private val silence = ByteArray(FRAME_BYTES)
    @Volatile private var gain = 1f

    // Every worker owns its control state; a previous worker that outlives its 1.5s join
    // window must never see a revived "running" flag or clear the new session's track/focus.
    private class SessionState(val pcmScratch: ByteArray = ByteArray(FRAME_BYTES)) {
        @Volatile var running = true
        @Volatile var halt = Halt.None
        @Volatile var track: AudioTrack? = null
        @Volatile var focusRequest: AudioFocusRequest? = null
        @Volatile var floatOutput = false
    }

    @Volatile private var active: SessionState? = null
    private var worker: Thread? = null
    @Volatile private var session = 0

    override fun setVolume(volume: Float) {
        gain = volume.coerceIn(0f, 1f)
        active?.track?.setVolume(gain)
    }

    override fun offer(packet: EchoListeningPacket) {
        jitter.push(packet)
    }

    @Synchronized
    override fun startEpoch(epoch: Long) {
        val previousWorker = AtomicReference(stopLocked())
        jitter.reset(epoch)
        val id = ++session
        val state = SessionState()
        active = state
        phaseFlow.value = EchoListeningAudio.Buffering
        worker = Thread({
            try {
                previousWorker.getAndSet(null)?.join(1_500)
                runLoop(state)
            } finally {
                finishSession(id, state)
            }
        }, "echo-listening-audio").also { it.start() }
    }

    @Synchronized
    override fun stop() {
        active?.halt = Halt.User
        stopLocked()
        phaseFlow.value = EchoListeningAudio.Off
    }

    private fun finishSession(id: Int, state: SessionState) {
        if (id != session) return
        phaseFlow.value = when (state.halt) {
            Halt.Focus -> EchoListeningAudio.Interrupted
            Halt.Failure -> EchoListeningAudio.Error
            else -> EchoListeningAudio.Off
        }
    }

    private fun stopLocked(): Thread? {
        session++
        val state = active
        active = null
        state?.running = false
        state?.track?.pause()
        val current = worker
        worker = null
        val previous = current?.takeIf { it !== Thread.currentThread() }
        previous?.interrupt()
        return previous
    }

    private fun runLoop(state: SessionState) {
        var codec: MediaCodec? = null
        var audioTrack: AudioTrack? = null
        try {
            if (!state.running) return
            if (!requestFocus(state)) {
                state.halt = Halt.Failure
                return
            }
            if (!state.running) return
            codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
            codec.configure(opusFormat(), null, null, 0)
            codec.start()
            audioTrack = newTrack()
            state.track = audioTrack
            audioTrack.setVolume(gain)
            audioTrack.play()
            pump(state, codec, audioTrack)
        } catch (_: Exception) {
            if (state.halt == Halt.None) state.halt = Halt.Failure
        } finally {
            state.track = null
            release(codec, audioTrack)
            abandonFocus(state)
        }
    }

    private fun pump(state: SessionState, codec: MediaCodec, audioTrack: AudioTrack) {
        var pending: EchoListeningOutput? = null
        var receiving = false
        val bufferInfo = MediaCodec.BufferInfo()
        while (state.running) {
            if (pending == null) {
                pending = jitter.poll(SystemClock.elapsedRealtime())
                if (pending == null) jitter.awaitPacket(if (receiving) 5L else 10L)
            }
            when (val frame = pending) {
                is EchoListeningOutput.Packet -> {
                    val index = codec.dequeueInputBuffer(5_000)
                    if (index >= 0) {
                        val buffer = codec.getInputBuffer(index)
                        if (buffer != null) {
                            buffer.clear()
                            buffer.put(frame.packet.payload)
                            val ptsUs = (frame.packet.sequence.toLong() and 0xffff_ffffL) * 20_000L
                            codec.queueInputBuffer(index, 0, frame.packet.payload.size, ptsUs, 0)
                            pending = null
                        }
                    }
                }
                EchoListeningOutput.Gap -> {
                    audioTrack.write(silence, 0, silence.size)
                    pending = null
                    if (!receiving) {
                        receiving = true
                        if (state === active) phaseFlow.value = EchoListeningAudio.Receiving
                    }
                }
                null -> Unit
            }
            drain(state, codec, audioTrack, bufferInfo) {
                if (!receiving) {
                    receiving = true
                    if (state === active) phaseFlow.value = EchoListeningAudio.Receiving
                }
            }
        }
    }

    private fun drain(state: SessionState, codec: MediaCodec, audioTrack: AudioTrack, info: MediaCodec.BufferInfo, onPcm: () -> Unit) {
        while (state.running) {
            when (val index = codec.dequeueOutputBuffer(info, 0)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> return
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val format = codec.outputFormat
                    val rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    state.floatOutput = format.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        format.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    if (rate != SAMPLE_RATE || channels != CHANNELS) {
                        state.halt = Halt.Failure
                        state.running = false
                        return
                    }
                }
                else -> if (index >= 0) {
                    val buffer = codec.getOutputBuffer(index)
                    if (buffer != null && info.size > 0) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        writePcm(state, audioTrack, buffer, info.size)
                        onPcm()
                    }
                    codec.releaseOutputBuffer(index, false)
                } else {
                    return
                }
            }
        }
    }

    private fun writePcm(state: SessionState, audioTrack: AudioTrack, buffer: ByteBuffer, size: Int) {
        if (!state.floatOutput) {
            audioTrack.write(buffer, size, AudioTrack.WRITE_BLOCKING)
            return
        }
        buffer.order(ByteOrder.nativeOrder())
        val samples = size / 4
        var offset = 0
        val scratch = state.pcmScratch
        while (offset < samples) {
            val count = minOf(scratch.size / 2, samples - offset)
            for (index in 0 until count) {
                val value = (buffer.float * 32767f).toInt().coerceIn(-32768, 32767)
                scratch[index * 2] = (value and 0xff).toByte()
                scratch[index * 2 + 1] = (value shr 8).toByte()
            }
            audioTrack.write(scratch, 0, count * 2)
            offset += count
        }
    }

    private fun requestFocus(state: SessionState): Boolean {
        val manager = audioManager ?: return false
        val attributes = mediaAttributes()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener({ change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    if (state.halt == Halt.None) state.halt = Halt.Focus
                    state.running = false
                }
            }, Handler(Looper.getMainLooper()))
            .build()
        state.focusRequest = request
        return manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus(state: SessionState) {
        val request = state.focusRequest ?: return
        state.focusRequest = null
        audioManager?.abandonAudioFocusRequest(request)
    }

    private fun newTrack(): AudioTrack {
        val min = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        return AudioTrack.Builder()
            .setAudioAttributes(mediaAttributes())
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(min, SAMPLE_RATE * FRAME_BYTES / 50))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private fun release(codec: MediaCodec?, audioTrack: AudioTrack?) {
        try {
            codec?.stop()
        } catch (_: Exception) {
            // The decoder may already be stopping.
        }
        codec?.release()
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.stop()
        } catch (_: Exception) {
            // The track may not have reached the playing state.
        }
        audioTrack?.release()
    }

    private enum class Halt { None, User, Focus, Failure }

    private companion object {
        const val SAMPLE_RATE = 48_000
        const val CHANNELS = 2
        const val FRAME_BYTES = 960 * CHANNELS * 2

        fun mediaAttributes(): AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        fun opusFormat(): MediaFormat {
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, SAMPLE_RATE, CHANNELS)
            format.setByteBuffer("csd-0", ByteBuffer.wrap(opusHead()))
            format.setByteBuffer("csd-1", longLe(6_500_000L))
            format.setByteBuffer("csd-2", longLe(80_000_000L))
            format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 4096)
            return format
        }

        fun opusHead(): ByteArray {
            val head = ByteArray(19)
            "OpusHead".encodeToByteArray().copyInto(head)
            head[8] = 1
            head[9] = CHANNELS.toByte()
            head[10] = 0x38
            head[11] = 0x01
            head[12] = 0x80.toByte()
            head[13] = 0xBB.toByte()
            return head
        }

        fun longLe(value: Long): ByteBuffer =
            ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).apply { flip() }
    }
}
