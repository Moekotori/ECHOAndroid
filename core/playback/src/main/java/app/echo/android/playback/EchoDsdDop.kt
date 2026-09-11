package app.echo.android.playback

import app.echo.android.model.playback.EchoDsdRates

internal data class EchoDsdDopPackResult(
    val bytesWritten: Int,
    val frames: Int,
    val nextMarkerA: Boolean,
)

/** 24-bit DoP: marker in bits 23–16, first 8 DSD bits in 15–8, next 8 in 7–0. */
internal object EchoDsdDop {
    const val MarkerA: Byte = 0x05
    const val MarkerB: Byte = 0xFA.toByte()
    const val PcmBytesPerChannel = 3
    const val DsdBytesPerChannel = 2
    const val SourceBits = 24

    fun sampleRateHz(decoderPcmRateHz: Int): Int? =
        EchoDsdRates.dopSampleRateFromDecoderPcm(decoderPcmRateHz)

    fun isPlanar(mimeType: String?): Boolean =
        mimeType == EchoDsdMime.LsbfPlanar || mimeType == EchoDsdMime.MsbfPlanar

    fun isLsbf(mimeType: String?): Boolean =
        mimeType == EchoDsdMime.Lsbf || mimeType == EchoDsdMime.LsbfPlanar

    fun frameCount(sourceLength: Int, channelCount: Int): Int {
        if (channelCount !in 1..2 || sourceLength <= 0) return 0
        if (sourceLength % channelCount != 0) return 0
        return (sourceLength / channelCount) / DsdBytesPerChannel
    }

    fun outputSize(sourceLength: Int, channelCount: Int): Int =
        frameCount(sourceLength, channelCount) * channelCount * PcmBytesPerChannel

    fun pack(
        source: ByteArray,
        sourceOffset: Int,
        sourceLength: Int,
        channelCount: Int,
        planar: Boolean,
        lsbf: Boolean,
        startWithMarkerA: Boolean,
        destination: ByteArray,
        destinationOffset: Int = 0,
    ): EchoDsdDopPackResult {
        val frames = frameCount(sourceLength, channelCount)
        val needed = frames * channelCount * PcmBytesPerChannel
        require(sourceOffset >= 0 && sourceLength >= 0 && sourceOffset + sourceLength <= source.size)
        require(destinationOffset >= 0 && destinationOffset + needed <= destination.size)
        var markerA = startWithMarkerA
        var out = destinationOffset
        val bytesPerChannel = if (channelCount == 0) 0 else sourceLength / channelCount
        repeat(frames) { frame ->
            val marker = if (markerA) MarkerA else MarkerB
            markerA = !markerA
            repeat(channelCount) { channel ->
                val firstIndex: Int
                val secondIndex: Int
                if (planar) {
                    val base = sourceOffset + channel * bytesPerChannel + frame * DsdBytesPerChannel
                    firstIndex = base
                    secondIndex = base + 1
                } else {
                    val base = sourceOffset + frame * channelCount * DsdBytesPerChannel
                    firstIndex = base + channel
                    secondIndex = base + channelCount + channel
                }
                var first = source[firstIndex]
                var second = source[secondIndex]
                if (lsbf) {
                    first = reverseBits(first)
                    second = reverseBits(second)
                }
                destination[out++] = second
                destination[out++] = first
                destination[out++] = marker
            }
        }
        return EchoDsdDopPackResult(bytesWritten = needed, frames = frames, nextMarkerA = markerA)
    }

    private fun reverseBits(value: Byte): Byte {
        var bits = value.toInt() and 0xFF
        bits = ((bits and 0xF0) ushr 4) or ((bits and 0x0F) shl 4)
        bits = ((bits and 0xCC) ushr 2) or ((bits and 0x33) shl 2)
        bits = ((bits and 0xAA) ushr 1) or ((bits and 0x55) shl 1)
        return bits.toByte()
    }
}
