package app.echo.android.listening

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoListeningJitterTest {
    @Test fun startsAfterTheTargetAndKeepsOrder() {
        val jitter = EchoListeningJitter(targetPackets = 3, maxAhead = 6, gapWaitMs = 40)
        jitter.reset(4)
        assertTrue(jitter.push(packet(4, 2)))
        assertTrue(jitter.push(packet(4, 1)))
        assertNull(jitter.poll(0))
        assertTrue(jitter.push(packet(4, 0)))
        assertEquals(0, (jitter.poll(0) as EchoListeningOutput.Packet).packet.sequence)
        assertEquals(1, (jitter.poll(0) as EchoListeningOutput.Packet).packet.sequence)
    }

    @Test fun dropsAnotherEpochAndPacketsPastTheCap() {
        val jitter = EchoListeningJitter(targetPackets = 2, maxAhead = 3, gapWaitMs = 40)
        jitter.reset(1)
        assertTrue(!jitter.push(packet(2, 0)))
        repeat(4) { index -> jitter.push(packet(1, index)) }
        jitter.poll(0)
        assertTrue(!jitter.push(packet(1, 20)))
    }

    @Test fun skipsAMissingFrameAfterTheWait() {
        val jitter = EchoListeningJitter(targetPackets = 1, maxAhead = 5, gapWaitMs = 40)
        jitter.reset(1)
        jitter.push(packet(1, 0))
        jitter.push(packet(1, 2))
        assertEquals(0, (jitter.poll(100) as EchoListeningOutput.Packet).packet.sequence)
        assertNull(jitter.poll(100))
        assertEquals(EchoListeningOutput.Gap, jitter.poll(150))
        assertEquals(2, (jitter.poll(150) as EchoListeningOutput.Packet).packet.sequence)
    }

    private fun packet(epoch: Long, sequence: Int) =
        EchoListeningPacket(epoch, sequence, ByteArray(4))
}
