package app.echo.android.usbaudio

import java.util.Collections
import kotlin.math.pow
import org.junit.Assert.*
import org.junit.Test

class UsbDacVolumeSafetyTest {
    private class Dac {
        val baseline = -5120
        @Volatile var db = baseline
        @Volatile var muted = false
        @Volatile var corruptGain = false
        val writes = Collections.synchronizedList(mutableListOf<Int>())
        var onVolumeWrite: (() -> Unit)? = null
        var onVolumeRead: (() -> Unit)? = null
        val transfer = UsbAudioControlTransfer { type, request, value, _, payload, length, _ ->
            val mute = value == 0x100
            if (type == 0x21) {
                if (mute) muted = payload[0].toInt() != 0
                else {
                    assertTrue("hardware gain must only change under verified mute", muted)
                    val wanted = ((payload[0].toInt() and 255) or (payload[1].toInt() shl 8)).toShort().toInt()
                    writes += wanted
                    db = if (corruptGain) 0 else wanted
                    onVolumeWrite?.invoke()
                }
            } else {
                if (!mute && request == 0x81) onVolumeRead?.invoke()
                val answer = if (mute) { if (muted) 1 else 0 } else when (request) {
                    0x81 -> db; 0x82 -> -15360; 0x83 -> 0; 0x84 -> 256; else -> error("request")
                }
                payload[0] = answer.toByte()
                if (length > 1) payload[1] = (answer shr 8).toByte()
            }
            length
        }
        fun controller(gain: Float = .5f) = UsbDacVolumeController(raw, format, transfer, 100, "hardware", 48000, 150, gain)
        fun combined(digital: Float): Float = digital * 10.0.pow((db - baseline) / (256.0 * 20.0)).toFloat()
    }

    @Test fun quieterRequestDuringDrainSupersedesThePendingLouderHandoff() {
        val dac = Dac(); val controller = dac.controller()
        try {
            controller.setPlaying(true)
            await { controller.state.handoff }
            controller.requestGain("software", .01f)
            await { controller.state.effective == "software" && controller.state.checked && !controller.state.handoff }
            assertTrue(dac.writes.isEmpty())
            assertTrue(dac.combined(controller.ramp.target) <= .01001f)
        } finally { controller.close() }
    }

    @Test fun newerQuieterRequestDuringTheGainWriteCapsTheUnmutedOutput() {
        val dac = Dac(); val controller = dac.controller()
        val observed = Collections.synchronizedList(mutableListOf<Float>())
        val sent = java.util.concurrent.atomic.AtomicBoolean(false)
        dac.onVolumeWrite = {
            if (sent.compareAndSet(false, true)) { controller.requestGain("software", .01f) }
        }
        dac.onVolumeRead = {
            if (sent.get() && !dac.muted) observed += dac.combined(controller.ramp.target)
        }
        try {
            controller.setPlaying(true)
            await { sent.get() && controller.state.effective == "software" && !controller.state.handoff }
            assertTrue(observed.isNotEmpty())
            assertTrue(observed.all { it <= .01001f })
            assertTrue(dac.combined(controller.ramp.target) <= .01001f)
        } finally { controller.close() }
    }

    @Test fun unexpectedHardwareGainFailsClosedWithoutUnmuting() {
        val dac = Dac().apply { corruptGain = true }; val controller = dac.controller()
        try {
            controller.setPlaying(true)
            await { controller.fatal }
            assertTrue(dac.muted)
            assertEquals(0f, controller.ramp.target, 0f)
            assertEquals("muted", controller.state.effective)
        } finally { controller.close() }
        assertTrue(dac.muted)
    }

    @Test fun closingNeverUndoesAnExternalReductionOrRestoresAnUnstoppedStream() {
        val external = Dac(); val first = external.controller()
        first.setPlaying(true); await { first.state.effective == "hardware" }
        external.db = -14080; external.muted = true
        val writes = external.writes.size
        first.close()
        assertEquals(-14080, external.db)
        assertTrue(external.muted)
        assertEquals(writes, external.writes.size)

        val unstopped = Dac(); val second = unstopped.controller()
        second.setPlaying(true); await { second.state.effective == "hardware" }
        val quiet = unstopped.db
        second.close(restoreHardware = false)
        assertEquals(quiet, unstopped.db)
        assertEquals("restore_failed", second.state.reason)
    }

    private fun await(condition: () -> Boolean) {
        val end = System.nanoTime() + 2_000_000_000L
        while (!condition() && System.nanoTime() < end) Thread.sleep(2)
        assertTrue("control worker did not reach the expected state", condition())
    }
    private companion object {
        val format = UsbAudioStreamingFormat(1, 1, UsbAudioClassVersion.Uac1, acInterfaceNumber = 0, terminalLink = 1)
        fun bytes(vararg values: Int) = values.map(Int::toByte).toByteArray()
        val raw = bytes(9,4,0,0,0,1,1,0,0) + bytes(12,0x24,2,1,1,1,0,2,3,0,0,0) +
            bytes(10,0x24,6,2,1,1,3,0,0,0) + bytes(9,0x24,3,3,1,3,0,2,0)
    }
}
