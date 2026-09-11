package app.echo.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFolderWatchPolicyTest {
    @Test
    fun documentIdCoversNestedPathsAndVolumeRoots() {
        assertTrue(LibraryFolderWatchPolicy.documentIdCovers("primary:Music", "primary:Music"))
        assertTrue(LibraryFolderWatchPolicy.documentIdCovers("primary:Music", "primary:Music/Album"))
        assertFalse(LibraryFolderWatchPolicy.documentIdCovers("primary:Music", "primary:MusicVideos"))
        assertTrue(LibraryFolderWatchPolicy.documentIdCovers("1D0C-1A0E:", "1D0C-1A0E:Music"))
        assertTrue(LibraryFolderWatchPolicy.documentIdCovers("1D0C-1A0E:Music", "1d0c-1a0e:Music/Live"))
        assertFalse(LibraryFolderWatchPolicy.documentIdCovers("primary:Music", "1D0C-1A0E:Music"))
    }

    @Test
    fun rememberDropsChildrenWhenParentIsAdded() {
        val album = tree("content://tree/album", "primary:Music/Album")
        val music = tree("content://tree/music", "primary:Music", lastScanEpochMs = 50L)
        val remembered = LibraryFolderWatchPolicy.remember(listOf(album), music)
        assertEquals(listOf(music), remembered)
    }

    @Test
    fun rememberKeepsParentWhenChildIsScanned() {
        val music = tree("content://tree/music", "primary:Music", lastScanEpochMs = 10L)
        val album = tree("content://tree/album", "primary:Music/Album", lastScanEpochMs = 99L)
        val remembered = LibraryFolderWatchPolicy.remember(listOf(music), album)
        assertEquals(1, remembered.size)
        assertEquals("content://tree/music", remembered.single().uri)
        assertEquals(99L, remembered.single().lastScanEpochMs)
    }

    @Test
    fun rememberCapsOldestTreesAndKeepsIncoming() {
        val existing = (1..8).map { index ->
            tree("content://tree/$index", "primary:Folder$index", lastScanEpochMs = index.toLong())
        }
        val incoming = tree("content://tree/new", "primary:New", lastScanEpochMs = 100L)
        val remembered = LibraryFolderWatchPolicy.remember(existing, incoming, maxTrees = 8)
        assertEquals(8, remembered.size)
        assertTrue(remembered.any { it.uri == "content://tree/new" })
        assertFalse(remembered.any { it.uri == "content://tree/1" })
    }

    @Test
    fun pruneRevokedDropsTreesWithoutPersistedRead() {
        val kept = tree("content://tree/keep", "primary:Music")
        val gone = tree("content://tree/gone", "primary:Download")
        val pruned = LibraryFolderWatchPolicy.pruneRevoked(
            trees = listOf(kept, gone),
            grantedUris = setOf("content://tree/keep"),
        )
        assertEquals(listOf(kept), pruned)
    }

    @Test
    fun pruneRevokedMatchesPercentEncodedTreeUris() {
        val kept = tree(
            "content://com.android.externalstorage.documents/tree/primary%3AMusic",
            "primary:Music",
        )
        val pruned = LibraryFolderWatchPolicy.pruneRevoked(
            trees = listOf(kept),
            grantedUris = setOf("content://com.android.externalstorage.documents/tree/primary:Music"),
        )
        assertEquals(listOf(kept), pruned)
    }

    @Test
    fun autoScanSkipsBusyStorageAndRecentTrees() {
        val stale = tree("content://tree/a", "primary:A", lastScanEpochMs = 1_000L)
        val fresh = tree("content://tree/b", "primary:B", lastScanEpochMs = 90_000L)
        assertTrue(
            LibraryFolderWatchPolicy.treesDueForAutoScan(
                trees = listOf(stale, fresh),
                nowEpochMs = 100_000L,
                storageBusy = true,
                lightweight = false,
                enabled = true,
            ).isEmpty(),
        )
        val due = LibraryFolderWatchPolicy.treesDueForAutoScan(
            trees = listOf(stale, fresh),
            nowEpochMs = 100_000L,
            storageBusy = false,
            lightweight = false,
            enabled = true,
        )
        assertEquals(listOf(stale), due)
        assertTrue(
            LibraryFolderWatchPolicy.treesDueForAutoScan(
                trees = listOf(stale),
                nowEpochMs = 100_000L,
                storageBusy = false,
                lightweight = true,
                enabled = true,
            ).isEmpty(),
        )
        assertTrue(
            LibraryFolderWatchPolicy.treesDueForAutoScan(
                trees = listOf(stale),
                nowEpochMs = 100_000L,
                storageBusy = false,
                lightweight = false,
                enabled = false,
            ).isEmpty(),
        )
    }

    @Test
    fun codecRoundTripsChinesePathsAndScanOptions() {
        val original = listOf(
            WatchedLibraryTree(
                uri = "content://com.android.externalstorage.documents/tree/primary%3A音乐",
                documentId = "primary:音乐",
                lastScanEpochMs = 42L,
                minDurationMs = 12_000L,
                minSizeBytes = 2048L,
                excludeNonMusicFolders = false,
                excludeHiddenFolders = true,
            ),
        )
        val restored = parseWatchedLibraryTrees(formatWatchedLibraryTrees(original))
        assertEquals(original, restored)
        assertTrue(parseWatchedLibraryTrees(null).isEmpty())
        assertTrue(parseWatchedLibraryTrees("not-json").isEmpty())
    }

    @Test
    fun scanMadeLibraryChangesIgnoresListingOnlyPasses() {
        assertFalse(LibraryFolderWatchPolicy.scanMadeLibraryChanges(0, 0, 0))
        assertTrue(LibraryFolderWatchPolicy.scanMadeLibraryChanges(1, 0, 0))
        assertTrue(LibraryFolderWatchPolicy.scanMadeLibraryChanges(0, 2, 0))
        assertTrue(LibraryFolderWatchPolicy.scanMadeLibraryChanges(0, 0, 3))
    }

    private fun tree(
        uri: String,
        documentId: String,
        lastScanEpochMs: Long = 0L,
    ) = WatchedLibraryTree(
        uri = uri,
        documentId = documentId,
        lastScanEpochMs = lastScanEpochMs,
    )
}
