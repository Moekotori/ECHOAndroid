package app.echo.android.data

import app.echo.android.model.library.LibraryScanOptions
import org.junit.Assert.*
import org.junit.Test

class LibraryScanFormatFilterTest {
    private val options = LibraryScanOptions(allowedExtensions = setOf("flac", "wav"))

    @Test fun selectedExtensionsMatchActualSuffixIgnoringCase() {
        assertTrue(options.acceptsFileFormat("Song.FLAC", false))
        assertTrue(options.acceptsFileFormat("song.live.wav", false))
        assertFalse(options.acceptsFileFormat("song.flac.mp3", false))
        assertFalse(options.acceptsFileFormat("song.mp3", false))
        assertFalse(options.acceptsFileFormat("flac", false))
        assertFalse(options.acceptsFileFormat(null, false))
    }

    @Test fun changingSelectionAllowsPreviouslySkippedFiles() {
        assertFalse(options.acceptsFileFormat("song.mp3", false))
        assertTrue(options.copy(allowedExtensions = setOf("mp3")).acceptsFileFormat("song.mp3", false))
        assertTrue(options.copy(allowedExtensions = emptySet()).acceptsFileFormat("song.mp3", false))
        assertTrue(LibraryScanOptions().acceptsFileFormat(null, false))
    }

    @Test fun existingSongsRemainEligibleForRefresh() {
        assertTrue(options.acceptsFileFormat("song.mp3", true))
        assertTrue(options.acceptsFileFormat(null, true))
    }

    @Test fun formatSelectionIsIndependentOfOtherFilters() {
        val noOtherFilters = LibraryScanOptions(0, 0, false, false, allowedExtensions = setOf("flac", "wav"))
        assertTrue(noOtherFilters.acceptsFileFormat("song.wav", false))
        assertFalse(noOtherFilters.acceptsFileFormat("song.mp3", false))
    }
}
