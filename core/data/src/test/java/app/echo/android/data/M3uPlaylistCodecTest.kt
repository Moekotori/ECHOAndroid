package app.echo.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uPlaylistCodecTest {
    @Test
    fun parsesExtendedEntriesAndMatchesRelativePaths() {
        val text = """
            #EXTM3U
            #EXTINF:123,Artist - Song One
            Music/Album/song one.flac
            #EXTINF:90,Other
            C:\Music\Album\other.mp3
        """.trimIndent()
        val entries = M3uPlaylistCodec.parse(text)
        assertEquals(2, entries.size)
        assertEquals("Music/Album/song one.flac", entries[0].location)
        assertEquals("Artist - Song One", entries[0].title)
        assertEquals(123, entries[0].durationSeconds)
        assertEquals("Music/Album/other.mp3", entries[1].location)

        val rows = listOf(
            M3uMatchRow("t1", "Song One", "Artist", "Music/Album/song one.flac", "content://1"),
            M3uMatchRow("t2", "Other", "Band", "Music/Album/other.mp3", "content://2"),
        )
        assertEquals("t1", M3uPlaylistCodec.matchTrackId(entries[0], rows))
        assertEquals("t2", M3uPlaylistCodec.matchTrackId(entries[1], rows))
    }

    @Test
    fun writeRoundTripKeepsOrder() {
        val body = M3uPlaylistCodec.write(
            listOf(
                M3uExportTrack("Song", "Artist", 61_000, "Music/a.flac"),
                M3uExportTrack("Next", "Artist", 0, "Music/b.mp3"),
            ),
        )
        assertTrue(body.startsWith("#EXTM3U"))
        val parsed = M3uPlaylistCodec.parse(body)
        assertEquals(listOf("Music/a.flac", "Music/b.mp3"), parsed.map { it.location })
    }
}
