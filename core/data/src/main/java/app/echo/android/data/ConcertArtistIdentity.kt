package app.echo.android.data

import app.echo.android.model.library.ArtistOnlineQuery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/** Resolve aliases once before provider matching, including the first visit to the Live tab. */
internal class ConcertArtistIdentity(private val lookup: suspend (ArtistOnlineQuery, Boolean) -> JSONObject?) {
    data class Result(val artist: JSONObject?, val names: List<String>, val failed: Boolean)

    suspend fun resolve(query: ArtistOnlineQuery, saved: JSONObject?, refresh: Boolean, budgetMillis: Long = 2500L): Result {
        var failed = false
        val artist = if (saved != null && !refresh) saved else try {
            withTimeout(budgetMillis) { lookup(query, refresh) }
        } catch (_: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            failed = true
            saved
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) {
            failed = true
            saved
        }
        val names = ConcertArtistMatcher.searchNames(listOf(query.name) + listOfNotNull(artist?.text("name")) +
            artist?.objects("aliases").orEmpty().mapNotNull { it.text("name") })
        return Result(artist, names, failed)
    }
}
