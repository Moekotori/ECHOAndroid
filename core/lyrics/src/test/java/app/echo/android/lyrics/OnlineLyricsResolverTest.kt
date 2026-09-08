package app.echo.android.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineLyricsResolverTest {
    @Test
    fun loadsNeteaseLyricsWhenCandidateMatches() {
        val resolver = OnlineLyricsResolver { url, _ ->
            when {
                url.contains("music.163.com/api/search") -> """
                    {
                      "result": {
                        "songs": [
                          {
                            "id": 42,
                            "name": "LiFE Garden",
                            "duration": 184000,
                            "artists": [{"name": "Yooh"}],
                            "album": {"name": "Album"}
                          }
                        ]
                      },
                      "code": 200
                    }
                """.trimIndent()

                url.contains("music.163.com/api/song/lyric") -> """
                    {
                      "lrc": {
                        "lyric": "[00:01.00]First line\n[00:02.00]Second line"
                      },
                      "code": 200
                    }
                """.trimIndent()

                else -> null
            }
        }

        val lyrics = resolver.loadForTrack(
            EchoLyricsSearchRequest(
                title = "LiFE Garden",
                artist = "Yooh",
                album = "Album",
                durationMs = 184_000L,
            ),
        )

        requireNotNull(lyrics)
        assertEquals("NetEase Cloud Music", lyrics.sourceLabel)
        assertEquals("First line", lyrics.lines.first().text)
        assertTrue(lyrics.isSynced)
    }

    @Test
    fun fallsBackToLrclibWhenNeteaseDoesNotMatch() {
        val resolver = OnlineLyricsResolver { url, _ ->
            when {
                url.contains("music.163.com/api/search") -> """{"result":{"songs":[]},"code":200}"""
                url.contains("lrclib.net/api/search") -> """
                    [
                      {
                        "id": 7,
                        "trackName": "LiFE Garden",
                        "artistName": "Yooh",
                        "albumName": "Album",
                        "duration": 184,
                        "plainLyrics": "Plain first\nPlain second",
                        "syncedLyrics": null
                      }
                    ]
                """.trimIndent()

                else -> null
            }
        }

        val lyrics = resolver.loadForTrack(
            EchoLyricsSearchRequest(
                title = "LiFE Garden",
                artist = "Yooh",
                album = "Album",
                durationMs = 184_000L,
            ),
        )

        requireNotNull(lyrics)
        assertEquals("LRCLIB", lyrics.sourceLabel)
        assertEquals("Plain first", lyrics.lines.first().text)
        assertEquals(false, lyrics.isSynced)
    }
    @Test fun prefersWordsAndMergesAuxiliaryTracks() {
        val resolver = OnlineLyricsResolver { _, _ -> """{
            "lrc":{"lyric":"[00:01.00]Hello"},
            "yrc":{"lyric":"[1000,2000](1000,1000,0)Hello\n[4000,1000](4000,1000,0)Again"},
            "tlyric":{"lyric":"[00:01.00]你好"},
            "romalrc":{"lyric":"[00:01.00]ni hao"}
        }""" }
        val line = requireNotNull(resolver.loadFromNeteaseSongId(42)).lines.first()
        assertEquals(1, line.words.size)
        assertEquals("你好", line.translation)
        assertEquals("ni hao", line.romanization)
    }

    @Test fun rejectsSameTitleByDifferentArtistEvenWithoutDuration() {
        var fetchedLyrics = false
        val resolver = OnlineLyricsResolver { url, _ -> when {
            url.contains("api/search") -> """{"result":{"songs":[{"id":42,"name":"Hello","artists":[{"name":"Wrong artist"}]}]}}"""
            url.contains("song/lyric") -> { fetchedLyrics = true; null }
            else -> "[]"
        } }
        assertEquals(null, resolver.loadForTrack(EchoLyricsSearchRequest("Hello", "Correct artist")))
        assertEquals(false, fetchedLyrics)
    }
}
