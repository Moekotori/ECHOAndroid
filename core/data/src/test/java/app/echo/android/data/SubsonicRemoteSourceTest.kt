package app.echo.android.data

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubsonicRemoteSourceTest {
    @Test
    fun normalizesNavidromeWebUiAndRestBases() {
        val expected = "http://host:4533"
        assertEquals(expected, normalizeSubsonicBaseUrl("http://host:4533/app"))
        assertEquals(expected, normalizeSubsonicBaseUrl("http://host:4533/rest"))
        assertEquals(expected, normalizeSubsonicBaseUrl("http://host:4533/app/#/album/1"))
        assertEquals(expected, normalizeSubsonicBaseUrl("http://host:4533/app/"))
        assertEquals("http://192.168.1.8:4533", normalizeSubsonicBaseUrl("192.168.1.8:4533"))
        assertEquals("https://music.example.com/music", normalizeSubsonicBaseUrl("https://music.example.com/music/rest"))
    }

    @Test
    fun pingRequestsLandOnRestNotAppOrDoubleRest() {
        val bases = listOf(
            "http://host:4533/app",
            "http://host:4533/rest",
            "http://host:4533/app/#/album/1",
            "192.168.1.8:4533",
        )
        for (base in bases) {
            val requested = pingUrl(base)
            val path = URI(requested).path
            assertEquals("base=$base", "/rest/ping.view", path)
            assertFalse(requested.contains("/app/rest"))
            assertFalse(URI(requested).path.contains("/rest/rest"))
        }
    }

    @Test
    fun mappedStreamAndCoverUrisOmitRotatingTokensAndStayStableAcrossSalts() {
        val endpoint = SubsonicEndpoint(
            baseUrl = "https://navidrome.example/app",
            username = "user",
            password = "pass",
        )
        val song = sampleSong()
        val first = song.toLibraryTrackEntity(endpoint, scanRunId = 1L)
        val second = song.toLibraryTrackEntity(endpoint, scanRunId = 2L)

        assertEquals(first.fingerprint, second.fingerprint)
        assertEquals(first.contentUri, second.contentUri)
        assertEquals(first.artworkUri, second.artworkUri)
        assertTrue(first.contentUri.contains("id=s1"))
        assertTrue(first.artworkUri.orEmpty().contains("id=cover-1"))
        assertFalse(first.contentUri.contains("t="))
        assertFalse(first.contentUri.contains("s="))
        assertFalse(first.artworkUri.orEmpty().contains("t="))
        assertFalse(first.artworkUri.orEmpty().contains("s="))
        assertTrue(first.contentUri.contains("/rest/stream.view"))
        assertTrue(first.artworkUri.orEmpty().contains("/rest/getCoverArt.view"))
        assertTrue(first.artworkUri.orEmpty().contains("size=${SubsonicClient.CoverArtSizePx}"))
        assertFalse(first.contentUri.contains("/app/"))
    }

    @Test
    fun albumListRequestsUseSubsonicMaxPageSize() {
        val requested = ArrayList<String>()
        val client = SubsonicClient(
            endpoint = SubsonicEndpoint(
                baseUrl = "https://navidrome.example",
                username = "user",
                password = "pass",
            ),
            httpGet = { url ->
                requested += url
                """{"subsonic-response":{"status":"ok","version":"1.16.1","albumList2":{"album":[]}}}"""
            },
        )
        client.fetchAlbums()
        val albumUrl = requested.first { it.contains("getAlbumList2.view") }
        assertEquals("500", queryParam(albumUrl, "size"))
    }

    @Test
    fun search3TriesEmptyQueryBeforeWildcard() {
        val queries = ArrayList<String>()
        val client = SubsonicClient(
            endpoint = SubsonicEndpoint(
                baseUrl = "https://navidrome.example",
                username = "user",
                password = "pass",
            ),
            httpGet = { url ->
                if (url.contains("search3.view")) {
                    queries += queryParam(url, "query").orEmpty()
                    search3Body(listOf(songJson("s1")))
                } else {
                    """{"subsonic-response":{"status":"ok","version":"1.16.1"}}"""
                }
            },
        )
        val songs = client.fetchSongsBySearch3()
        assertEquals(1, songs.size)
        assertEquals("", queries.first())
        assertFalse(queries.contains("*"))
    }

    @Test
    fun search3ContinuesWhenServerReturnsCappedPages() {
        val offsets = ArrayList<Int>()
        val client = SubsonicClient(
            endpoint = SubsonicEndpoint(
                baseUrl = "https://navidrome.example",
                username = "user",
                password = "pass",
            ),
            httpGet = { url ->
                if (!url.contains("search3.view")) {
                    """{"subsonic-response":{"status":"ok","version":"1.16.1"}}"""
                } else {
                    val offset = queryParam(url, "songOffset")?.toIntOrNull() ?: 0
                    offsets += offset
                    val songs = when (offset) {
                        0 -> (1..100).map { songJson("s$it") }
                        100 -> (101..150).map { songJson("s$it") }
                        else -> emptyList()
                    }
                    search3Body(songs)
                }
            },
        )
        val songs = client.fetchSongsBySearch3(pageSize = 500, maxSongs = 1_000)
        assertEquals(150, songs.size)
        assertEquals("s1", songs.first().id)
        assertEquals("s150", songs.last().id)
        assertTrue(offsets.contains(0))
        assertTrue(offsets.contains(100))
    }

    @Test
    fun albumListContinuesWhenServerReturnsCappedPages() {
        val offsets = ArrayList<Int>()
        val client = SubsonicClient(
            endpoint = SubsonicEndpoint(
                baseUrl = "https://navidrome.example",
                username = "user",
                password = "pass",
            ),
            httpGet = { url ->
                if (!url.contains("getAlbumList2.view")) {
                    """{"subsonic-response":{"status":"ok","version":"1.16.1"}}"""
                } else {
                    val offset = queryParam(url, "offset")?.toIntOrNull() ?: 0
                    offsets += offset
                    val albums = when (offset) {
                        0 -> (1..100).map { """{"id":"a$it","name":"Album $it","songCount":1}""" }
                        100 -> (101..130).map { """{"id":"a$it","name":"Album $it","songCount":1}""" }
                        else -> emptyList()
                    }
                    """{"subsonic-response":{"status":"ok","version":"1.16.1","albumList2":{"album":[${albums.joinToString(",")}]}}}"""
                }
            },
        )
        val albums = client.fetchAlbums(pageSize = 500, maxAlbums = 2_000)
        assertEquals(130, albums.size)
        assertEquals("a1", albums.first().id)
        assertEquals("a130", albums.last().id)
        assertTrue(offsets.contains(0))
        assertTrue(offsets.contains(100))
    }

    @Test
    fun jsonNullTitleDoesNotBecomeLiteralNull() {
        val client = SubsonicClient(
            endpoint = SubsonicEndpoint(
                baseUrl = "https://navidrome.example",
                username = "user",
                password = "pass",
            ),
            httpGet = {
                search3Body(listOf("""{"id":"s1","title":null,"artist":"A","album":"B","duration":1,"size":1}"""))
            },
        )
        val songs = client.fetchSongsBySearch3()
        assertEquals(1, songs.size)
        assertEquals("s1", songs[0].id)
        assertEquals("", songs[0].title)
        assertEquals("A", songs[0].artist)
    }

    @Test
    fun blankSongIdsAreDroppedFromAlbumFetch() {
        val client = SubsonicClient(
            endpoint = SubsonicEndpoint(
                baseUrl = "https://navidrome.example",
                username = "user",
                password = "pass",
            ),
            httpGet = {
                """{"subsonic-response":{"status":"ok","version":"1.16.1","album":{"id":"a1","song":[{"id":"","title":"Missing"},{"id":"s2","title":"Keep","artist":"A","duration":1,"size":1}]}}}"""
            },
        )
        val songs = client.fetchAlbumSongs(
            SubsonicAlbum(id = "a1", name = "Album", artist = "A", coverArt = null, year = null, songCount = 2),
        )
        assertEquals(1, songs.size)
        assertEquals("s2", songs[0].id)
    }

    @Test
    fun mappedTracksWithDifferentClientSaltsShareFingerprint() {
        val endpoint = SubsonicEndpoint(
            baseUrl = "https://navidrome.example",
            username = "user",
            password = "pass",
        )
        val song = sampleSong()
        val firstClient = SubsonicClient(
            endpoint = endpoint,
            httpGet = { """{"subsonic-response":{"status":"ok","version":"1.16.1"}}""" },
            saltFactory = { "salt-one" },
        )
        val secondClient = SubsonicClient(
            endpoint = endpoint,
            httpGet = { """{"subsonic-response":{"status":"ok","version":"1.16.1"}}""" },
            saltFactory = { "salt-two" },
        )
        firstClient.ping()
        secondClient.ping()
        val first = song.toLibraryTrackEntity(endpoint, scanRunId = 10L)
        val second = song.toLibraryTrackEntity(endpoint, scanRunId = 99L)
        assertEquals(first.fingerprint, second.fingerprint)
        assertFalse(first.contentUri.contains("salt-one"))
        assertFalse(first.contentUri.contains("salt-two"))
        assertEquals(firstClient.streamUrl("s1"), secondClient.streamUrl("s1"))
    }

    @Test
    fun failedSubsonicStatusSurfacesErrorMessage() {
        val thrown = runCatching {
            parseSubsonicResponse(
                """{"subsonic-response":{"status":"failed","error":{"code":40,"message":"Wrong username or password"}}}""",
            )
        }.exceptionOrNull()
        assertNotNull(thrown)
        assertEquals("Wrong username or password", thrown?.message)
    }

    @Test
    fun pingFailedJsonSurfacesServerErrorMessage() {
        val client = SubsonicClient(
            endpoint = SubsonicEndpoint(
                baseUrl = "https://navidrome.example",
                username = "user",
                password = "wrong",
            ),
            httpGet = {
                """{"subsonic-response":{"status":"failed","version":"1.16.1","error":{"code":40,"message":"Wrong username or password"}}}"""
            },
        )
        val thrown = runCatching { client.ping() }.exceptionOrNull()
        assertEquals("Wrong username or password", thrown?.message)
    }

    @Test
    fun nonSuccessHttpJsonIsNotCollapsedToNoResponse() {
        val failedJson =
            """{"subsonic-response":{"status":"failed","error":{"code":40,"message":"Wrong username or password"}}}"""
        val body = subsonicHttpBody(responseCode = 401, successBody = null, errorBody = failedJson)
        assertEquals(failedJson, body)
        val thrown = runCatching { parseSubsonicResponse(requireNotNull(body)) }.exceptionOrNull()
        assertEquals("Wrong username or password", thrown?.message)
        assertFalse(thrown?.message.orEmpty().contains("无响应"))
    }

    @Test
    fun blankErrorHttpStillCountsAsNoBody() {
        assertEquals(null, subsonicHttpBody(responseCode = 401, successBody = null, errorBody = "  "))
        assertEquals(null, subsonicHttpBody(responseCode = 500, successBody = null, errorBody = null))
    }

    @Test
    fun persistedOriginMatchesNormalizedBase() {
        val entered = "http://host:4533/app/#/album/1"
        assertEquals(normalizeSubsonicBaseUrl(entered), SubsonicEndpoint(entered, "user", "pass").normalizedBaseUrl)
        assertEquals("http://host:4533", SubsonicEndpoint(entered, "user", "pass").normalizedBaseUrl)
    }
}

