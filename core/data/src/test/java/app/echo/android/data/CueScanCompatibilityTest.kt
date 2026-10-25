package app.echo.android.data

import app.echo.android.model.library.CueSheet
import app.echo.android.model.library.CueSheetTrack
import org.junit.Assert.*
import org.junit.Test

class CueScanCompatibilityTest {
    @Test
    fun caseDistinctSourcesKeepSeparateParserTimelinesAndCatalogKeys() {
        val sheet = CueSheetParser.parseText("FILE \"A.flac\" WAVE\nTRACK 01 AUDIO\nINDEX 01 00:00:00\nFILE \"a.flac\" WAVE\nTRACK 02 AUDIO\nINDEX 01 00:00:00")!!
        val catalog = CueDirectoryCatalog(listOf("A.flac", "a.flac"))
        catalog.add(sheet, "album.cue")
        assertFalse(catalog.uncertain)
        assertEquals(listOf(1), catalog.sheets().getValue("A.flac").tracks.map { it.number })
        assertEquals(listOf(2), catalog.sheets().getValue("a.flac").tracks.map { it.number })
        assertNotEquals(cueFolderKey("Music/Disc", "A.flac"), cueFolderKey("Music/Disc", "a.flac"))
        assertNotEquals(cueFolderKey("Music/Disc", "a.flac"), cueFolderKey("Music/disc", "a.flac"))
        val uncertain = MediaStoreCueCatalog.CueSheetCatalog(emptyMap(), emptyList(), setOf("Music/Disc"))
        assertTrue(uncertain.isUncertain("Music/Disc/Sub/"))
        assertFalse(uncertain.isUncertain("Music/Other/"))
    }

    @Test
    fun legacyCharsetSelectionUsesActualSourcesAndKeepsPrimaryOnTies() {
        for ((encoding, title) in listOf("windows-1251" to "Привет", "windows-1250" to "Příliš")) {
            val filename = "$title.flac"
            val cue = "FILE \"$filename\" WAVE\nTRACK 01 AUDIO\nTITLE \"$title\"\nINDEX 01 00:00:00"
            val bytes = cue.toByteArray(java.nio.charset.Charset.forName(encoding))
            val parsed = CueSheetParser.parse(bytes) { it == filename }!!
            assertEquals(filename, parsed.fileName)
            assertEquals(title, parsed.tracks.single().title)
        }
        val bytes = text.toByteArray()
        assertEquals(CueSheetParser.parse(bytes), CueSheetParser.parse(bytes) { false })
        assertNull(cueReference("../outside.flac"))
        assertNull(cueReference("C:\\outside.flac"))
    }

    private val text = "FILE \"album.flac\" WAVE\nTRACK 01 AUDIO\nTITLE \"夜空 🎵\"\nINDEX 01 00:00:00\n"
    private fun sheet(title: String = "First", source: String = "album.wav") = CueSheet(
        fileName = source, tracks = listOf(CueSheetTrack(1, title, startMs = 0, fileName = source)),
    )

    @Test
    fun unicodeCueDocumentsKeepAllCodePointsWithAndWithoutBom() {
        for (encoding in listOf("UTF-8", "UTF-16LE", "UTF-16BE", "UTF-32LE", "UTF-32BE")) {
            val bytes = text.toByteArray(java.nio.charset.Charset.forName(encoding))
            val bom = when (encoding) {
                "UTF-8" -> byteArrayOf(-17, -69, -65)
                "UTF-16LE" -> byteArrayOf(-1, -2)
                "UTF-16BE" -> byteArrayOf(-2, -1)
                "UTF-32LE" -> byteArrayOf(-1, -2, 0, 0)
                else -> byteArrayOf(0, 0, -2, -1)
            }
            for (document in listOf(bytes, bom + bytes)) {
                assertEquals(encoding, "夜空 🎵", CueSheetParser.parse(document)?.tracks?.single()?.title)
            }
        }
    }

    @Test
    fun malformedUnicodeCannotAuthorizeAPartialReplacement() {
        for (encoding in listOf("UTF-8", "UTF-16LE", "UTF-16BE", "UTF-32LE", "UTF-32BE")) {
            val charset = java.nio.charset.Charset.forName(encoding)
            // A valid first track followed by NUL used to be silently accepted as a whole sheet.
            assertNull(encoding, CueSheetParser.parse((text + "\u0000TRACK 02 AUDIO").toByteArray(charset)))
        }
        val utf16 = byteArrayOf(-1, -2) + text.toByteArray(Charsets.UTF_16LE)
        assertNull(CueSheetParser.parse(utf16 + byteArrayOf(65)))
        val utf32 = byteArrayOf(-1, -2, 0, 0) + text.toByteArray(java.nio.charset.Charset.forName("UTF-32LE"))
        assertNull(CueSheetParser.parse(utf32 + byteArrayOf(0)))
        assertNull(CueSheetParser.parse(utf32 + byteArrayOf(0, -40, 0, 0))) // surrogate
        assertNull(CueSheetParser.parse(byteArrayOf(-17, -69, -65) + text.toByteArray() + byteArrayOf(-1)))
    }

    @Test
    fun conflictingSidecarsStayUnresolvedRegardlessOfEnumerationOrder() {
        for (titles in listOf(listOf("First", "Different"), listOf("Different", "First"))) {
            val catalog = CueDirectoryCatalog(listOf("album.flac"))
            titles.forEach { catalog.add(sheet(it), "$it.cue") }
            catalog.add(sheet(titles.first()), "copy.cue")
            assertTrue(catalog.uncertain)
            assertTrue(catalog.sheets().isEmpty())
        }
        val identical = CueDirectoryCatalog(listOf("album.flac"))
        repeat(2) { identical.add(sheet(), "copy$it.cue") }
        assertFalse(identical.uncertain)
        assertEquals(setOf("album.flac"), identical.sheets().keys)
    }

    @Test
    fun missingAndAmbiguousRipSourcesPreserveUncertaintyWithoutBlockingValidSources() {
        val ambiguous = CueDirectoryCatalog(listOf("album.flac", "album.ape", "other.flac"))
        ambiguous.add(sheet(), "album.cue")
        ambiguous.add(sheet(source = "other.wav"), "other.cue")
        assertTrue(ambiguous.uncertain)
        assertEquals(setOf("other.flac"), ambiguous.sheets().keys)
        val missing = CueDirectoryCatalog(emptyList())
        assertFalse(missing.add(sheet(), "album.cue"))
        assertTrue(missing.uncertain)
        val partial = CueDirectoryCatalog(listOf("album.flac"))
        partial.add(sheet().let { it.copy(tracks = it.tracks + CueSheetTrack(2, "Missing", startMs = 0, fileName = "missing.wav")) }, "album.cue")
        assertTrue(partial.uncertain)
        assertEquals(setOf("album.flac"), partial.sheets().keys)
    }

    @Test
    fun flaAliasCanReplaceAWavRipReference() {
        val catalog = CueDirectoryCatalog(listOf("album.FLA"))
        catalog.add(sheet(), "album.cue")
        assertFalse(catalog.uncertain)
        assertEquals("album.FLA", catalog.sheets().values.single().tracks.single().fileName)
    }
}
