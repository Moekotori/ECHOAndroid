package app.echo.android.data

import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.PlaylistExportFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistFileRoundTripTest {
    @Test fun referenceFormatsPreserveOrderAndEscapedLocations() {
        val tracks = listOf(
            M3uExportTrack("夜曲 & <现场>", "歌手", 61_200, "Music/夜曲 & #1 %.flac", "现场"),
            M3uExportTrack("Second", "Artist", 120_000, "content://media/track%202"),
        )
        val formats = PlaylistExportFormat.entries - setOf(PlaylistExportFormat.Csv, PlaylistExportFormat.Text)
        formats.forEach { format ->
            val entries = PlaylistFileCodec.parse(PlaylistFileCodec.write("A & B", tracks, format), "test.${format.extension}")
            assertEquals(format.name, tracks.map { it.location }, entries.map { it.location })
        }
    }

    @Test fun csvAndTxtResolveMetadataWithoutConfusingTitlesOrArtists() {
        val tracks = listOf(
            EchoTrack("a", "content://a", "'=A, \"B\" - C", "First - Artist"),
            EchoTrack("b", "content://b", "'=A, \"B\" - C", "Second"),
        )
        for (format in listOf(PlaylistExportFormat.Csv, PlaylistExportFormat.Text)) {
            val entries = PlaylistFileCodec.parse(exportPlaylistText(tracks, format), "test.${format.extension}")
            val matcher = PlaylistImportMatcher(entries)
            matcher.add(tracks.reversed().map { M3uMatchRow(it.id, it.title, it.artist, null, it.uri) })
            assertEquals(format.name, listOf("a", "b"), matcher.preview().trackIds)
        }
        val csv = "\"Track Name\",\"Artist Name(s)\"\r\n\"Line 1\nLine 2\",\"A, B\"\r\n"
        val entry = PlaylistFileCodec.parse(csv, "library.csv").single()
        assertEquals("Line 1\nLine 2", entry.title)
        assertEquals("A, B", entry.artist)
    }

    @Test fun xspfHandlesNamespacesAndEncodedRelativePaths() {
        val input = """
            <p:playlist xmlns:p="http://xspf.org/ns/0/" xml:base="file:///C:/Music/">
              <p:trackList><p:track>
                <p:location>Some%20Song%23.flac</p:location>
                <p:title>Some Song</p:title><p:creator>Artist</p:creator><p:duration>61200</p:duration>
              </p:track></p:trackList>
            </p:playlist>
        """.trimIndent()
        val entry = PlaylistFileCodec.parse(input).single()
        assertEquals("Music/Some Song#.flac", normalizeM3uLocation(entry.location))
        assertEquals("Artist", entry.artist)
        assertEquals(61, entry.durationSeconds)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsXmlExternalEntities() {
        PlaylistFileCodec.parse("""
            <!DOCTYPE ASX [<!ENTITY external SYSTEM "file:///secret">]>
            <ASX><ENTRY><TITLE>&external;</TITLE><REF HREF="track.mp3"/></ENTRY></ASX>
        """.trimIndent())
    }
}
