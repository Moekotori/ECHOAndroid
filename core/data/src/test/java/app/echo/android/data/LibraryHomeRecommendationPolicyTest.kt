package app.echo.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryHomeRecommendationPolicyTest {
    @Test
    fun favoritesAndPlaysRankAboveUnusedRecentlyAdded() {
        val now = 1_700_000_000_000L
        val ranked = LibraryHomeRecommendationPolicy.rankAlbumKeys(
            seeds = listOf(
                LibraryAlbumListenSeed(
                    albumKey = "new-unplayed",
                    playCount = 0,
                    lastPlayedAtEpochMs = 0L,
                    favoritedAtEpochMs = 0L,
                    addedAtSeconds = now / 1000L,
                ),
                LibraryAlbumListenSeed(
                    albumKey = "loved",
                    playCount = 1,
                    lastPlayedAtEpochMs = now - 3_600_000L,
                    favoritedAtEpochMs = now - 1_000L,
                    addedAtSeconds = now / 1000L - 10_000L,
                ),
                LibraryAlbumListenSeed(
                    albumKey = "heavy-rotation",
                    playCount = 40,
                    lastPlayedAtEpochMs = now - 86_400_000L,
                    favoritedAtEpochMs = 0L,
                    addedAtSeconds = now / 1000L - 20_000L,
                ),
            ),
            nowEpochMs = now,
            refreshSalt = 0,
            limit = 3,
        )
        assertEquals("loved", ranked.first())
        assertTrue(ranked.contains("heavy-rotation"))
        assertFalse(ranked.first() == "new-unplayed")
    }

    @Test
    fun neglectedPlaysOutrankBrandNewUnplayedAlbums() {
        val now = 1_700_000_000_000L
        val ranked = LibraryHomeRecommendationPolicy.rankAlbumKeys(
            seeds = listOf(
                LibraryAlbumListenSeed(
                    albumKey = "fresh",
                    playCount = 0,
                    lastPlayedAtEpochMs = 0L,
                    favoritedAtEpochMs = 0L,
                    addedAtSeconds = now / 1000L,
                ),
                LibraryAlbumListenSeed(
                    albumKey = "forgotten",
                    playCount = 8,
                    lastPlayedAtEpochMs = now - 40L * 86_400_000L,
                    favoritedAtEpochMs = 0L,
                    addedAtSeconds = now / 1000L - 80_000L,
                ),
            ),
            nowEpochMs = now,
            refreshSalt = 0,
            limit = 2,
        )
        assertEquals(listOf("forgotten", "fresh"), ranked)
    }

    @Test
    fun sameRefreshSaltIsStable() {
        val now = 1_700_000_000_000L
        val twins = listOf(
            LibraryAlbumListenSeed("alpha", playCount = 5, lastPlayedAtEpochMs = now, favoritedAtEpochMs = 0L, addedAtSeconds = 1L),
            LibraryAlbumListenSeed("bravo", playCount = 5, lastPlayedAtEpochMs = now, favoritedAtEpochMs = 0L, addedAtSeconds = 1L),
        )
        val first = LibraryHomeRecommendationPolicy.rankAlbumKeys(twins, now, refreshSalt = 7, limit = 2)
        val second = LibraryHomeRecommendationPolicy.rankAlbumKeys(twins, now, refreshSalt = 7, limit = 2)
        assertEquals(setOf("alpha", "bravo"), first.toSet())
        assertEquals(first, second)
    }
}
