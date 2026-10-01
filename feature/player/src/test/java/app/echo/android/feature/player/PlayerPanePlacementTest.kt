package app.echo.android.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerPanePlacementTest {
    @Test fun verticalHingeUsesWindowOriginAndLeavesBothSidesClear() {
        val panes = playerPanePlacement(1000, 700, 20, PlayerFold(590, 0, 610, 900, false), 100, 80)
        assertEquals(PlayerPaneBounds(0, 0, 480, 700), panes.cover)
        assertEquals(PlayerPaneBounds(520, 0, 480, 700), panes.lyrics)
    }
    @Test fun tabletopKeepsControlsBelowTheFold() {
        val panes = playerPanePlacement(700, 800, 20, PlayerFold(0, 450, 700, 470, true), 0, 100)
        assertEquals(PlayerPaneBounds(0, 380, 700, 420), panes.cover)
        assertEquals(PlayerPaneBounds(0, 0, 700, 340), panes.lyrics)
    }
    @Test fun zeroWidthFoldAndResizedWindowsHaveBoundedPanes() {
        val panes = playerPanePlacement(700, 500, 20, PlayerFold(350, 0, 350, 500, false))
        assertEquals(340, panes.cover.width)
        assertEquals(360, panes.lyrics!!.x)
        val resized = playerPanePlacement(900, 600, 18, PlayerFold(1200, 0, 1200, 900, false))
        assertTrue(resized.cover.width > 0 && resized.lyrics!!.width > 0)
        assertEquals(900, resized.lyrics!!.x + resized.lyrics.width)
    }

    @Test fun insufficientPaneKeepsPagerOnTheLargerSideIncludingPartlyClippedHinges() {
        val small = playerPanePlacement(700, 500, 20, PlayerFold(180, 0, 200, 500, false),
            minimumPaneWidth = 280, minimumPaneHeight = 180)
        assertTrue(!small.isSplit)
        assertEquals(PlayerPaneBounds(210, 0, 490, 500), small.cover)
        val clipped = playerPanePlacement(700, 500, 20, PlayerFold(-20, 0, 20, 500, false))
        assertTrue(!clipped.isSplit)
        assertEquals(PlayerPaneBounds(30, 0, 670, 500), clipped.cover)
    }

    @Test fun rtlChangesPhysicalPanePlacementWithoutCrossingAHinge() {
        val panes = playerPanePlacement(700, 500, 20, PlayerFold(340, 0, 360, 500, false), rtl = true)
        assertEquals(PlayerPaneBounds(370, 0, 330, 500), panes.cover)
        assertEquals(PlayerPaneBounds(0, 0, 330, 500), panes.lyrics)
    }
}
