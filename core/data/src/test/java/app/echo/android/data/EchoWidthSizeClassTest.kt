package app.echo.android.data

import app.echo.android.model.settings.EchoWidthSizeClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoWidthSizeClassTest {
    @Test
    fun fromWidthDpUsesMaterialBreakpoints() {
        assertEquals(EchoWidthSizeClass.Compact, EchoWidthSizeClass.fromWidthDp(320))
        assertEquals(EchoWidthSizeClass.Compact, EchoWidthSizeClass.fromWidthDp(599))
        assertEquals(EchoWidthSizeClass.Medium, EchoWidthSizeClass.fromWidthDp(600))
        assertEquals(EchoWidthSizeClass.Medium, EchoWidthSizeClass.fromWidthDp(839))
        assertEquals(EchoWidthSizeClass.Expanded, EchoWidthSizeClass.fromWidthDp(840))
        assertEquals(EchoWidthSizeClass.Expanded, EchoWidthSizeClass.fromWidthDp(1280))
    }

    @Test
    fun splitLayoutsOnlyOnExpandedWidth() {
        assertFalse(EchoWidthSizeClass.Compact.prefersLibrarySplit)
        assertFalse(EchoWidthSizeClass.Medium.prefersLibrarySplit)
        assertTrue(EchoWidthSizeClass.Expanded.prefersLibrarySplit)
        assertTrue(EchoWidthSizeClass.Expanded.prefersNowPlayingSplit)
        assertFalse(EchoWidthSizeClass.Medium.prefersNowPlayingSplit)
    }

    @Test
    fun contentMaxWidthGrowsWithSizeClass() {
        assertEquals(560, EchoWidthSizeClass.Compact.contentMaxWidthDp())
        assertEquals(720, EchoWidthSizeClass.Medium.contentMaxWidthDp())
        assertEquals(960, EchoWidthSizeClass.Expanded.contentMaxWidthDp())
    }
}
