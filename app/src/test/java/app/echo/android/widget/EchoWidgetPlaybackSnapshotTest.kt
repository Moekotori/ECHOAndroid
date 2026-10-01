package app.echo.android.widget

import app.echo.android.data.EchoSavedPlaybackSession
import app.echo.android.model.playback.EchoTrackRef
import app.echo.android.playback.EchoPlaybackSurfaceSnapshot
import org.junit.Assert.*
import org.junit.Test

class EchoWidgetPlaybackSnapshotTest {
    @Test fun savedMetadataReturnsPausedAndLivePlaybackAlwaysWins() {
        val saved = EchoSavedPlaybackSession(listOf(EchoTrackRef("saved", "file://saved", "Saved", "Artist")), 0, 1234, true)
        val restored = widgetPlaybackSnapshot(EchoPlaybackSurfaceSnapshot(), saved)
        assertEquals("saved", restored.mediaId)
        assertEquals(1234L, restored.positionMs)
        assertTrue(restored.hasTrack)
        assertFalse(restored.isPlaying)
        val live = restored.copy(mediaId = "live", isPlaying = true)
        assertSame(live, widgetPlaybackSnapshot(live, saved))
        val empty = EchoPlaybackSurfaceSnapshot()
        assertSame(empty, widgetPlaybackSnapshot(empty, saved.copy(currentIndex = 99)))
    }
}
