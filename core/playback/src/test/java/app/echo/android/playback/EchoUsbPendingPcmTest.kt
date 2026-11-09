package app.echo.android.playback

import app.echo.android.usbaudio.UsbPcmSourceEncoding
import app.echo.android.usbaudio.UsbPcmGainRamp
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class EchoUsbPendingPcmTest {
    @Test fun shortWritesAdvanceTheGainOnlyForFramesAcceptedByUsb() {
        val pending = EchoUsbPendingPcm(UsbPcmSourceEncoding.Pcm16, 1, 2, 8)
        val input = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        repeat(4) { input.putShort(20_000) }
        input.flip()
        pending.stash(input)
        val output = ByteArray(8)
        val samples = ByteBuffer.wrap(output).order(ByteOrder.LITTLE_ENDIAN)
        val ramp = UsbPcmGainRamp(0f, 1_000).apply { target = 1f }

        pending.pack(output, 1f, ramp)
        val initial = output.copyOf()
        ramp.commitWrittenFrames(0)
        pending.pack(output, 1f, ramp)
        assertArrayEquals(initial, output)

        val secondSample = samples.getShort(2)
        ramp.commitWrittenFrames(1)
        pending.consume(2)
        assertEquals(6, pending.pack(output, 1f, ramp))
        assertEquals(secondSample, samples.getShort(0))
    }

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
