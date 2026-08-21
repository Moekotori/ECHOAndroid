package app.echo.android.data

import app.echo.android.model.library.LibraryPlaybackOrigin
import app.echo.android.model.library.LibrarySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryPlaybackQueuePolicyTest {
    @Test
    fun albumArtistFolderPlaylistTapsUseCollectionQueue() {
        assertTrue(LibraryPlaybackQueuePolicy.usesCollectionQueue(LibraryPlaybackOrigin.Album("album-1")))
        assertTrue(LibraryPlaybackQueuePolicy.usesCollectionQueue(LibraryPlaybackOrigin.Artist("artist-1")))
        assertTrue(LibraryPlaybackQueuePolicy.usesCollectionQueue(LibraryPlaybackOrigin.Folder("folder-1")))
        assertTrue(LibraryPlaybackQueuePolicy.usesCollectionQueue(LibraryPlaybackOrigin.Playlist("playlist-1")))
        assertFalse(LibraryPlaybackQueuePolicy.usesCollectionQueue(LibraryPlaybackOrigin.Songs))
    }

    @Test
    fun collectionStartIndexIsTheTappedTrack() {
        val albumIds = listOf("a", "b", "c", "d")
        assertEquals(2, LibraryPlaybackQueuePolicy.startIndex(albumIds, "c"))
        assertEquals(0, LibraryPlaybackQueuePolicy.startIndex(albumIds, "missing"))
        assertEquals("album-1", LibraryPlaybackQueuePolicy.collectionKey(LibraryPlaybackOrigin.Album("album-1")))
    }

    @Test
    fun remoteAnchorIsNotPrependedOntoLocalSongsQueue() {
        val localCandidates = listOf(
            LibraryPlaybackQueueCandidate("local-1", LibrarySource.MediaStore.id),
            LibraryPlaybackQueueCandidate("local-2", LibraryScanPolicy.SafSourceId),
        )
        val merged = LibraryPlaybackQueuePolicy.mergeAnchorIntoQueue(
            anchor = LibraryPlaybackQueueCandidate("webdav-1", LibrarySource.WebDav.id),
            candidates = localCandidates,
            selectedLibrarySource = EchoLibrarySelectedSource.Local,
            limit = 200,
        )
        assertEquals(listOf("webdav-1"), merged.map { it.id })
    }

    @Test
    fun songsListAroundTrackKeepsTheActiveLocalSource() {
        val mixed = listOf(
            LibraryPlaybackQueueCandidate("local-1", LibrarySource.MediaStore.id),
            LibraryPlaybackQueueCandidate("cloud-1", LibrarySource.WebDav.id),
            LibraryPlaybackQueueCandidate("local-2", LibraryScanPolicy.SafSourceId),
        )
        val localQueue = LibraryPlaybackQueuePolicy.mergeAnchorIntoQueue(
            anchor = LibraryPlaybackQueueCandidate("local-2", LibraryScanPolicy.SafSourceId),
            candidates = mixed,
            selectedLibrarySource = EchoLibrarySelectedSource.Local,
            limit = 200,
        )
        assertEquals(listOf("local-1", "local-2"), localQueue.map { it.id })

        val cloudQueue = LibraryPlaybackQueuePolicy.mergeAnchorIntoQueue(
            anchor = LibraryPlaybackQueueCandidate("cloud-1", LibrarySource.WebDav.id),
            candidates = mixed,
            selectedLibrarySource = EchoLibrarySelectedSource.Cloud,
            limit = 200,
        )
        assertEquals(listOf("cloud-1"), cloudQueue.map { it.id })
        assertTrue(LibraryPlaybackQueuePolicy.usesLocalTrackQueue(EchoLibrarySelectedSource.Local))
        assertFalse(LibraryPlaybackQueuePolicy.usesLocalTrackQueue(EchoLibrarySelectedSource.Cloud))
        assertEquals(LibraryScanPolicy.RemoteSourceSql, LibraryPlaybackQueuePolicy.sourceSql(EchoLibrarySelectedSource.Cloud))
    }
}
