package app.echo.android.data

import app.echo.android.model.library.ArtistOnlineQuery
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ConcertArtistIdentityTest {
    private val artist = JSONObject("""{"id":"artist","name":"张学友","aliases":[{"name":"張學友"},{"name":"Jacky Cheung"}]}""")

    @Test fun firstConcertVisitResolvesAliasesAndWarmIdentityAvoidsAnotherLookup() = runBlocking {
        var calls = 0
        val resolver = ConcertArtistIdentity { _, _ -> calls++; artist }
        val cold = resolver.resolve(ArtistOnlineQuery("张学友"), null, false)
        assertEquals(1, calls)
        assertTrue(ConcertArtistMatcher(cold.names).matchesName("Jacky Cheung"))
        assertTrue(cold.names.contains("張學友"))
        resolver.resolve(ArtistOnlineQuery("张学友"), artist, false)
        assertEquals(1, calls)
    }

    @Test fun failedRefreshRetainsKnownAliasesAndReportsFailure() = runBlocking {
        val resolver = ConcertArtistIdentity { _, _ -> error("offline") }
        val result = resolver.resolve(ArtistOnlineQuery("张学友"), artist, true)
        assertTrue(result.failed)
        assertTrue(ConcertArtistMatcher(result.names).matchesName("Jacky Cheung"))
    }

    @Test fun localTimeoutFallsBackToQueryWithoutInventingAnIdentity() = runBlocking {
        val result = ConcertArtistIdentity { _, _ -> delay(1000L); artist }
            .resolve(ArtistOnlineQuery("Local Artist"), null, false, budgetMillis = 10L)
        assertTrue(result.failed)
        assertNull(result.artist)
        assertEquals(listOf("Local Artist"), result.names)
    }

    @Test fun parentCancellationIsNotTreatedAsAProviderFailure() = runBlocking {
        val result = withTimeoutOrNull(20L) {
            ConcertArtistIdentity { _, _ -> delay(1000L); artist }
                .resolve(ArtistOnlineQuery("Local Artist"), null, false, budgetMillis = 5000L)
        }
        assertNull(result)
    }
}
