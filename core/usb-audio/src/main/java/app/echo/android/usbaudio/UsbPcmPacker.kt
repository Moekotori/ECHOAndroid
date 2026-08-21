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
        volume: Float = 1f,
    ): Int {
        val channels = channelCount.coerceAtLeast(1)
        val destStride = destBytesPerSample.coerceIn(1, 4)
        val outBytes = frames.coerceAtLeast(0) * channels * destStride
        if (outBytes == 0) return 0
        require(destinationOffset >= 0 && destinationOffset + outBytes <= destination.size) {
            "USB PCM destination is too small"
        }
        val safeVolume = volume.coerceAtLeast(0f)
        if (canCopyDirectly(sourceEncoding, destStride, source.order(), safeVolume)) {
            source.get(destination, destinationOffset, outBytes)
            return outBytes
        }
        var destIndex = destinationOffset
        repeat(frames) {
            repeat(channels) {
                val sample = scaleSample(readNormalizedSample(source, sourceEncoding), safeVolume)
                writeIntegerSample(destination, destIndex, destStride, sample)
                destIndex += destStride
            }
        }
        return outBytes
    }

    fun sourceBytesPerFrame(sourceEncoding: UsbPcmSourceEncoding, channelCount: Int): Int =
        sourceEncoding.bytesPerSample * channelCount.coerceAtLeast(1)

    fun canCopyDirectly(
        sourceEncoding: UsbPcmSourceEncoding,
        destBytesPerSample: Int,
        sourceOrder: ByteOrder,
        volume: Float,
    ): Boolean =
        volume in 0.999f..1.001f &&
            sourceOrder == ByteOrder.LITTLE_ENDIAN &&
            when (sourceEncoding) {
                UsbPcmSourceEncoding.Pcm16 -> destBytesPerSample == 2
                UsbPcmSourceEncoding.Pcm24Packed -> destBytesPerSample == 3
                UsbPcmSourceEncoding.Pcm32 -> destBytesPerSample == 4
                UsbPcmSourceEncoding.Pcm24In32,
                UsbPcmSourceEncoding.PcmFloat,
                -> false
            }

    private fun scaleSample(sample: Int, volume: Float): Int =
        when {
            volume in 0.999f..1.001f -> sample
            volume <= 0.001f -> 0
            else -> {
                val scaled = (sample.toLong() * (volume * 65536.0).toLong()) shr 16
                scaled.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
            }
        }

    private fun readNormalizedSample(source: ByteBuffer, encoding: UsbPcmSourceEncoding): Int {
        val order = source.order()
        return when (encoding) {
            UsbPcmSourceEncoding.Pcm16 -> {
                val short = if (order == ByteOrder.LITTLE_ENDIAN) source.short else java.lang.Short.reverseBytes(source.short)
                short.toInt() shl 16
            }
            UsbPcmSourceEncoding.Pcm24Packed -> readPackedPcm24(source, order)
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

    private fun readPackedPcm24(source: ByteBuffer, order: ByteOrder): Int {
        val b0 = source.get().toInt() and 0xff
        val b1 = source.get().toInt() and 0xff
        val b2 = source.get().toInt() and 0xff
        val packed = if (order == ByteOrder.LITTLE_ENDIAN) {
            b0 or (b1 shl 8) or (b2 shl 16)
        } else {
            (b0 shl 16) or (b1 shl 8) or b2
        }
        return packed shl 8
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
    Pcm24Packed(3),
    Pcm24In32(4),
    Pcm32(4),
    PcmFloat(4),
}
