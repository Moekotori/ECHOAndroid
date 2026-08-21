package app.echo.android.usbaudio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

object UsbPcmPacker {
    fun bytesPerSample(bitDepth: Int, subslotSize: Int?): Int =
        subslotSize?.takeIf { it in 1..4 } ?: ((bitDepth + 7) / 8).coerceIn(1, 4)

    fun pack(
        source: ByteBuffer,
        sourceEncoding: UsbPcmSourceEncoding,
        frames: Int,
        channelCount: Int,
        destBytesPerSample: Int,
        destination: ByteArray,
        destinationOffset: Int = 0,
    ): Int {
        val channels = channelCount.coerceAtLeast(1)
        val destStride = destBytesPerSample.coerceIn(1, 4)
        val outBytes = frames.coerceAtLeast(0) * channels * destStride
        if (outBytes == 0) return 0
        require(destinationOffset >= 0 && destinationOffset + outBytes <= destination.size) {
            "USB PCM destination is too small"
        }
        var destIndex = destinationOffset
        repeat(frames) {
            repeat(channels) {
                val sample = readNormalizedSample(source, sourceEncoding)
                writeIntegerSample(destination, destIndex, destStride, sample)
                destIndex += destStride
            }
        }
        return outBytes
    }

    fun sourceBytesPerFrame(sourceEncoding: UsbPcmSourceEncoding, channelCount: Int): Int =
        sourceEncoding.bytesPerSample * channelCount.coerceAtLeast(1)

    private fun readNormalizedSample(source: ByteBuffer, encoding: UsbPcmSourceEncoding): Int {
        val order = source.order()
        return when (encoding) {
            UsbPcmSourceEncoding.Pcm16 -> {
                val short = if (order == ByteOrder.LITTLE_ENDIAN) source.short else java.lang.Short.reverseBytes(source.short)
                short.toInt() shl 16
            }
            UsbPcmSourceEncoding.Pcm24In32 -> {
                val word = if (order == ByteOrder.LITTLE_ENDIAN) source.int else Integer.reverseBytes(source.int)
                word shl 8
            }
            UsbPcmSourceEncoding.Pcm32 -> {
                val word = if (order == ByteOrder.LITTLE_ENDIAN) source.int else Integer.reverseBytes(source.int)
                word
            }
            UsbPcmSourceEncoding.PcmFloat -> {
                val value = source.float.coerceIn(-1f, 1f)
                (value * Int.MAX_VALUE).roundToInt()
            }
        }
    }

    private fun writeIntegerSample(destination: ByteArray, offset: Int, destBytesPerSample: Int, sample: Int) {
        when (destBytesPerSample) {
            1 -> destination[offset] = (sample shr 24).toByte()
            2 -> {
                val value = sample shr 16
                destination[offset] = value.toByte()
                destination[offset + 1] = (value shr 8).toByte()
            }
            3 -> {
                val value = sample shr 8
                destination[offset] = value.toByte()
                destination[offset + 1] = (value shr 8).toByte()
                destination[offset + 2] = (value shr 16).toByte()
            }
            else -> {
                destination[offset] = sample.toByte()
                destination[offset + 1] = (sample shr 8).toByte()
                destination[offset + 2] = (sample shr 16).toByte()
                destination[offset + 3] = (sample shr 24).toByte()
            }
        }
    }
}

enum class UsbPcmSourceEncoding(val bytesPerSample: Int) {
    Pcm16(2),
    Pcm24In32(4),
    Pcm32(4),
    PcmFloat(4),
}
