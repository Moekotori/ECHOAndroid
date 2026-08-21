package app.echo.android.usbaudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsbAudioFeedbackTest {
    @Test
    fun highSpeedSixteenSixteenDecodesNominalFortyEightK() {
        val nominal = UsbAudioFeedback.nominalQ16(48_000, 8_000)
        assertEquals(6L shl 16, nominal)
        val payload = byteArrayOf(
            (nominal and 0xff).toByte(),
            ((nominal shr 8) and 0xff).toByte(),
            ((nominal shr 16) and 0xff).toByte(),
            ((nominal shr 24) and 0xff).toByte(),
        )
        assertEquals(nominal, UsbAudioFeedback.decodeQ16(payload, highSpeed = true))
    }

    @Test
    fun fullSpeedTenFourteenShiftsToSixteenSixteen() {
        val samplesPerFrame = 48
        val tenFourteen = samplesPerFrame shl 14
        val payload = byteArrayOf(
            (tenFourteen and 0xff).toByte(),
            ((tenFourteen shr 8) and 0xff).toByte(),
            ((tenFourteen shr 16) and 0xff).toByte(),
        )
        assertEquals((samplesPerFrame.toLong() shl 16), UsbAudioFeedback.decodeQ16(payload, highSpeed = false))
    }

    @Test
    fun clampRejectsWildFeedbackJumps() {
        val nominal = UsbAudioFeedback.nominalQ16(48_000, 8_000)
        val wild = nominal + (8L shl 16)
        val clamped = UsbAudioFeedback.clampQ16(wild, nominal)
        assertEquals(nominal + (1L shl 16), clamped)
        assertNull(UsbAudioFeedback.decodeQ16(byteArrayOf(1, 2), highSpeed = true))
    }
}
