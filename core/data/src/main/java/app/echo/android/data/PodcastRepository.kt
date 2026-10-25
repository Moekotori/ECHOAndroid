package app.echo.android.data

import app.echo.android.model.podcast.EchoPodcastFeed
import app.echo.android.model.radio.EchoRadioStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl

class PodcastRepository(val store: PodcastStore, private val http: PodcastHttpClient) {
    private val refreshMutex = Mutex()
    suspend fun subscribe(raw: String): EchoPodcastFeed = refreshMutex.withLock {
        require(EchoRadioStation.validUrl(raw.trim()))
        val url = raw.trim().toHttpUrl().toString()
        val response = http.fetch(url, null, null)
        val parsed = withContext(Dispatchers.Default) {
            PodcastFeedParser.parse(requireNotNull(response.bytes), url, response.finalUrl, response.charset)
        }
        store.commit(parsed, response.etag, response.modified, subscribing = true)
        parsed.feed
    }
    suspend fun refresh(feed: EchoPodcastFeed) = refreshMutex.withLock {
        val (etag, modified) = store.validators(feed.id)
        val response = http.fetch(feed.url, etag, modified)
        response.bytes?.let { bytes ->
            val parsed = withContext(Dispatchers.Default) { PodcastFeedParser.parse(bytes, feed.url, response.finalUrl, response.charset) }
            store.commit(parsed, response.etag, response.modified, subscribing = false)
        }
    }
}
