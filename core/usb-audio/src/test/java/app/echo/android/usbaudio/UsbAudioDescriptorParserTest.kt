package app.echo.android.usbaudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbAudioDescriptorParserTest {
    private val parser = UsbAudioDescriptorParser()

    @Test
    fun parsesUac1PcmIsochronousOutFormat() {
        val raw = bytes(
            9, 4, 1, 0, 0, 1, 2, 0, 0,
            9, 4, 1, 1, 1, 1, 2, 0, 0,
            7, 36, 1, 1, 1, 1, 0,
            14, 36, 2, 1, 2, 3, 24, 2, 0x44, 0xac, 0x00, 0x80, 0xbb, 0x00,
            9, 5, 0x01, 0x05, 0x00, 0x02, 1, 0, 0,
        )

        val info = parser.parse(raw)

        assertEquals(1, info.audioStreamingInterfaceCount)
        assertTrue(info.classVersions.contains(UsbAudioClassVersion.Uac1))
        assertTrue(info.hasIsochronousOut)
        assertEquals(listOf(44100, 48000), info.sampleRates)
        assertEquals(24, info.streamingFormats.single().bitResolution)
        assertEquals(true, info.streamingFormats.single().pcmIntegerSupported)
    }

    @Test
    fun parsesUac1ContinuousSampleRateRange() {
        val raw = bytes(
            9, 4, 1, 1, 1, 1, 2, 0, 0,
            14, 36, 2, 1, 2, 2, 16, 0, 0x40, 0x1f, 0x00, 0x00, 0x77, 0x01,
            9, 5, 0x01, 0x05, 0x00, 0x02, 1, 0, 0,
        )

        val format = parser.parse(raw).streamingFormats.single()

        assertTrue(format.sampleRates.isEmpty())
        assertEquals(8_000, format.sampleRateMinHz)
        assertEquals(96_000, format.sampleRateMaxHz)
    }

    @Test
    fun attachesFeedbackFromUac1SynchAddressWithoutClobberingOutEndpoint() {
        val raw = bytes(
            9, 4, 1, 1, 2, 1, 2, 0, 0,
            11, 36, 2, 1, 2, 2, 16, 1, 0x44, 0xac, 0x00,
            9, 5, 0x01, 0x05, 0xc0, 0x00, 1, 0, 0x81,
            9, 5, 0x81, 0x01, 0x04, 0x00, 1, 0, 0,
        )

        val info = parser.parse(raw)
        val format = info.streamingFormats.single()

        assertTrue(info.hasIsochronousOut)
        assertTrue(info.hasFeedbackEndpoint)
        assertEquals(0x01, format.endpointAddress)
        assertEquals(UsbEndpointDirection.Out, format.endpointDirection)
        assertEquals(0x81, format.feedbackEndpointAddress)
        assertEquals(0x81, format.synchAddress)
    }

    @Test
    fun bindsUac2ClockFromAsGeneralTerminalLink() {
        val raw = bytes(
            9, 4, 0, 0, 0, 1, 1, 0x20, 0,
            8, 36, 0x0A, 5, 1, 0, 0, 0,
            12, 36, 2, 1, 0x01, 0x01, 0, 5, 0, 0, 0, 0,
            9, 4, 2, 1, 2, 1, 2, 0x20, 0,
            16, 36, 1, 1, 0, 1, 1, 0, 0, 0, 2, 0, 0, 0, 0, 0,
            6, 36, 2, 1, 4, 32,
            9, 5, 0x02, 0x05, 0x00, 0x04, 1, 0, 0,
            9, 5, 0x83, 0x15, 0x03, 0x00, 1, 0, 0,
        )

        val format = parser.parse(raw).streamingFormats.single()

        assertEquals(1, format.terminalLink)
        assertEquals(listOf(5), format.clockSourceIds)
        assertEquals(true, format.pcmIntegerSupported)
        assertEquals(32, format.bitResolution)
        assertEquals(2, format.channelCount)
    }

    @Test
    fun parsesUac2FormatAndFeedbackEndpoint() {
        val raw = bytes(
            9, 4, 2, 1, 2, 1, 2, 0x20, 0,
            6, 36, 2, 1, 4, 32,
            9, 5, 0x02, 0x05, 0x00, 0x04, 1, 0, 0,
            9, 5, 0x83, 0x15, 0x03, 0x00, 1, 0, 0,
        )

        val info = parser.parse(raw)

        assertTrue(info.classVersions.contains(UsbAudioClassVersion.Uac2))
        assertTrue(info.hasIsochronousOut)
        assertTrue(info.hasFeedbackEndpoint)
        assertEquals(32, info.streamingFormats.first().bitResolution)
        assertEquals(0x83, info.streamingFormats.first().feedbackEndpointAddress)
    }

    @Test fun retainsSelectorTopologyForTheStreamingTerminal() {
        val raw = bytes(
            9, 4, 3, 0, 0, 1, 1, 0x20, 0,
            8, 36, 0x0A, 0x11, 1, 3, 0, 0,
            8, 36, 0x0A, 0x12, 1, 3, 0, 0,
            9, 36, 0x0B, 0x20, 2, 0x11, 0x12, 3, 0,
            12, 36, 2, 1, 1, 1, 0, 0x20, 0, 0, 0, 0,
            9, 4, 2, 1, 1, 1, 2, 0x20, 0,
            16, 36, 1, 1, 0, 1, 1, 0, 0, 0, 2, 0, 0, 0, 0, 0,
            6, 36, 2, 1, 3, 24,
            9, 5, 2, 5, 0, 4, 1, 0, 0,
        )
        val format = parser.parse(raw).streamingFormats.single()
        assertEquals(3, format.acInterfaceNumber)
        assertEquals(listOf(0x20), format.clockSourceIds)
        assertEquals(mapOf(0x20 to listOf(0x11, 0x12)), format.clockSelectorSources)
    }

    private fun bytes(vararg values: Int): ByteArray =
        values.map { it.toByte() }.toByteArray()
}
