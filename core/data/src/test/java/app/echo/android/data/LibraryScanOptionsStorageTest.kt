package app.echo.android.data

import app.echo.android.model.library.LibraryScanOptions
import org.junit.Assert.*
import org.junit.Test

class LibraryScanOptionsStorageTest {
    @Test fun persistedRulesSurviveReloadAndCanBeRemoved() {
        val original = LibraryScanOptions(60_000, 1_048_576, false, false,
            setOf("Recordings/Call", "Music/Private"))
        val restored = decodeLibraryScanOptions(encodeLibraryScanOptions(original))
        assertEquals(original, restored)
        assertFalse(restored.accepts(120_000, 2_000_000, "Recordings/Call/2026/"))
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
        assertFalse(options.includesDirectory("Music/Private/Calls/"))
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
