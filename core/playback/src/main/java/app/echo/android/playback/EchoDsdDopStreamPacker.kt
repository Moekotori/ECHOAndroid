package app.echo.android.playback

/** Carries at most one normalized DSD byte per channel; padding is only allowed at EOS. */
internal class EchoDsdDopStreamPacker(private val channels: Int, private val rateHz: Int) {
    private val tail = ByteArray(channels)
    private var carried = false
    private var markerA = true
    private var tailTimeUs = 0L
    var outputTimeUs = 0L
        private set

    init { require(channels in 1..2 && rateHz > 0) }

    fun reset() { carried = false; markerA = true; tailTimeUs = 0L }

    fun pack(source: ByteArray, length: Int, planar: Boolean, lsbf: Boolean,
        timeUs: Long, destination: ByteArray): Int {
        require(length in 0..source.size && length % channels == 0)
        val perChannel = length / channels
        val total = perChannel + if (carried) 1 else 0
        val frames = total / 2
        require(destination.size >= frames * channels * 3)
        outputTimeUs = if (carried) tailTimeUs else timeUs
        var out = 0
        repeat(frames) { frame ->
            val marker = if (markerA) EchoDsdDop.MarkerA else EchoDsdDop.MarkerB
            repeat(channels) { channel ->
                val first = byteAt(source, perChannel, channel, frame * 2, planar, lsbf)
                val second = byteAt(source, perChannel, channel, frame * 2 + 1, planar, lsbf)
                destination[out++] = second
                destination[out++] = first
                destination[out++] = marker
            }
            markerA = !markerA
        }
        if (total % 2 != 0) {
            repeat(channels) { channel ->
                tail[channel] = byteAt(source, perChannel, channel, total - 1, planar, lsbf)
            }
            if (perChannel > 0) tailTimeUs = timeUs + (perChannel - 1L) * 1_000_000L / (rateHz * 2L)
        }
        carried = total % 2 != 0
        return out
    }

    fun finish(destination: ByteArray): Int {
        if (!carried) return 0
        require(destination.size >= channels * 3)
        outputTimeUs = tailTimeUs
        repeat(channels) { channel ->
            destination[channel * 3] = 0x69
            destination[channel * 3 + 1] = tail[channel]
            destination[channel * 3 + 2] = if (markerA) EchoDsdDop.MarkerA else EchoDsdDop.MarkerB
        }
        carried = false
        markerA = !markerA
        return channels * 3
    }

    private fun byteAt(source: ByteArray, perChannel: Int, channel: Int, index: Int,
        planar: Boolean, lsbf: Boolean): Byte {
        if (carried && index == 0) return tail[channel]
        val sourceIndex = index - if (carried) 1 else 0
        val value = source[if (planar) channel * perChannel + sourceIndex else sourceIndex * channels + channel]
        return if (lsbf) EchoDsdDop.reverseBits(value) else value
    }
}
