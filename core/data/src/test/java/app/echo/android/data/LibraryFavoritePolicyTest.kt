package app.echo.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFavoritePolicyTest {
    @Test
    fun toggleOnThenOffLeavesTrackUnliked() {
        val trackId = "mediastore:42"
        val liked = LibraryFavoritePolicy.toggle(LibraryFavoriteSnapshot(), trackId)
        assertTrue(LibraryFavoritePolicy.isLiked(liked, trackId))

        val unliked = LibraryFavoritePolicy.toggle(liked, trackId)
        assertFalse(LibraryFavoritePolicy.isLiked(unliked, trackId))
        assertTrue(unliked.likedTrackIds.isEmpty())
    }

    @Test
    fun likedIdsSurviveSerializeReloadSnapshot() {
        val first = "saf:one"
        val second = "mediastore:two"
        var snapshot = LibraryFavoriteSnapshot()
        snapshot = LibraryFavoritePolicy.toggle(snapshot, first)
        snapshot = LibraryFavoritePolicy.toggle(snapshot, second)

        val reloaded = LibraryFavoritePolicy.parse(LibraryFavoritePolicy.serialize(snapshot))

        assertEquals(snapshot.likedTrackIds, reloaded.likedTrackIds)
        assertTrue(LibraryFavoritePolicy.isLiked(reloaded, first))
        assertTrue(LibraryFavoritePolicy.isLiked(reloaded, second))
        assertFalse(LibraryFavoritePolicy.isLiked(reloaded, "missing"))
    }

    @Test
    fun blankTrackIdDoesNotChangeSnapshot() {
        val original = LibraryFavoritePolicy.toggle(LibraryFavoriteSnapshot(), "keep")
        val unchanged = LibraryFavoritePolicy.toggle(original, "  ")
        assertEquals(original, unchanged)
        assertFalse(LibraryFavoritePolicy.isLiked(original, " "))
    }

    @Test
    fun favoriteAlbumsFollowLikedTracksNotRecentPlayOrder() {
        val liked = setOf("t-new", "t-old")
        val albumKeys = LibraryFavoritePolicy.favoriteAlbumKeys(
            likedTrackIds = liked,
            albumKeyByTrackId = mapOf(
                "t-new" to "album-b",
                "t-old" to "album-a",
                "recent-only" to "album-recent",
            ),
            favoritedAtByTrackId = mapOf(
                "t-new" to 20L,
                "t-old" to 10L,
            ),
            limit = 4,
        )
        assertEquals(listOf("album-b", "album-a"), albumKeys)
        assertFalse(albumKeys.contains("album-recent"))
    }
}
