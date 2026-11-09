package app.echo.android.data

import app.echo.android.model.library.LibraryScanOptions
import org.junit.Assert.*
import org.junit.Test

class LibraryScanOptionsStorageTest {
    @Test fun formatSelectionRoundTripsAndNormalizes() {
        val options = LibraryScanOptions(allowedExtensions = setOf(" FLAC ", ".WAV", "flac", ""))
        val restored = decodeLibraryScanOptions(encodeLibraryScanOptions(options))
        assertEquals(setOf("flac", "wav"), restored.allowedExtensions)
        assertTrue(restored.acceptsFileFormat("song.WAV", false))
        assertFalse(restored.acceptsFileFormat("song.mp3", false))
        assertTrue(decodeLibraryScanOptions(encodeLibraryScanOptions(
            restored.copy(allowedExtensions = emptySet()),
        )).allowedExtensions.isEmpty())
    }


    @Test fun persistedRulesSurviveReloadAndCanBeRemoved() {
        val original = LibraryScanOptions(
            minDurationMs = 60_000,
            minSizeBytes = 1_048_576,
            excludeNonMusicFolders = false,
            excludeHiddenFolders = false,
            excludedRelativePaths = setOf("Recordings/Call", "Music/Private"),
            blockedExtensions = setOf("mp3", "wav"),
            excludedNameKeywords = setOf("试听"),
        )
        val restored = decodeLibraryScanOptions(encodeLibraryScanOptions(original))
        assertEquals(original, restored)
        assertFalse(restored.accepts(120_000, 2_000_000, "Recordings/Call/2026/"))
        assertFalse(restored.acceptsFileFormat("Song.WAVE", alreadyImported = true))
        assertFalse(restored.includesFileName("歌曲-试听.flac"))
        val cleared = decodeLibraryScanOptions(encodeLibraryScanOptions(restored.copy(excludedRelativePaths = emptySet())))
        assertTrue(cleared.accepts(120_000, 2_000_000, "Recordings/Call/2026/"))
    }

    @Test fun normalizationRejectsRootAndTraversalAndPreservesFolderBoundaries() {
        val options = decodeLibraryScanOptions(encodeLibraryScanOptions(LibraryScanOptions(
            excludeNonMusicFolders = false,
            excludedRelativePaths = setOf(" /Music//Private/ ", "Music\\Private", "/", "../Music"),
        )))
        assertEquals(setOf("Music/Private"), options.excludedRelativePaths)
        assertFalse(options.includesDirectory("Music/Private"))
        assertFalse(options.includesDirectory("music/private/Calls/"))
        assertTrue(options.includesDirectory("Music/Private Live/"))
        assertTrue(options.includesDirectory("Other/Music/Private/"))
        assertTrue(options.includesDirectory(null))
    }

    @Test fun absentOrInvalidStoredOptionsUseDefaults() {
        assertEquals(LibraryScanOptions(), decodeLibraryScanOptions(null))
        assertEquals(LibraryScanOptions(), decodeLibraryScanOptions("broken json"))
        assertEquals(LibraryScanOptions(), decodeLibraryScanOptions("{}"))
    }

    @Test fun explicitExclusionAppliesWithOtherFiltersDisabled() {
        val options = LibraryScanOptions(0, 0, false, false, setOf("Sounds/Calls"))
        assertFalse(options.accepts(0, 0, "Sounds/Calls/"))
        assertTrue(options.accepts(0, 0, "Sounds/Music/"))
    }
}
