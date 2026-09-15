package app.echo.android.widget

import app.echo.android.playback.EchoPlaybackSurfaceSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoPlaybackWidgetLayoutTest {
    @Test
    fun threeByOneStaysCompact() {
        assertFalse(
            EchoPlaybackWidgetLayout.isExpanded(
                EchoPlaybackWidgetLayout.CompactWidthDp,
                EchoPlaybackWidgetLayout.CompactHeightDp,
            ),
        )
        assertFalse(EchoPlaybackWidgetLayout.isExpanded(250, 56))
        assertFalse(EchoPlaybackWidgetLayout.isExpanded(180, 110))
    }

    @Test
    fun fourByTwoUsesExpanded() {
        assertTrue(
            EchoPlaybackWidgetLayout.isExpanded(
                EchoPlaybackWidgetLayout.ExpandedWidthDp,
                EchoPlaybackWidgetLayout.ExpandedHeightDp,
            ),
        )
        assertTrue(EchoPlaybackWidgetLayout.isExpanded(280, 140))
    }

    @Test
    fun chromeIgnoresPlaybackPosition() {
        val playing = EchoPlaybackSurfaceSnapshot(
            title = "Song",
            artist = "Artist",
            isPlaying = true,
            hasTrack = true,
            mediaId = "t1",
            artworkUri = "art",
            playUri = "file://a",
            positionMs = 1_000L,
        )
        val later = playing.copy(positionMs = 8_000L)
        assertEquals(
            EchoPlaybackWidgetLayout.chrome(playing, "line"),
            EchoPlaybackWidgetLayout.chrome(later, "line"),
        )
        assertNotEquals(
            EchoPlaybackWidgetLayout.chrome(playing, "line"),
            EchoPlaybackWidgetLayout.chrome(playing, "next"),
        )
    }
}
