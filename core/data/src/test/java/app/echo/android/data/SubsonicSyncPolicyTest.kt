package app.echo.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubsonicSyncPolicyTest {
    @Test
    fun search3TriesEmptyQueryBeforeWildcard() {
        assertEquals(listOf("", "*"), SubsonicSyncPolicy.Search3QueryAttempts)
    }

    private fun album(id: String, name: String, artist: String?, songCount: Int) =
        SubsonicAlbum(id = id, name = name, artist = artist, coverArt = null, year = null, songCount = songCount)

    @Test
    fun fallbackPlanSkipsAlbumsWithMatchingLocalTrackCount() {
        val unchanged = album("a1", "Blue", "Miles", songCount = 3)
        val changed = album("a2", "Red", "Miles", songCount = 4)
        val missing = album("a3", "Green", "Miles", songCount = 2)
        val localIds = mapOf(
            SubsonicSyncPolicy.albumCandidateKey(unchanged) to listOf("t1", "t2", "t3"),
            SubsonicSyncPolicy.albumCandidateKey(changed) to listOf("t4", "t5"),
        )
        val plan = SubsonicSyncPolicy.planAlbumFallbackSync(
            albums = listOf(unchanged, changed, missing),
            localTrackIdsByAlbumKey = localIds,
            refreshSalt = 0L,
        )
        // 只有 1 个未变组,会落进轮换刷新窗口被强刷,所以三张都要拉取
        assertEquals(3, plan.albumsToFetch.size)
        assertEquals(0, plan.skippedAlbumCount)
    }

    @Test
    fun fallbackPlanSkipsGroupsOutsideRefreshWindow() {
        val albums = (0 until SubsonicSyncPolicy.UnchangedAlbumRefreshBudget + 10).map { index ->
            album("a$index", "Album $index", "Artist", songCount = 1)
        }
        val localIds = albums.associate { one ->
            SubsonicSyncPolicy.albumCandidateKey(one) to listOf("track-${one.id}")
        }
        val plan = SubsonicSyncPolicy.planAlbumFallbackSync(
            albums = albums,
            localTrackIdsByAlbumKey = localIds,
            refreshSalt = 7L,
        )
        assertEquals(SubsonicSyncPolicy.UnchangedAlbumRefreshBudget, plan.albumsToFetch.size)
        assertEquals(10, plan.skippedAlbumCount)
        assertEquals(10, plan.skippedTrackCount)
        assertEquals(10, plan.seenTrackIds.size)
        // 被跳过的 id 与被强刷的专辑不重叠
        val fetchedAlbumIds = plan.albumsToFetch.map { it.id }.toSet()
        assertTrue(plan.seenTrackIds.none { id -> id.removePrefix("track-") in fetchedAlbumIds })
    }

    @Test
    fun fallbackPlanTreatsWholeAlbumKeyGroupAtomically() {
        // 同一 albumKey 下两张服务器专辑:本地数量等于两张之和才可跳过
        val discOne = album("d1", "Live", "Band", songCount = 5)
        val discTwo = album("d2", "Live", "Band", songCount = 5)
        val key = SubsonicSyncPolicy.albumCandidateKey(discOne)
        assertEquals(key, SubsonicSyncPolicy.albumCandidateKey(discTwo))

        val mismatchPlan = SubsonicSyncPolicy.planAlbumFallbackSync(
            albums = listOf(discOne, discTwo),
            localTrackIdsByAlbumKey = mapOf(key to List(5) { "t$it" }),
            refreshSalt = 0L,
        )
        // 本地只有 5 首,与 10 首不符 → 两张都拉取
        assertEquals(2, mismatchPlan.albumsToFetch.size)
        assertEquals(0, mismatchPlan.skippedAlbumCount)
    }

    @Test
    fun failedAlbumFetchReusesLocalIdsForTheAlbumKey() {
        val album = album("a1", "Blue", "Miles", songCount = 3)
        val key = SubsonicSyncPolicy.albumCandidateKey(album)
        assertEquals(
            listOf("t1", "t2", "t3"),
            SubsonicSyncPolicy.localTrackIdsForAlbum(
                album = album,
                localTrackIdsByAlbumKey = mapOf(key to listOf("t1", "t2", "t3")),
            ),
        )
        assertEquals(
            emptyList<String>(),
            SubsonicSyncPolicy.localTrackIdsForAlbum(album, emptyMap()),
        )
    }

    @Test
    fun fallbackPlanFetchesAlbumsWithUnknownSongCount() {
        val zeroCount = album("z1", "Zero", "Artist", songCount = 0)
        val plan = SubsonicSyncPolicy.planAlbumFallbackSync(
            albums = listOf(zeroCount),
            localTrackIdsByAlbumKey = emptyMap(),
            refreshSalt = 0L,
        )
        assertEquals(listOf(zeroCount), plan.albumsToFetch)
        assertEquals(0, plan.skippedTrackCount)
    }

    @Test
    fun emptyBulkDoesNotReplaceAlbumFanOut() {
        assertFalse(SubsonicSyncPolicy.shouldPreferSearch3Bulk(expectedSongCount = 40, bulkSongCount = 0))
    }

    @Test
    fun completeBulkSkipsAlbumFanOut() {
        assertTrue(SubsonicSyncPolicy.shouldPreferSearch3Bulk(expectedSongCount = 40, bulkSongCount = 40))
        assertTrue(SubsonicSyncPolicy.shouldPreferSearch3Bulk(expectedSongCount = 40, bulkSongCount = 41))
    }

    @Test
    fun partialBulkFallsBackToAlbums() {
        assertFalse(SubsonicSyncPolicy.shouldPreferSearch3Bulk(expectedSongCount = 40, bulkSongCount = 12))
    }

    @Test
    fun unknownExpectedCountDoesNotReplaceAlbumFanOut() {
        assertFalse(SubsonicSyncPolicy.shouldPreferSearch3Bulk(expectedSongCount = 0, bulkSongCount = 8))
        assertFalse(SubsonicSyncPolicy.shouldPreferSearch3Bulk(expectedSongCount = 0, bulkSongCount = 0))
    }

    @Test
    fun incompleteSearch3BulkDoesNotAuthorizeMissingRowDeletion() {
        assertFalse(
            SubsonicSyncPolicy.shouldAuthorizeMissingRowDeletion(
                usedSearch3 = true,
                expectedSongCount = 0,
                bulkSongCount = 8,
                existingRemoteCount = 120,
            ),
        )
        assertFalse(
            SubsonicSyncPolicy.shouldAuthorizeMissingRowDeletion(
                usedSearch3 = true,
                expectedSongCount = 40,
                bulkSongCount = 12,
                existingRemoteCount = 40,
            ),
        )
        assertFalse(
            SubsonicSyncPolicy.shouldAuthorizeMissingRowDeletion(
                usedSearch3 = true,
                expectedSongCount = 40,
                bulkSongCount = 40,
                existingRemoteCount = 400,
            ),
        )
        assertTrue(
            SubsonicSyncPolicy.shouldAuthorizeMissingRowDeletion(
                usedSearch3 = true,
                expectedSongCount = 40,
                bulkSongCount = 40,
                existingRemoteCount = 40,
            ),
        )
        assertTrue(
            SubsonicSyncPolicy.shouldAuthorizeMissingRowDeletion(
                usedSearch3 = false,
                expectedSongCount = 0,
                bulkSongCount = 8,
                existingRemoteCount = 120,
            ),
        )
    }

    @Test
    fun failedPlaylistFetchDoesNotReplaceLocalCopy() {
        assertFalse(
            SubsonicSyncPolicy.shouldReplaceSyncedPlaylist(
                fetchSucceeded = false,
                remoteSongCount = 0,
                matchedTrackCount = 0,
            ),
        )
    }

    @Test
    fun unmatchedRemoteSongsDoNotWipePlaylist() {
        assertFalse(
            SubsonicSyncPolicy.shouldReplaceSyncedPlaylist(
                fetchSucceeded = true,
                remoteSongCount = 8,
                matchedTrackCount = 0,
            ),
        )
    }

    @Test
    fun emptyOrPartialRemotePlaylistMayReplace() {
        assertTrue(
            SubsonicSyncPolicy.shouldReplaceSyncedPlaylist(
                fetchSucceeded = true,
                remoteSongCount = 0,
                matchedTrackCount = 0,
            ),
        )
        assertTrue(
            SubsonicSyncPolicy.shouldReplaceSyncedPlaylist(
                fetchSucceeded = true,
                remoteSongCount = 8,
                matchedTrackCount = 3,
            ),
        )
    }

    @Test
    fun unchangedRemotePlaylistDoesNotRewriteLocalRows() {
        assertFalse(
            SubsonicSyncPolicy.shouldRewriteSyncedPlaylist(
                existingName = "Favs",
                existingTrackIds = listOf("s1", "s2"),
                incomingName = "Favs",
                incomingTrackIds = listOf("s1", "s2"),
            ),
        )
        assertTrue(
            SubsonicSyncPolicy.shouldRewriteSyncedPlaylist(
                existingName = "Favs",
                existingTrackIds = listOf("s1", "s2"),
                incomingName = "Favorites",
                incomingTrackIds = listOf("s1", "s2"),
            ),
        )
        assertTrue(
            SubsonicSyncPolicy.shouldRewriteSyncedPlaylist(
                existingName = "Favs",
                existingTrackIds = listOf("s1"),
                incomingName = "Favs",
                incomingTrackIds = listOf("s1", "s2"),
            ),
        )
        assertTrue(
            SubsonicSyncPolicy.shouldRewriteSyncedPlaylist(
                existingName = null,
                existingTrackIds = null,
                incomingName = "Favs",
                incomingTrackIds = listOf("s1"),
            ),
        )
    }
}
