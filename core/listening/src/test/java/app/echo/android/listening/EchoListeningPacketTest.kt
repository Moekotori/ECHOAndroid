package app.echo.android.listening

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EchoListeningPacketTest {
    @Test fun parsesAFixedOpusFrame() {
        val packet = EchoListeningPackets.parse(frame(epoch = 7, sequence = 3, payload = ByteArray(8) { 1 }))
        assertEquals(7L, packet?.epoch)
        assertEquals(3, packet?.sequence)
        assertEquals(8, packet?.payload?.size)
    }

    @Test fun rejectsAShortOrForeignFrame() {
        assertNull(EchoListeningPackets.parse(ByteArray(10)))
        val wrongRate = frame(epoch = 1, sequence = 1, payload = ByteArray(4), sampleRate = 44_100)
        assertNull(EchoListeningPackets.parse(wrongRate))
        assertNull(EchoListeningPackets.parse(frame(epoch = 0, sequence = 1, payload = ByteArray(4))))
    }
}

internal fun frame(
    epoch: Long,
    sequence: Int,
    payload: ByteArray,
    sampleRate: Int = 48_000,
): ByteArray {
    val bytes = ByteArray(36 + payload.size)
    bytes[0] = 'E'.code.toByte()
    bytes[1] = 'L'.code.toByte()
    bytes[2] = 'T'.code.toByte()
    bytes[3] = 'A'.code.toByte()
    bytes[4] = 1
    ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).apply {
        putShort(6, 36)
        putLong(8, epoch)
        putInt(16, sequence)
        putInt(28, sampleRate)
        putShort(32, payload.size.toShort())
    }
    bytes[34] = 2
    bytes[35] = 20
    payload.copyInto(bytes, 36)
    return bytes
}
