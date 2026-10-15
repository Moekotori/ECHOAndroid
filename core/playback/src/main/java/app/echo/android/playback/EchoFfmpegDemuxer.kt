package app.echo.android.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import java.io.IOException
import java.nio.ByteBuffer

/** Private JNI contract for the four explicitly enabled audio containers. */
@UnstableApi
internal class EchoFfmpegDemuxer : AutoCloseable {
    private var handle = 0L

    fun open(input: EchoFfmpegInput, container: String) {
        if (!FfmpegLibrary.isAvailable()) throw IOException("FFmpeg audio backend is unavailable")
        handle = nativeOpen(input, container, input.canSeek())
        if (handle == 0L) throw IOException("Unable to open audio container")
    }
    fun codec(): String = nativeCodec(handle)
    // rate, channels, coded bits, block alignment, codec tag, bit rate, duration us
    fun info(): LongArray = nativeInfo(handle)
    fun extraData(): ByteArray = nativeExtraData(handle)
    fun readPacket(): Int = nativeReadPacket(handle)
    fun packetData(): ByteBuffer = nativePacketData(handle)
    fun packetTimeUs(): Long = nativePacketTimeUs(handle)
    fun seek(timeUs: Long) = nativeSeek(handle, timeUs)

    override fun close() {
        if (handle != 0L) nativeClose(handle)
        handle = 0
    }

    private external fun nativeOpen(input: EchoFfmpegInput, container: String, seekable: Boolean): Long
    private external fun nativeCodec(handle: Long): String
    private external fun nativeInfo(handle: Long): LongArray
    private external fun nativeExtraData(handle: Long): ByteArray
    private external fun nativeReadPacket(handle: Long): Int
    private external fun nativePacketData(handle: Long): ByteBuffer
    private external fun nativePacketTimeUs(handle: Long): Long
    private external fun nativeSeek(handle: Long, timeUs: Long)
    private external fun nativeClose(handle: Long)
}
