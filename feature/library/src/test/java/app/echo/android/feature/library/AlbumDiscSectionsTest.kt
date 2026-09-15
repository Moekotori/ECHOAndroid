package app.echo.android.feature.library

import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.LibrarySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumDiscSectionsTest {
    @Test
    fun singleDiscAndMissingDiscStayFlat() {
        assertFalse(albumHasMultipleDiscs(listOf(track(1), track(1), track(null))))
        assertFalse(albumHasMultipleDiscs(listOf(track(null), track(null))))
        assertFalse(albumHasMultipleDiscs(listOf(track(2), track(2))))
        assertFalse(albumHasMultipleDiscs(emptyList()))
    }

    @Test
    fun mixedDiscNumbersEnableHeaders() {
        assertTrue(albumHasMultipleDiscs(listOf(track(1), track(2))))
        assertTrue(albumHasMultipleDiscs(listOf(track(null), track(2))))
        assertTrue(albumHasMultipleDiscs(listOf(track(2), track(3))))
    }

    @Test
    fun headersAppearOnFirstRowAndDiscChanges() {
        val first = track(1)
        val second = track(1)
        val third = track(2)
        assertTrue(shouldShowAlbumDiscHeader(first, previous = null, isFirst = true, multiDisc = true))
        assertFalse(shouldShowAlbumDiscHeader(second, previous = first, isFirst = false, multiDisc = true))
        assertTrue(shouldShowAlbumDiscHeader(third, previous = second, isFirst = false, multiDisc = true))
        assertFalse(shouldShowAlbumDiscHeader(first, previous = null, isFirst = true, multiDisc = false))
        assertFalse(shouldShowAlbumDiscHeader(third, previous = null, isFirst = false, multiDisc = true))
        assertEquals(1, first.albumDiscNumber())
        assertEquals(1, track(null).albumDiscNumber())
        assertEquals(2, third.albumDiscNumber())
    }

    private fun track(disc: Int?): EchoTrack = EchoTrack(
        id = "track-${disc ?: "none"}-${System.identityHashCode(disc)}",
        uri = "content://track",
        title = "Track",
        artist = "Artist",
        discNumber = disc,
        source = LibrarySource.MediaStore,
    )
}
