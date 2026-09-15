package app.echo.android.lyrics

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

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

    @Test fun strongMatchDownloadsLyricsOnce() {
        val urls = Collections.synchronizedList(mutableListOf<String>())
        val resolver = OnlineLyricsResolver { url, _ ->
            urls += url
            when {
                url.contains("music.163.com/api/search") -> searchResponse(song(1), song(2))
                url.contains("song/lyric") -> lyricResponse
                else -> "[]"
            }
        }
        assertEquals("NetEase", resolver.loadForTrack(request)?.lines?.first()?.text)
        assertEquals(1, urls.count { it.contains("music.163.com/api/search") })
        assertEquals(1, urls.count { it.contains("song/lyric") })
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
        val cancelled = java.util.concurrent.atomic.AtomicBoolean(false)
        val lyricDownloads = AtomicInteger(0)
        val resolver = OnlineLyricsResolver { url, _ ->
            cancelled.set(true)
            if (url.contains("song/lyric")) lyricDownloads.incrementAndGet()
            if (url.contains("music.163.com")) searchResponse(song(1)) else "[]"
        }
        try {
            resolver.loadForTrack(request) { if (cancelled.get()) throw CancellationException() }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertEquals(0, lyricDownloads.get())
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
                if (searches == 1) """{"code":200,"result":{}}""" else searchResponse(song(1, title = "Spring Day").apply {
                    put("alias", JSONArray().put("봄날"))
                    put("artists", JSONArray().put(JSONObject().put("name", "IU").put("alias", JSONArray().put("아이유"))))
                })
            }
            url.contains("song/lyric") -> { downloads++; lyricResponse }
            else -> "[]"
        } }
        assertNotNull(resolver.loadForTrack(request.copy(title = "봄날 (Spring Day)", artist = "아이유 (IU)")))
        assertEquals(2, searches)
        assertEquals(1, downloads)
    }

    @Test fun bilingualSearchFallbackIsBoundedWhenThereAreNoMatches() {
        val searches = AtomicInteger(0)
        val resolver = OnlineLyricsResolver { url, _ ->
            searches.incrementAndGet()
            if (url.contains("music.163.com")) searchResponse() else "[]"
        }
        assertNull(resolver.loadForTrack(request.copy(title = "봄날 (Spring Day)", artist = "아이유 (IU)")))
        assertEquals(4, searches.get())
    }

    @Test(timeout = 3000)
    fun lrclibSearchOverlapsNeteaseSearch() {
        val gate = CountDownLatch(1)
        val resolver = OnlineLyricsResolver { url, _ ->
            when {
                url.contains("music.163.com/api/search") -> {
                    assertTrue(gate.await(2, TimeUnit.SECONDS))
                    searchResponse()
                }
                url.contains("song/lyric") -> lyricResponse
                else -> {
                    gate.countDown()
                    lrclib()
                }
            }
        }
        assertEquals("LRCLIB", resolver.loadForTrack(request)?.lines?.first()?.text)
    }

    @Test(timeout = 3000)
    fun strongSyncedMatchDoesNotWaitForSlowLrclib() {
        val resolver = OnlineLyricsResolver { url, _ ->
            when {
                url.contains("music.163.com/api/search") -> searchResponse(song(1))
                url.contains("song/lyric") -> lyricResponse
                else -> {
                    Thread.sleep(2_500)
                    "[]"
                }
            }
        }
        val started = System.nanoTime()
        assertEquals("NetEase", resolver.loadForTrack(request)?.lines?.first()?.text)
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 1_000)
    }

    @Test fun labeledCoverAutoLoadsOriginalLyrics() {
        val resolver = OnlineLyricsResolver { url, _ ->
            when {
                url.contains("song/lyric") -> lyricResponse
                url.contains("music.163.com") && url.contains("Cover") -> searchResponse()
                url.contains("music.163.com") -> searchResponse(song(1))
                url.contains("artist_name") -> "[]"
                else -> lrclib()
            }
        }
        assertEquals(
            "NetEase",
            resolver.loadForTrack(request.copy(title = "Song (Cover)", artist = "Cover Artist"))
                ?.lines?.first()?.text,
        )
    }

    @Test fun unlabeledCoverIsManualOnlyAndCanBePickedFromTitleSearch() {
        val resolver = OnlineLyricsResolver { url, _ ->
            when {
                url.contains("song/lyric") -> lyricResponse
                url.contains("music.163.com") && url.contains("Cover") -> searchResponse()
                url.contains("music.163.com") -> searchResponse(song(1))
                url.contains("artist_name") -> "[]"
                else -> lrclib()
            }
        }
        val cover = request.copy(artist = "Cover Artist")
        assertNull(resolver.loadForTrack(cover))
        assertEquals(setOf("lrclib:7", "netease:1"), resolver.search(cover).map { it.id }.toSet())
    }

}
