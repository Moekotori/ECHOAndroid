package app.echo.android.usbaudio

import java.nio.ByteBuffer

/** Integer-only PCM packing. Source s32 is left-aligned as returned by libswresample. */
object UsbBitPerfectPacker {
    fun pack(source: ByteBuffer, sourceBytes: Int, sourceBits: Int, bigEndian: Boolean,
        outputBits: Int, outputBytes: Int, destination: ByteArray, channels: Int): Int {
        require(sourceBytes in 2..4 && sourceBits in listOf(16, 24))
        require(outputBits in sourceBits..32 && outputBits <= outputBytes * 8)
        require(channels in 1..2)
        val frames = minOf(source.remaining() / (sourceBytes * channels), destination.size / (outputBytes * channels))
        var out = 0
        repeat(frames * channels) {
            var raw = 0
            repeat(sourceBytes) { byte ->
                val shift = (if (bigEndian) sourceBytes - byte - 1 else byte) * 8
                raw = raw or ((source.get().toInt() and 255) shl shift)
            }
            val normalized = raw shl (32 - sourceBytes * 8)
            // Reject any precision that was not declared; never silently truncate it.
            require((normalized shl sourceBits) == 0) { "Undeclared source precision" }
            val packed = normalized shr (32 - outputBytes * 8)
            repeat(outputBytes) { byte -> destination[out++] = (packed shr (byte * 8)).toByte() }
        }
        return out
    }
}
