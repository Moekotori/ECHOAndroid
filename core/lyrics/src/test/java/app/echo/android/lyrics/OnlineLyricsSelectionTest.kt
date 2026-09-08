package app.echo.android.lyrics

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

class OnlineLyricsSelectionTest {
    private val request = EchoLyricsSearchRequest("Song", "Artist", "Album", 180000)
    private val lyricResponse = """{"lrc":{"lyric":"[00:01.00]NetEase"}}"""

    private fun song(id: Int, title: String = "Song", duration: Long = 180000, album: String = "Album") = JSONObject()
        .put("id", id).put("name", title).put("duration", duration)
        .put("artists", JSONArray().put(JSONObject().put("name", "Artist")))
        .put("album", JSONObject().put("name", album))

    private fun searchResponse(vararg songs: JSONObject) = JSONObject()
        .put("result", JSONObject().put("songs", JSONArray(songs.toList()))).toString()

    private fun lrclib(duration: Int = 180, synced: Boolean = true) = JSONArray().put(JSONObject()
        .put("id", 7).put("trackName", "Song").put("artistName", "Artist").put("albumName", "Album")
        .put("duration", duration).put(if (synced) "syncedLyrics" else "plainLyrics",
            if (synced) "[00:01.00]LRCLIB" else "LRCLIB plain")).toString()

    @Test fun strongMatchUsesOnlyTwoRequests() {
        val urls = mutableListOf<String>()
        val resolver = OnlineLyricsResolver { url, _ ->
            urls += url
            when {
                url.contains("api/search") -> searchResponse(song(1), song(2))
                url.contains("song/lyric") -> lyricResponse
                else -> error("Strong match must not need another provider")
            }
        }
        assertEquals("NetEase", resolver.loadForTrack(request)?.lines?.first()?.text)
        assertEquals(2, urls.size)
    }

    @Test fun partialTitleDoesNotDownloadOrBlockExactLrclibMatch() {
        var downloads = 0
        val resolver = OnlineLyricsResolver { url, _ -> when {
            url.contains("music.163.com/api/search") -> searchResponse(song(1, title = "Song Again"))
            url.contains("song/lyric") -> { downloads++; lyricResponse }
            else -> lrclib()
        } }
        assertEquals("LRCLIB", resolver.loadForTrack(request)?.lines?.first()?.text)
        assertEquals(0, downloads)
    }

    @Test fun comparesBorderlineMatchBeforeDownloadingLyrics() {
        var downloads = 0
        val resolver = OnlineLyricsResolver { url, _ -> when {
            url.contains("music.163.com/api/search") -> searchResponse(song(1, duration = 187000))
            url.contains("song/lyric") -> { downloads++; lyricResponse }
            else -> { assertFalse(url.contains("album_name")); lrclib() }
        } }
        assertEquals("LRCLIB", resolver.loadForTrack(request)?.lines?.first()?.text)
        assertEquals(0, downloads)
    }

    @Test fun plainFastResultCanBeReplacedBySyncedLyricsWithoutRepeatedDownload() {
        var downloads = 0
        val resolver = OnlineLyricsResolver { url, _ -> when {
            url.contains("music.163.com/api/search") -> searchResponse(song(1))
            url.contains("song/lyric") -> { downloads++; """{"lrc":{"lyric":"Plain NetEase"}}""" }
            else -> lrclib()
        } }
        assertEquals("LRCLIB", resolver.loadForTrack(request)?.lines?.first()?.text)
        assertEquals(1, downloads)
    }

    @Test fun retainsPlainFallbackWhenSecondProviderFails() {
        val resolver = OnlineLyricsResolver { url, _ -> when {
            url.contains("music.163.com/api/search") -> searchResponse(song(1))
            url.contains("song/lyric") -> """{"lrc":{"lyric":"Plain NetEase"}}"""
            else -> null
        } }
        assertEquals("Plain NetEase", resolver.loadForTrack(request)?.lines?.first()?.text)
    }

    @Test fun scansBeyondFirstFiveButBoundsFailedDownloads() {
        var downloads = 0
        var response = searchResponse(*(1..6).map { song(it, title = if (it == 6) "Song" else "Wrong $it") }.toTypedArray())
        val resolver = OnlineLyricsResolver { url, _ -> when {
            url.contains("music.163.com/api/search") -> response
            url.contains("song/lyric") -> { downloads++; if (url.contains("id=6&")) lyricResponse else null }
            else -> "[]"
        } }
        assertNotNull(resolver.loadForTrack(request))
        assertEquals(1, downloads)
        downloads = 0
        response = searchResponse(*(1..15).map { song(it) }.toTypedArray())
        assertNull(resolver.loadForTrack(request))
        assertEquals(3, downloads)
    }

    @Test fun manualSearchKeepsPartialTitlesButRanksExactMatchesAcrossSourcesFirst() {
        val resolver = OnlineLyricsResolver { url, _ -> when {
            url.contains("music.163.com/api/search") -> searchResponse(song(1, title = "Song Again"))
            url.contains("song/lyric") -> lyricResponse
            else -> lrclib()
        } }
        val results = resolver.search(request)
        assertEquals(listOf("lrclib:7", "netease:1"), results.map { it.id })
    }

    @Test fun cancellationAfterSearchStopsDownloadsAndFallback() {
        var cancelled = false
        var requests = 0
        val resolver = OnlineLyricsResolver { _, _ ->
            requests++
            cancelled = true
            searchResponse(song(1))
        }
        try {
            resolver.loadForTrack(request) { if (cancelled) throw CancellationException() }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertEquals(1, requests)
    }

    @Test fun conflictingAlbumLanguageNeverDownloadsOrAppearsInManualResults() {
        var downloads = 0
        val resolver = OnlineLyricsResolver { url, _ -> when {
            url.contains("music.163.com/api/search") -> searchResponse(song(1, album = "EP (Korean Ver.)"))
            url.contains("song/lyric") -> { downloads++; lyricResponse }
            else -> lrclib().replace("Album", "EP (한국어 버전)")
        } }
        val japanese = request.copy(album = "EP (Japanese Ver.)")
        assertNull(resolver.loadForTrack(japanese))
        assertTrue(resolver.search(japanese).isEmpty())
        assertEquals(0, downloads)
    }


    @Test fun sourceAliasesAndOneBilingualFallbackPreserveTheOriginalIdentity() {
        var searches = 0
        var downloads = 0
        val resolver = OnlineLyricsResolver { url, _ -> when {
            url.contains("music.163.com/api/search") -> {
                searches++
                if (searches == 1) searchResponse() else searchResponse(song(1, title = "Spring Day").apply {
                    put("alias", JSONArray().put("봄날"))
                    put("artists", JSONArray().put(JSONObject().put("name", "IU").put("alias", JSONArray().put("아이유"))))
                })
            }
            url.contains("song/lyric") -> { downloads++; lyricResponse }
            else -> error("Confirmed match needs no fallback provider")
        } }
        assertNotNull(resolver.loadForTrack(request.copy(title = "봄날 (Spring Day)", artist = "아이유 (IU)")))
        assertEquals(2, searches)
        assertEquals(1, downloads)
    }

    @Test fun bilingualSearchFallbackIsBoundedWhenThereAreNoMatches() {
        var searches = 0
        val resolver = OnlineLyricsResolver { url, _ ->
            searches++
            if (url.contains("music.163.com")) searchResponse() else "[]"
        }
        assertNull(resolver.loadForTrack(request.copy(title = "봄날 (Spring Day)", artist = "아이유 (IU)")))
        assertEquals(4, searches)
    }

}
