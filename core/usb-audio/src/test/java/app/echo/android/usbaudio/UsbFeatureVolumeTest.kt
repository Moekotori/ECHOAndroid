package app.echo.android.usbaudio

import org.junit.Assert.*
import org.junit.Test

class UsbFeatureVolumeTest {
    private val format = UsbAudioStreamingFormat(1, 1, UsbAudioClassVersion.Uac1, acInterfaceNumber = 0, terminalLink = 1)
    private fun bytes(vararg values: Int) = values.map(Int::toByte).toByteArray()
    @Test fun selectsPlaybackMasterWithoutTouchingCaptureOrAmbiguousMixers() {
        val raw = bytes(9,4,0,0,0,1,1,0,0) +
            bytes(12,0x24,2,1,1,1,0,2,3,0,0,0) +
            bytes(10,0x24,6,2,1,1,3,0,0,0) + bytes(9,0x24,3,3,1,3,0,2,0) +
            bytes(12,0x24,2,4,1,2,0,2,3,0,0,0) +
            bytes(10,0x24,6,5,4,1,3,0,0,0) + bytes(9,0x24,3,6,1,1,0,5,0)
        assertEquals(UsbVolumeUnit(2,0,false,true), UsbVolumeTopology.find(raw, format))
        assertNull(UsbVolumeTopology.find(raw, format.copy(terminalLink = 4)))
        assertNull(UsbVolumeTopology.find(raw.copyOf(raw.size - 1), format))
        assertNull(UsbVolumeTopology.find(raw, format.copy(audioClassVersion = UsbAudioClassVersion.Uac3)))
    }
    @Test fun signedDbControlsUseEntityAddressAndRejectAcknowledgedNoOp() {
        var current = -5120
        var noOp = false
        val control = UsbFeatureVolumeControl(UsbAudioControlTransfer { type, request, value, index, payload, length, _ ->
            assertEquals(0x0200, index)
            assertEquals(0x0200, value)
            if (type == 0x21) {
                assertEquals(1, request)
                if (!noOp) current = ((payload[0].toInt() and 255) or (payload[1].toInt() shl 8)).toShort().toInt()
            } else {
                val answer = when (request) { 0x81 -> current; 0x82 -> -15360; 0x83 -> 0; 0x84 -> 256; else -> error("request") }
                payload[0] = answer.toByte(); payload[1] = (answer shr 8).toByte()
            }
            length
        }, UsbVolumeUnit(2,0,false,true))
        assertEquals(UsbVolumeRange(-15360,0,256), control.range())
        assertTrue(control.writeVolume(-10240))
        noOp = true
        assertFalse(control.writeVolume(-5120))
    }
    @Test fun hardwareHandoffMutesBeforeChangingAndRestoresBorrowedSettings() {
        val raw = bytes(9,4,0,0,0,1,1,0,0) + bytes(12,0x24,2,1,1,1,0,2,3,0,0,0) +
            bytes(10,0x24,6,2,1,1,3,0,0,0) + bytes(9,0x24,3,3,1,3,0,2,0)
        var currentDb = -5120
        var muted = false
        val writes = java.util.Collections.synchronizedList(mutableListOf<Int>())
        val transfer = UsbAudioControlTransfer { type, request, value, _, payload, length, _ ->
            val mute = value == 0x100
            if (type == 0x21) {
                if (mute) muted = payload[0].toInt() != 0
                else {
                    assertTrue("Volume changes must happen while DAC mute is verified", muted)
                    currentDb = ((payload[0].toInt() and 255) or (payload[1].toInt() shl 8)).toShort().toInt()
                    assertTrue(currentDb <= -5120)
                    writes += currentDb
                }
            } else {
                val answer = if (mute) { if (muted) 1 else 0 } else when (request) {
                    0x81 -> currentDb; 0x82 -> -15360; 0x83 -> 0; 0x84 -> 256; else -> error("request") }
                payload[0] = answer.toByte()
                if (length > 1) payload[1] = (answer shr 8).toByte()
            }
            length
        }
        val controller = UsbDacVolumeController(raw, format, transfer, 50, "hardware", 48000, 150)
        try {
            controller.setPlaying(true)
            val deadline = System.nanoTime() + 2_000_000_000L
            while (controller.state.effective != "hardware" && System.nanoTime() < deadline) Thread.sleep(10)
            assertEquals("hardware", controller.state.effective)
            assertTrue(controller.state.readbackVerified)
            assertTrue(currentDb < -5120)
            assertFalse(muted)
            assertTrue(controller.ramp.target in 0f..1f)
        } finally { controller.close() }
        assertEquals(-5120, currentDb)
        assertFalse(muted)
        assertEquals(-5120, writes.last())
    }

    @Test fun pcmRampIsBoundedMonotonicAndReachesTarget() {
        val ramp = UsbPcmGainRamp(1f, 48000)
        ramp.target = 0f
        var previous = 1f
        repeat(1600) { val gain = ramp.next(); assertTrue(gain in 0f..previous); previous = gain }
        assertEquals(0f, previous, 0f)
        ramp.target = 1f
        repeat(1600) { val gain = ramp.next(); assertTrue(gain in previous..1f); previous = gain }
        assertTrue(ramp.unity())
        ramp.resetWhileMuted()
        ramp.target = .5f
        assertTrue(ramp.next() < .01f)
    }
}
