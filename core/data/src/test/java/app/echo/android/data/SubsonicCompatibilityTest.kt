package app.echo.android.data

import app.echo.android.model.library.NavidromeQuery
import app.echo.android.model.library.NavidromeCollection
import java.net.URI
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SubsonicCompatibilityTest {
    private val server = SubsonicEndpoint("https://music.example", "alice", "secret")
    private fun ok(value: String) = "{\"subsonic-response\":{\"status\":\"ok\",$value}}"

    @Test fun normalizedIpv6AndEscapedProxyPathsRemainValidThroughResourceCreation() {
        for (base in listOf("http://[::1]:4533", "https://music.example/my%20music", "https://music.example/a%2Fb")) {
            val endpoint = SubsonicEndpoint("$base/app/#/album/1", "alice", "secret")
            assertEquals(base, endpoint.normalizedBaseUrl)
            val uri = URI(SubsonicClient(endpoint).coverArtUrl("cover with space"))
            assertEquals(URI(base).rawPath + "/rest/getCoverArt.view", uri.rawPath)
            assertTrue(uri.rawQuery.contains("echoSource="))
        }
    }

    @Test fun albumFallbackKeepsDistinctReleasesAndSuppliesMissingCover() {
        val first = SubsonicAlbum("release-1", "Live", "Band", "cover-1", 2020, 1)
        val second = first.copy(id = "release-2", coverArt = "cover-2")
        val metadata = SubsonicSongMetadata(listOf(first, second))
        fun track(id: String) = metadata.enrich(JSONObject("{\"id\":\"song\",\"albumId\":\"$id\",\"album\":\"Live\",\"artist\":\"Band\"}").toSubsonicSong())
            .toLibraryTrackEntity(server, 1)
        val a = track(first.id); val b = track(second.id)
        assertTrue(a.artworkUri!!.contains("cover-1"))
        assertTrue(b.artworkUri!!.contains("cover-2"))
        assertNotEquals(a.albumKey, b.albumKey)
        assertNotEquals(a.fingerprint, b.fingerprint)
        assertEquals(a.albumKey, a.toEchoTrack().toLibraryTrackEntity().albumKey)
        // A known, different release ID must never borrow art from a same-name cached release.
        val unseen = JSONObject("{\"id\":\"other\",\"albumId\":\"not-cached\",\"album\":\"Live\",\"artist\":\"Band\"}").toSubsonicSong()
        assertNull(SubsonicSongMetadata(listOf(first)).enrich(unseen).coverArt)
    }

    @Test fun browseSnapshotReusesPagesExpiresAndRefreshes() {
        var clock = 0L
        var loads = 0
        val snapshots = SubsonicBrowseSnapshots { clock }
        val client = SubsonicClient(server, snapshots = snapshots, httpGet = {
            loads++
            ok("\"starred2\":{\"song\":[" + (0..60).joinToString(",") { "{\"id\":\"$it\"}" } + "]}")
        })
        client.readUserLibrary(server, NavidromeQuery.Collection(NavidromeCollection.Favorites, 0))
        val second = client.readUserLibrary(server, NavidromeQuery.Collection(NavidromeCollection.Favorites, 1))
        assertEquals("50", second.entries.first().id)
        assertEquals(1, loads)
        clock = 60_001
        client.readUserLibrary(server, NavidromeQuery.Collection(NavidromeCollection.Favorites, 1))
        assertEquals(2, loads)
        snapshots.clear()
        client.readUserLibrary(server, NavidromeQuery.Collection(NavidromeCollection.Favorites, 0))
        assertEquals(3, loads)
    }

    @Test fun albumFallbackContinuesPastItsInitialIndexWindow() {
        val offsets = mutableListOf<Int>()
        val client = SubsonicClient(server, httpGet = { url ->
            val offset = URI(url).rawQuery.split('&').first { it.startsWith("offset=") }.substringAfter('=').toInt()
            offsets += offset
            val rows = (offset until minOf(offset + 500, 2_003)).joinToString(",") { "{\"id\":\"a$it\",\"name\":\"Album\"}" }
            ok("\"albumList2\":{\"album\":[$rows]}")
        })
        val first = client.fetchAlbums()
        assertEquals(2_000, first.size)
        val next = client.fetchAlbums(maxAlbums = 500, startOffset = first.size)
        assertEquals(listOf("a2000", "a2001", "a2002"), next.map { it.id })
        assertTrue(offsets.contains(2_000))
    }

    @Test fun streamingSyncCrossesTheOldTwentyThousandSongLimit() = runBlocking {
        val client = SubsonicClient(server, httpGet = { url ->
            val offset = URI(url).rawQuery.split('&').first { it.startsWith("songOffset=") }.substringAfter('=').toInt()
            val rows = (offset until minOf(offset + 500, 20_003)).joinToString(",") { "{\"id\":\"s$it\"}" }
            ok("\"searchResult3\":{\"song\":[$rows]}")
        })
        var count = 0
        val total = client.forEachSongPage { page -> assertTrue(page.size <= 500); count += page.size }
        assertEquals(20_003, total)
        assertEquals(total, count)
    }

    @Test fun cancellingAReadReleasesTheBlockedHttpCall() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        http.createContext("/rest/ping.view") { exchange ->
            entered.countDown()
            release.await(3, TimeUnit.SECONDS)
            exchange.close()
        }
        http.start()
        try {
            val reading = launch(Dispatchers.IO) {
                SubsonicClient(SubsonicEndpoint("http://127.0.0.1:${http.address.port}", "u", "p"),
                    cancellationJob = currentCoroutineContext()[Job]).ping()
            }
            assertTrue(withContext(Dispatchers.IO) { entered.await(2, TimeUnit.SECONDS) })
            withTimeout(2_000) { reading.cancelAndJoin() }
            assertTrue(reading.isCancelled)
        } finally { release.countDown(); http.stop(0) }
    }

    @Test fun uncachedAlbumsSupplyCoverOnceAcrossSongPages() = runBlocking {
        var requests = 0
        val client = SubsonicClient(server, httpGet = {
            requests++
            ok("\"album\":{\"id\":\"beyond-first-window\",\"name\":\"Live\",\"coverArt\":\"album-cover\"}")
        })
        val metadata = SubsonicSongMetadata(emptyList())
        val song = JSONObject("{\"id\":\"song\",\"albumId\":\"beyond-first-window\"}").toSubsonicSong()
        val first = metadata.enrichMissingCovers(client, listOf(song, song.copy(id = "song-2")))
        assertTrue(first.all { it.coverArt == "album-cover" })
        assertEquals("album-cover", metadata.enrichMissingCovers(client, listOf(song)).single().coverArt)
        assertEquals(1, requests)
        assertEquals(0, metadata.failedAlbumLookups)
    }

    @Test fun failedAlbumCoverLookupIsBoundedAndReported() = runBlocking {
        var requests = 0
        val client = SubsonicClient(server, httpGet = { requests++; error("offline") })
        val metadata = SubsonicSongMetadata(emptyList())
        val song = JSONObject("{\"id\":\"song\",\"albumId\":\"missing\"}").toSubsonicSong()
        repeat(3) { assertNull(metadata.enrichMissingCovers(client, listOf(song)).single().coverArt) }
        assertEquals(1, requests)
        assertEquals(1, metadata.failedAlbumLookups)
    }

    @Test fun decodedProxyPathFromOlderSettingsNormalizesIdempotently() {
        val normalized = normalizeSubsonicBaseUrl("https://music.example/my music/app/#/album/1")
        assertEquals("https://music.example/my%20music", normalized)
        assertEquals(normalized, normalizeSubsonicBaseUrl(normalized))
        assertEquals(URI(normalized).rawPath + "/rest/stream.view",
            URI(SubsonicClient(SubsonicEndpoint(normalized, "user", "pass")).streamUrl("s")).rawPath)
    }

    @Test fun navidromePasswordWhitespaceSurvivesSecretPersistenceAndMigration() {
        val password = " leading and trailing "
        assertEquals(password, normalizedSecretValue(EchoSecretKeys.SubsonicPassword, password))
        assertNull(normalizedSecretValue(EchoSecretKeys.SubsonicPassword, "   "))
        assertEquals("token", normalizedSecretValue(EchoSecretKeys.EchoLinkToken, " token "))
        val decoded = decodeSubsonicConnections(encodeSubsonicConnections(listOf(server.copy(password = password))))
        assertEquals(password, decoded.single().password)
        assertEquals(password, savedSubsonicConnection(decoded, server.baseUrl, server.username)?.password)
        assertNull(savedSubsonicConnection(decoded, server.baseUrl, "another-account"))
    }

    @Test fun bookmarkDetailsKeepTheSavedPositionAndNote() {
        val client = SubsonicClient(server, httpGet = { ok("\"song\":{\"id\":\"s\",\"title\":\"Updated title\"}") })
        val entry = app.echo.android.model.library.NavidromeEntry("s", "Old title", app.echo.android.model.library.NavidromeKind.Track,
            positionMs = 42_000, comment = "Resume here")
        val detail = client.information(server, entry).entry
        assertEquals("Updated title", detail.name)
        assertEquals(42_000L, detail.positionMs)
        assertEquals("Resume here", detail.comment)
    }

    @Test fun searchPagesDoNotSkipSongsWhenTheServerCapsBatches() {
        var calls = 0
        val client = SubsonicClient(server, httpGet = { url ->
            calls++
            val params = URI(url).rawQuery.split('&').associate { it.substringBefore('=') to it.substringAfter('=') }
            val offset = params.getValue("songOffset").toInt()
            val count = minOf(3, params.getValue("songCount").toInt())
            val rows = (offset until minOf(offset + count, 25)).joinToString(",") { "{\"id\":\"s$it\"}" }
            ok("\"searchResult3\":{\"song\":[$rows]}")
        })
        val first = client.readUserLibrary(server, NavidromeQuery.Search("music"))
        val next = client.readUserLibrary(server, NavidromeQuery.Search("music", 1))
        assertEquals((0..15).map { "s$it" }, first.entries.map { it.id })
        assertTrue(first.hasMore)
        assertEquals((16..24).map { "s$it" }, next.entries.map { it.id })
        assertFalse(next.hasMore)
        assertTrue(calls <= 34)
    }

    @Test fun queueVersionNegotiationKeepsUnsupportedExtensionsOnTheLegacyApi() {
        val paths = mutableListOf<String>()
        val client = SubsonicClient(server, httpGet = { url ->
            paths += URI(url).path
            if (url.contains("getOpenSubsonicExtensions"))
                ok("\"openSubsonicExtensions\":[{\"name\":\"indexBasedQueue\",\"versions\":[2]}]")
            else ok("\"playQueue\":{\"current\":\"s\",\"entry\":[{\"id\":\"s\"}]}")
        })
        assertEquals("s", client.readQueue(server).entries.single().id)
        assertTrue(paths.last().endsWith("getPlayQueue.view"))
    }

    @Test fun structuredLyricOffsetsFollowLrcDirectionAndNeverOverflow() {
        val lyrics = parseSubsonicStructuredLyrics(JSONObject("""{"lyricsList":{"structuredLyrics":[
            {"lang":"ja","synced":true,"offset":200,"line":[{"start":1000,"value":"Earlier"},{"value":"Untimed"}]}
        ]}}"""))!!
        assertEquals(800L, lyrics.lines[0].startMs)
        assertEquals(-1L, lyrics.lines[1].startMs)
        assertEquals(1200L, subsonicLyricStartMs(1000, -200))
        assertEquals(0L, subsonicLyricStartMs(100, 200))
        assertEquals(Long.MAX_VALUE, subsonicLyricStartMs(1000, Long.MIN_VALUE))
    }

    @Test fun onlineBrowseUpdatesTagsWithoutDroppingLocalRatingColumns() {
        val original = JSONObject("{\"id\":\"s\",\"title\":\"Old title\"}").toSubsonicSong().toLibraryTrackEntity(server, 1)
            .copy(rating = 5, localRating = 5, embeddedRating = 2)
        val refreshed = mergeSubsonicBrowseTrack(original.toEchoTrack().copy(title = "Server title"), original, server, 2)
        assertEquals("Server title", refreshed.title)
        assertEquals(5, refreshed.rating)
        assertEquals(5, refreshed.localRating)
        assertEquals(2, refreshed.embeddedRating)
    }

    @Test fun archivedConnectionsKeepAccountsSeparateAndRoundTripSecrets() {
        val accounts = listOf(server, server.copy(username = "bob", password = "different"))
        val decoded = decodeSubsonicConnections(encodeSubsonicConnections(accounts))
        assertEquals(accounts.map { it.sourceId }, decoded.map { it.sourceId })
        assertEquals(accounts.map { it.password }, decoded.map { it.password })
    }
}
