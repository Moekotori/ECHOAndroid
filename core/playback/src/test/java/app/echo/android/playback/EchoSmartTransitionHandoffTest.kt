package app.echo.android.playback

import org.junit.Assert.*
import org.junit.Test

class EchoSmartTransitionHandoffTest {
    @Test fun automaticHandoffConsumesOnlyFramesActuallyMixed() {
        assertEquals(1500L, position(frames = 24_000))
        assertEquals(2000L, position(frames = 48_000))
        assertNull(position(frames = 0))
    }

    @Test fun manualSkipQueueReplacementAndDuplicateOccurrenceNeverConsumeIntro() {
        assertNull(position(automatic = false))
        assertNull(position(index = 2))
        assertNull(position(id = "replacement"))
    }

    private fun position(automatic: Boolean = true, frames: Int = 48_000, index: Int = 1, id: String = "next") =
        EchoSmartTransitionHandoff.continuationMs(automatic, 1, index, "next", id, 1000, frames, 48_000)
}
