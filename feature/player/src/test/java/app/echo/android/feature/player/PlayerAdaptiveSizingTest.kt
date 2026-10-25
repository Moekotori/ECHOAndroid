package app.echo.android.feature.player

import org.junit.Assert.*
import org.junit.Test

class PlayerAdaptiveSizingTest {
    @Test fun shortLandscapeKeepsBothPanesReadableAndLargeTextFallsBack() {
        fun lyrics(width: Float, height: Float, scale: Float) = playerSidePaneWidth(
            width, height, scale, 264f, 216f * scale, 20f, .45f)
        assertNotNull(lyrics(720f, 320f, 1f))
        val controls = lyrics(720f, 320f, 1.3f)!!
        assertTrue(controls >= 264f * 1.3f)
        assertTrue(720f - controls - 20f >= 216f * 1.3f)
        assertNull(lyrics(600f, 300f, 1.3f))
        assertNull(lyrics(720f, 320f, 2f))
        assertNull(lyrics(412f, 800f, 1f))
        assertNotNull(lyrics(1000f, 700f, 1f))
        assertNull(lyrics(Float.POSITIVE_INFINITY, 320f, 1f))
    }

    @Test fun wideTransportStylesReserveArtworkSpaceRatherThanOverflowing() {
        val width = playerSidePaneWidth(520f, 280f, 1f, 340f, 144f, 24f, 1.3f / 2.3f)!!
        assertTrue(width >= 340f)
        assertTrue(520f - width - 24f >= 144f)
        assertNull(playerSidePaneWidth(480f, 280f, 1f, 340f, 144f, 24f, 1.3f / 2.3f))
    }

    @Test fun dialogBodyShrinksWithWindowInsetsAndFontSize() {
        val landscape = playerDialogSizing(360f, 48f, 1f)
        assertEquals(280f, landscape.heightDp, 0f)
        assertEquals(120f, landscape.bodyHeightDp, 0f)
        val largeText = playerDialogSizing(360f, 48f, 2f)
        assertTrue(largeText.bodyHeightDp > 0)
        assertTrue(largeText.bodyHeightDp < landscape.bodyHeightDp)
        val portrait = playerDialogSizing(820f, 48f, 1f)
        assertTrue(portrait.bodyHeightDp > landscape.bodyHeightDp)
    }
}