private fun pingUrl(base: String): String {
    var requested = ""
    val client = SubsonicClient(
        endpoint = SubsonicEndpoint(baseUrl = base, username = "user", password = "pass"),
        httpGet = { url ->
            requested = url
            """{"subsonic-response":{"status":"ok","version":"1.16.1"}}"""
        },
    )
    client.ping()
    return requested
}

private fun queryParam(url: String, name: String): String? {
    val query = url.substringAfter('?', missingDelimiterValue = "")
    if (query.isEmpty()) return null
    return query.split('&').firstNotNullOfOrNull { part ->
        val separator = part.indexOf('=')
        if (separator <= 0) {
            null
        } else if (part.substring(0, separator) == name) {
            java.net.URLDecoder.decode(part.substring(separator + 1), "UTF-8")
        } else {
            null
        }
    }
}

private fun songJson(id: String): String =
    """{"id":"$id","title":"$id","artist":"A","album":"B","duration":1,"size":1}"""

private fun search3Body(songs: List<String>): String {
    val songJson = songs.joinToString(",")
    return """{"subsonic-response":{"status":"ok","version":"1.16.1","searchResult3":{"song":[$songJson]}}}"""
}

private fun sampleSong(): SubsonicSong =
    SubsonicSong(
        id = "s1",
        title = "Bulk",
        artist = "A",
        album = "B",
        albumArtist = "A",
        coverArt = "cover-1",
        durationSeconds = 12,
        trackNumber = 1,
        discNumber = 1,
        year = 2020,
        contentType = "audio/flac",
        suffix = "flac",
        sizeBytes = 8,
        bitRateKbps = 1_000,
        path = "Music/B/Bulk.flac",
    )
