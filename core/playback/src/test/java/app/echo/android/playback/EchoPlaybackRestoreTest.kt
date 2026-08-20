package app.echo.android.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoPlaybackRestoreTest {
    @Test
    fun queueReplacingPlaySkipsRestore() {
        assertTrue(shouldSkipSavedSessionRestore(listOf(true)))
        assertTrue(shouldSkipSavedSessionRestore(listOf(false, true)))
    }

    @Test
    fun playPauseAndSkipDoNotSkipRestore() {
        assertFalse(shouldSkipSavedSessionRestore(emptyList()))
        assertFalse(shouldSkipSavedSessionRestore(listOf(false)))
        assertFalse(shouldSkipSavedSessionRestore(listOf(false, false)))
    }
}
