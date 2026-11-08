package app.echo.android.data

import app.echo.android.model.library.CueSheetPolicy
import org.junit.Assert.*
import org.junit.Test

class PcCueFormatCompatibilityTest {
    @Test fun dataOnlyIndex00UsesCanonicalCaseAndKeepsDistinctSourcesSeparate() {
        val text = "FILE 'album.wav' WAVE\nTRACK 01 AUDIO\nINDEX 01 0\nFILE 'album.FLAC' FLAC\nTRACK 02 MODE1/2352\nINDEX 00 1500"
        val index = CueAudioNameIndex(listOf("album.FLAC"))
        val sheet = CueSheetParser.parse(text.toByteArray(), CueSourceLookup(index::exactMatch))!!
        assertEquals(20_000L, sheet.tracks.single().endMs)
        assertEquals("album.FLAC", sheet.tracks.single().fileName)
        val distinct = CueAudioNameIndex(listOf("album.wav", "album.FLAC"))
        assertEquals(0L, CueSheetParser.parse(text.toByteArray(), CueSourceLookup(distinct::exactMatch))!!.tracks.single().endMs)
        val caseSensitive = CueAudioNameIndex(listOf("A.flac", "a.flac"))
        val separate = CueSheetParser.parse("FILE 'A.flac' FLAC\nTRACK 01 AUDIO\nINDEX 01 0\nFILE 'a.flac' FLAC\nTRACK 02 AUDIO\nINDEX 01 0".toByteArray(), CueSourceLookup(caseSensitive::exactMatch))!!
        assertEquals(2, separate.tracks.size)
    }
    @Test fun trackZeroSurvivesParsingCatalogAndClipIdentity() {
        val sheet = CueSheetParser.parseText("FILE 'album.flac' WAVE\nTRACK 00 AUDIO\nTITLE 'Hidden'\nINDEX 01 0\nTRACK 01 AUDIO\nINDEX 01 150")!!
        val catalog = CueDirectoryCatalog(listOf("album.flac"))
        catalog.add(sheet, "album.cue")
        val tracks = CueSheetPolicy.withEndTimes(catalog.sheets().getValue("album.flac").tracks, 10_000)
        assertEquals(listOf(0, 1), tracks.map { it.number })
        assertEquals(2000L, tracks.first().endMs)
        assertEquals("song#cue:0", CueSheetPolicy.cueTrackId("song", tracks.first().number))
        assertTrue(CueSheetPolicy.contentUri("content://song", 0).endsWith("echo-cue=0"))
    }

    @Test fun aliasResolutionBoundsAudioAndDataByTheActualSource() {
        val prefix = "FILE 'disc/album.wav' WAVE\nTRACK 01 AUDIO\nINDEX 01 0\nFILE 'disc/album.flac' FLAC\n"
        val audio = CueSheetParser.parse((prefix + "TRACK 02 AUDIO\nINDEX 01 1500").toByteArray()) { it == "disc/album.flac" }!!
        assertEquals(20_000L, audio.tracks.first().endMs)
        assertEquals(listOf("disc/album.flac", "disc/album.flac"), audio.tracks.map { it.fileName })
        val data = CueSheetParser.parse((prefix + "TRACK 02 MODEx/2xxx\nINDEX 00 1500\nINDEX 01 1575").toByteArray()) { it == "disc/album.flac" }!!
        assertEquals(20_000L, data.tracks.single().endMs)
        val separate = CueSheetParser.parse((prefix + "TRACK 02 AUDIO\nINDEX 01 1500").toByteArray()) { it in setOf("disc/album.wav", "disc/album.flac") }!!
        assertEquals(0L, separate.tracks.first().endMs)
        val unordered = "FILE 'album.wav' WAVE\nTRACK 01 AUDIO\nINDEX 01 1500\nFILE 'album.flac' FLAC\nTRACK 02 AUDIO\nINDEX 01 750"
        assertNull(CueSheetParser.parse(unordered.toByteArray()) { it == "album.flac" })
        assertEquals("album.wav", resolveCueSource("album.wav") { it in setOf("album.flac", "album.ape") })
    }
}
