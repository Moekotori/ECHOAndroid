package app.echo.android.data

import android.content.Context
import app.echo.android.model.library.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Server reference data supplements public sources; it never replaces tags or public lookup. */
class SubsonicReferenceInformation(private val database: EchoLibraryDatabase, context: Context,
    private val endpoint: () -> SubsonicEndpoint?) {
    private val context = context.applicationContext
    private val gate = Mutex()
    private val cache = LinkedHashMap<String, Pair<Long, NavidromeInformation?>>()

    suspend fun artist(query: ArtistOnlineQuery, refresh: Boolean): NavidromeInformation? =
        find(NavidromeKind.Artist, query.name, null, refresh)
    suspend fun album(album: AlbumSummary, refresh: Boolean): NavidromeInformation? =
        find(NavidromeKind.Album, album.title, album.albumArtist ?: album.artist, refresh)

    private suspend fun find(kind: NavidromeKind, name: String, artist: String?, refresh: Boolean): NavidromeInformation? =
        withContext(Dispatchers.IO) { gate.withLock {
            val server = endpoint() ?: return@withLock null
            val key = "${server.sourceId}\u0000$kind\u0000$name\u0000${artist.orEmpty()}"
            val now = System.currentTimeMillis()
            cache[key]?.let { if (!refresh && now - it.first in 0..600_000L) return@withLock it.second }
            val dao = database.trackDao()
            val owned = if (kind == NavidromeKind.Artist) dao.hasSubsonicArtist(server.sourceId, name.normalizedForSearch())
                else artist != null && dao.hasSubsonicAlbum(server.sourceId, name.normalizedForSearch(), artist.normalizedForSearch())
            if (!owned) return@withLock null
            try {
                val client = SubsonicClient(server, appContext = context)
                val field = if (kind == NavidromeKind.Artist) "artist" else "album"
                val root = client.request("search3.view", listOf("query" to name, "artistCount" to if (field == "artist") "25" else "0",
                    "albumCount" to if (field == "album") "25" else "0", "songCount" to "0"))
                    .optJSONObject("searchResult3") ?: return@withLock null
                val candidates = root.jsonObjects(field).filter { row ->
                    row.optString("name").normalizedForSearch() == name.normalizedForSearch() &&
                        (artist == null || row.optString("artist").normalizedForSearch() == artist.normalizedForSearch())
                }
                val entry = candidates.singleOrNull()?.userEntry(server, kind)
                val result = entry?.let { client.information(server, it) }
                currentCoroutineContext().ensureActive()
                if (endpoint()?.sourceId != server.sourceId) return@withLock null
                cache[key] = now to result
                while (cache.size > 128) cache.remove(cache.keys.first())
                result
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        } }
}

class SubsonicArtistOnlineInfoLoader(private val public: ArtistOnlineInfoLoader,
    private val server: SubsonicReferenceInformation) : ArtistOnlineInfoLoader by public {
    override suspend fun loadServerProfile(query: ArtistOnlineQuery, refresh: Boolean): ArtistOnlineInfo? {
        val info = server.artist(query, refresh) ?: return null
        return ArtistOnlineInfo("", query.name, null, null, null, null, emptyList(), emptyList(), emptyList(),
            artworkUri = info.entry.artworkUri, serverDescription = info.description, serverUrl = info.sourceUrl)
    }
    override suspend fun loadProfile(query: ArtistOnlineQuery, language: String, refresh: Boolean): ArtistOnlineInfo? {
        val remote = loadServerProfile(query, refresh)
        val reference = try { public.loadProfile(query, language, refresh) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (remote == null) throw failure else null }
        return reference?.copy(artworkUri = remote?.artworkUri, serverDescription = remote?.serverDescription, serverUrl = remote?.serverUrl)
            ?: remote
    }
}

class SubsonicAlbumOnlineInfoLoader(private val public: AlbumOnlineInfoLoader,
    private val server: SubsonicReferenceInformation) : AlbumOnlineInfoLoader {
    override suspend fun load(album: AlbumSummary, language: String, refresh: Boolean): AlbumOnlineInfo? {
        val remote = server.album(album, refresh)
        val reference = try { public.load(album, language, refresh) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (remote == null) throw failure else null }
        if (remote == null) return reference
        val facts = remote.facts.toMap()
        val base = reference ?: AlbumOnlineInfo("", album.title, album.albumArtist ?: album.artist.orEmpty(),
            null, null, emptyList(), emptyList(), emptyList(), null, null, null, System.currentTimeMillis())
        return base.copy(serverDescription = remote.description, serverUrl = remote.sourceUrl,
            labels = (base.labels + facts["recordLabels"].orEmpty().split(" · ").filter(String::isNotBlank)).distinct())
    }
}
