package app.echo.android.playback

import app.echo.android.usbaudio.UsbPcmSourceEncoding
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class EchoUsbPendingPcmTest {
    @Test fun prebufferUsesCurrentGainAndRetainsOnlyUnwrittenFrames() {
        val pending = EchoUsbPendingPcm(UsbPcmSourceEncoding.Pcm16, 1, 2, 8)
        val input = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in listOf(10000,20000,-10000,-20000)) input.putShort(sample.toShort())
        input.flip()
        assertEquals(8, pending.stash(input))
        val output = ByteArray(8)
        assertEquals(8, pending.pack(output, .5f, null))
        assertEquals(5000, ByteBuffer.wrap(output).order(ByteOrder.LITTLE_ENDIAN).short.toInt())
        pending.consume(2)
        assertEquals(6, pending.pack(output, 1f, null))
        assertEquals(20000, ByteBuffer.wrap(output).order(ByteOrder.LITTLE_ENDIAN).short.toInt())
        pending.consume(6)
        assertTrue(pending.empty)
    }
}
