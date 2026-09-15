package app.echo.android.playback

import android.media.AudioFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object EchoSmartTransitionPcm {
    fun bytesPerSample(encoding: Int): Int = when (encoding) {
        AudioFormat.ENCODING_PCM_8BIT -> 1
        AudioFormat.ENCODING_PCM_16BIT -> 2
        AudioFormat.ENCODING_PCM_FLOAT -> 4
        AudioFormat.ENCODING_PCM_32BIT -> 4
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
        else -> when (encoding) {
            21 -> 3
            22 -> 4
            else -> 2
        }
    }

    fun readSample(buffer: ByteBuffer, encoding: Int): Float = when (encoding) {
        AudioFormat.ENCODING_PCM_8BIT -> (buffer.get().toInt() and 0xff) / 128f - 1f
        AudioFormat.ENCODING_PCM_FLOAT -> buffer.float
        AudioFormat.ENCODING_PCM_32BIT, 22 -> buffer.int * (1.0f / 2_147_483_648f)
        AudioFormat.ENCODING_PCM_24BIT_PACKED, 21 -> {
            val b0 = buffer.get().toInt() and 0xff
            val b1 = buffer.get().toInt() and 0xff
            val b2 = buffer.get().toInt() and 0xff
            val packed = b0 or (b1 shl 8) or (b2 shl 16)
            ((packed shl 8) shr 8) / 8_388_608f
        }
        else -> buffer.short / 32_768f
    }

    fun resampleInterleaved(
        input: FloatArray,
        inputRateHz: Int,
        inputChannels: Int,
        outputRateHz: Int,
        outputChannels: Int,
    ): FloatArray {
        if (input.isEmpty() || inputRateHz <= 0 || outputRateHz <= 0) return FloatArray(0)
        val inCh = inputChannels.coerceAtLeast(1)
        val inFrames = input.size / inCh
        if (inFrames <= 0) return FloatArray(0)
        val outCh = outputChannels.coerceIn(1, 2)
        if (inputRateHz == outputRateHz && inCh == outCh) {
            return input.copyOf(inFrames * inCh)
        }
        val outFrames = ((inFrames.toLong() * outputRateHz) / inputRateHz).toInt().coerceAtLeast(1)
        val output = FloatArray(outFrames * outCh)
        for (outFrame in 0 until outFrames) {
            val src = if (inFrames == 1) 0.0 else outFrame.toDouble() * inputRateHz / outputRateHz
            val index = src.toInt().coerceIn(0, inFrames - 1)
            val next = (index + 1).coerceAtMost(inFrames - 1)
            val fraction = (src - index).toFloat().coerceIn(0f, 1f)
            for (channel in 0 until outCh) {
                val a = channelSample(input, index, channel, inCh)
                val b = channelSample(input, next, channel, inCh)
                output[outFrame * outCh + channel] = a + (b - a) * fraction
            }
        }
        return output
    }

    private fun channelSample(input: FloatArray, frame: Int, channel: Int, inputChannels: Int): Float {
        if (inputChannels == 1) return input[frame]
        val source = channel.coerceAtMost(inputChannels - 1)
        return input[frame * inputChannels + source]
    }

    fun prepareBuffer(buffer: ByteBuffer, offset: Int, size: Int): ByteBuffer {
        buffer.position(offset)
        buffer.limit(offset + size)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        return buffer
    }
}
