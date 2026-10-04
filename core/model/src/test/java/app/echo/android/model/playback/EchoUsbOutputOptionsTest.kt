package app.echo.android.model.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoUsbOutputOptionsTest {
    @Test fun invalidPersistedValuesReturnToBoundedDefaults() {
        val state = EchoUsbOutputOptions(sampleRateHz = -1, bitDepth = 8, channelCount = 8, volumePercent = 400).normalized
        assertEquals(EchoUsbOutputOptions(), state)
        assertEquals(EchoUsbBufferMode.Balanced, EchoUsbBufferMode.fromId("unknown"))
        assertEquals(listOf(8, 16, 32), EchoUsbBufferMode.entries.map { it.transferCount })
    }
    @Test fun strictModeKeepsFormatPreferencesWithoutRebuildingItsAudioPath() {
        val previous = EchoUsbOutputOptions()
        assertFalse(previous.copy(sampleRateHz = 96000, bitDepth = 24, channelCount = 1,
            volumePercent = 40).needsRebuild(previous, strict = true))
        assertTrue(previous.copy(sampleRateHz = 96000).needsRebuild(previous, strict = false))
        assertFalse(previous.copy(volumePercent = 40).needsRebuild(previous, strict = false))
        assertTrue(previous.copy(bufferMode = EchoUsbBufferMode.Stable).needsRebuild(previous, strict = true))
    }
}
