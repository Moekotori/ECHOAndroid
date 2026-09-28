package app.echo.android.listening

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class EchoListeningPacket(
    val epoch: Long,
    val sequence: Int,
    val payload: ByteArray,
)

object EchoListeningPackets {
    const val HEADER_BYTES = 36
    const val MAX_PAYLOAD = 1275
    const val MAX_PACKET = HEADER_BYTES + MAX_PAYLOAD

    fun parse(bytes: ByteArray, length: Int = bytes.size): EchoListeningPacket? {
        if (length < HEADER_BYTES || length > MAX_PACKET) return null
        if (bytes[0] != 'E'.code.toByte() || bytes[1] != 'L'.code.toByte() ||
            bytes[2] != 'T'.code.toByte() || bytes[3] != 'A'.code.toByte()
        ) {
            return null
        }
        if (bytes[4].toInt() != 1) return null
        val headerLength = u16(bytes, 6)
        if (headerLength != HEADER_BYTES) return null
        val epoch = u64(bytes, 8)
        if (epoch <= 0L) return null
        val sequence = u32(bytes, 16)
        val sampleRate = u32(bytes, 28)
        if (sampleRate != 48_000) return null
        val payloadLength = u16(bytes, 32)
        if (payloadLength <= 0 || payloadLength > MAX_PAYLOAD) return null
        if (HEADER_BYTES + payloadLength != length) return null
        if (bytes[34].toInt() != 2 || bytes[35].toInt() != 20) return null
        val payload = bytes.copyOfRange(HEADER_BYTES, length)
        return EchoListeningPacket(epoch = epoch, sequence = sequence, payload = payload)
    }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun u32(bytes: ByteArray, offset: Int): Int =
        ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int

    private fun u64(bytes: ByteArray, offset: Int): Long =
        ByteBuffer.wrap(bytes, offset, 8).order(ByteOrder.LITTLE_ENDIAN).long
}
