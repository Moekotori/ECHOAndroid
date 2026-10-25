package app.echo.android.playback

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.core.net.toUri
import dev.t1m3.qplayer.audio.core.AudioBuf
import dev.t1m3.qplayer.audio.core.ChannelLayout
import dev.t1m3.qplayer.audio.decode.Decoder
import dev.t1m3.qplayer.audio.decode.TrackSpec
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Streaming analysis decoder. Created, used and closed only on the preparation worker. */
internal class EchoAutoMixDecoder(
    context: Context,
    uri: String,
    private val checkBudget: () -> Unit,
) : Decoder {
    private val extractor = MediaExtractor()
    private var codec: MediaCodec? = null
    private val trackSpec: TrackSpec
    private val info = MediaCodec.BufferInfo()
    private var encoding = AudioFormat.ENCODING_PCM_16BIT
    private var pending: ByteBuffer? = null
    private var pendingIndex = -1
    private var inputDone = false
    private var drained = false
    private var decoded = 0L
    private var seekUs = 0L

    init {
        try {
            checkBudget()
            require(EchoSmartTransitionPolicy.isLocalUri(uri, null))
            extractor.setDataSource(context.applicationContext, uri.toUri(), null)
            val track = (0 until extractor.trackCount).first {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")
            }
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)
            // Bound analysis allocations made by the supplied engine, including harmonic windows.
            require(rate in 8_000..48_000 && channels in 1..2 && durationUs in 1..900_000_000L)
            val mime = requireNotNull(format.getString(MediaFormat.KEY_MIME))
            trackSpec = TrackSpec(mime, rate, ChannelLayout.of(channels), 16,
                durationUs * rate / 1_000_000L, false)
            val active = MediaCodec.createDecoderByType(mime)
            codec = active
            active.configure(format, null, null, 0)
            active.start()
        } catch (error: Exception) {
            close()
            throw error
        }
    }

    override fun spec(): TrackSpec = trackSpec
    override fun framesDecoded(): Long = decoded
    override fun isDrained(): Boolean = drained && pending == null

    override fun seek(secs: Double): Double {
        checkBudget()
        require(secs.isFinite() && secs >= 0)
        releasePending()
        codec!!.flush()
        seekUs = (secs * 1_000_000).toLong()
        extractor.seekTo(seekUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        inputDone = false
        drained = false
        decoded = seekUs * trackSpec.sampleRate / 1_000_000L
        return secs
    }

    override fun decodeInto(out: AudioBuf): Int {
        checkBudget()
        require(out.channels() == trackSpec.layout.count())
        val channels = out.channels()
        val pcm = out.backingArray()
        var written = 0
        while (written < out.capacityFrames()) {
            checkBudget()
            if (pending == null && !nextOutput()) break
            val buffer = pending ?: continue
            val frames = minOf(out.capacityFrames() - written,
                buffer.remaining() / (EchoSmartTransitionPcm.bytesPerSample(encoding) * channels))
            for (frame in 0 until frames) {
                for (channel in 0 until channels) {
                    pcm[out.channelBase(channel) + written + frame] = EchoSmartTransitionPcm.readSample(buffer, encoding)
                }
            }
            written += frames
            if (!buffer.hasRemaining()) releasePending()
        }
        out.setFrames(written)
        decoded += written
        return written
    }

    private fun nextOutput(): Boolean {
        val active = codec!!
        while (!drained) {
            checkBudget()
            if (!inputDone) {
                val index = active.dequeueInputBuffer(5_000)
                if (index >= 0) {
                    val buffer = requireNotNull(active.getInputBuffer(index))
                    val timeUs = extractor.sampleTime
                    val size = extractor.readSampleData(buffer, 0)
                    inputDone = size < 0
                    active.queueInputBuffer(index, 0, maxOf(size, 0), maxOf(timeUs, 0),
                        if (inputDone) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                    if (!inputDone) extractor.advance()
                }
            }
            val index = active.dequeueOutputBuffer(info, 5_000)
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val format = active.outputFormat
                require(format.getInteger(MediaFormat.KEY_SAMPLE_RATE) == trackSpec.sampleRate &&
                    format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == trackSpec.layout.count())
                encoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING))
                    format.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                require(encoding in supportedEncodings)
            } else if (index >= 0) {
                pendingIndex = index
                drained = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                val buffer = active.getOutputBuffer(index)
                if (buffer != null && info.size > 0) {
                    val bytes = EchoSmartTransitionPcm.bytesPerSample(encoding) * trackSpec.layout.count()
                    require(info.size % bytes == 0) { "Incomplete decoded PCM frame" }
                    val skip = (((seekUs - info.presentationTimeUs).coerceAtLeast(0) * trackSpec.sampleRate +
                        999_999L) / 1_000_000L).coerceAtMost((info.size / bytes).toLong()).toInt()
                    buffer.limit(info.offset + info.size)
                    buffer.position(info.offset + skip * bytes)
                    buffer.order(ByteOrder.LITTLE_ENDIAN)
                    if (buffer.hasRemaining()) {
                        pending = buffer
                        return true
                    }
                }
                releasePending()
            }
        }
        return false
    }

    private fun releasePending() {
        if (pendingIndex >= 0) codec?.releaseOutputBuffer(pendingIndex, false)
        pendingIndex = -1
        pending = null
    }

    override fun close() {
        runCatching { releasePending() }
        codec?.let { active ->
            runCatching { active.stop() }
            runCatching { active.release() }
        }
        codec = null
        runCatching { extractor.release() }
    }

    private companion object {
        val supportedEncodings = setOf(AudioFormat.ENCODING_PCM_8BIT, AudioFormat.ENCODING_PCM_16BIT,
            AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_24BIT_PACKED, AudioFormat.ENCODING_PCM_32BIT)
    }
}
