package app.echo.android.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoAdaptiveLayoutTest {
    @Test fun navigationUsesWindowSpaceInPortraitLandscapeAndSplitScreen() {
        assertEquals(0, EchoLayoutInfo(412f, 820f).navigationRailWidthDp)
        assertEquals(56, EchoLayoutInfo(740f, 360f).navigationRailWidthDp)
        assertEquals(56, EchoLayoutInfo(520f, 300f).navigationRailWidthDp)
        assertEquals(80, EchoLayoutInfo(800f, 1280f).navigationRailWidthDp)
        assertEquals(80, EchoLayoutInfo(1280f, 800f).navigationRailWidthDp)
        assertEquals(0, EchoLayoutInfo(480f, 800f).navigationRailWidthDp)
    }

    @Test fun twoPanesNeedBothReadableWidthAndUsableHeight() {
        assertFalse(EchoLayoutInfo(839f, 800f).supportsTwoPanes())
        assertTrue(EchoLayoutInfo(840f, 800f).supportsTwoPanes())
        assertFalse(EchoLayoutInfo(960f, 280f).supportsTwoPanes())
        assertFalse(EchoLayoutInfo(960f, 800f, 1.3f).supportsTwoPanes())
        assertTrue(EchoLayoutInfo(1200f, 800f, 1.3f).supportsTwoPanes())
        assertTrue(EchoLayoutInfo(760f, 1000f).supportsTwoPanes(760f))
    }
}
