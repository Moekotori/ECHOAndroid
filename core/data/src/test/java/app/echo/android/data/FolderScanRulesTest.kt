package app.echo.android.data

import app.echo.android.model.library.LibraryFolderScanRules
import app.echo.android.model.library.LibraryScanOptions
import app.echo.android.model.library.LibraryScanSkipReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderScanRulesTest {
    @Test
    fun ignoredNamesAndPathsStayScopedToTheirFolder() {
        val folder = WatchedLibraryTree(
            "content://music", "primary:Music",
            rules = LibraryFolderScanRules(
                watch = false,
                importITunes = true,
                ignoredPaths = "\\tmp\\; @eaDir\nExtras\\Demos; .pmbmf; \\.; ../invalid",
            ),
        )
        val restored = parseWatchedLibraryTrees(formatWatchedLibraryTrees(listOf(folder)))
        assertEquals(listOf(folder), restored)
        val options = LibraryScanOptions(excludeHiddenFolders = false).withFolderIgnoredPaths(restored)
        assertFalse(options.includesDirectory("Music/Albums/TMP/Live/"))
        assertFalse(options.includesDirectory("Music/Extras/Demos/"))
        assertFalse(options.includesDirectory("Music/Albums/@eaDir/"))
        assertFalse(options.includesDirectory("Music/.metadata/"))
        assertTrue(options.includesDirectory("Music/Archive/Extras/Demos/"))
        assertTrue(options.includesDirectory("MusicBackup/tmp/"))
        assertTrue(options.includesDirectory("Music/Albums/tmp-live/"))
        assertEquals(LibraryScanSkipReason.Name, options.explicitRejection("Music/Albums/", ".pmbmf"))
        assertNull(options.explicitRejection("Other/Albums/", ".pmbmf"))
    }

    @Test
    fun blockedFormatsOverrideTheAllowlistAndAreRetainedByQuietCleanup() {
        val options = LibraryScanOptions(allowedExtensions = setOf("wave"), blockedExtensions = setOf("wav"))
        assertFalse(options.acceptsFileFormat("Song.WAVE", alreadyImported = true))
        assertEquals(LibraryScanSkipReason.Format, options.explicitRejection("Music/", "Song.WAVE"))
        assertEquals(LibraryScanSkipReason.Format, options.explicitRejection("Music/", "Song.bwf"))
        assertFalse(LibraryScanPolicy.isDirectoryCleanupCandidate("Music/", options, false, "Song.WAVE"))
        assertTrue(LibraryScanPolicy.isDirectoryCleanupCandidate("Music/", options, true, "Song.WAVE"))
    }

    @Test
    fun parentAndChildCanKeepIndependentWatchOptions() {
        val parent = WatchedLibraryTree("content://parent", "primary:Music", rules = LibraryFolderScanRules(watch = false))
        val child = WatchedLibraryTree("content://child", "primary:Music/Live")
        val folders = LibraryFolderWatchPolicy.remember(listOf(parent), child, keepNested = true)
        assertEquals(listOf(parent, child), folders)
        assertEquals(listOf(child), LibraryFolderWatchPolicy.treesDueForAutoScan(folders, 60_000, false, false, true))
        val mediaStore = LibraryScanOptions().skipManagedFolders(folders)
        assertFalse(mediaStore.includesDirectory("Music/Other/"))
        assertTrue(mediaStore.includesDirectory("MusicBackup/"))
    }
}
