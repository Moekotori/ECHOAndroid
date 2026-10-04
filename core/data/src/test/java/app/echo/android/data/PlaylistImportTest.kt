package app.echo.android.data

import app.echo.android.model.library.PlaylistImportLimits
import org.junit.Assert.*
import org.junit.Test

class PlaylistImportTest {
    @Test fun plsUsesNumberedOrderAndPreservesMetadata() {
        val entries = PlaylistFileCodec.parse("""
            [playlist]
            Title2=Artist - Second
            File2=C:\Music\second.flac
            Length2=-1
            file1=file:///C:/Music/First%20Song+A.flac
            Title1=Artist - First
            Length1=61
            NumberOfEntries=2
            Version=2
        """.trimIndent())
        assertEquals(listOf("Music/First Song+A.flac", "Music/second.flac"), entries.map { it.location })
        assertEquals(61, entries[0].durationSeconds)
        assertNull(entries[1].durationSeconds)
        assertEquals("Artist - Second", entries[1].title)
    }

    @Test fun streamedMatchingKeepsOrderCountsDuplicatesAndRejectsAmbiguity() {
        val entries = PlaylistFileCodec.parse("""
            #EXTM3U
            Music/one.flac
            one.flac
            same.flac
            missing.flac
            content://media/unique
        """.trimIndent())
        val matcher = PlaylistImportMatcher(entries)
        matcher.add(listOf(
            M3uMatchRow("one", "One", "Artist", "Music/", "content://one", "one.flac"),
            M3uMatchRow("a", "Same", "Artist A", "Music/A/", "content://a", "same.flac"),
        ))
        matcher.add(listOf(
            M3uMatchRow("b", "Same", "Artist B", "Music/B/", "content://b", "same.flac"),
            M3uMatchRow("unique", "Last", "Artist", null, "content://media/unique", "last.flac"),
        ))
        val preview = matcher.preview()
        assertEquals(listOf("one", "unique"), preview.trackIds)
        assertEquals(5, preview.totalEntries)
        assertEquals(1, preview.duplicateEntries)
        assertEquals(1, preview.unmatchedEntries)
        assertEquals(1, preview.ambiguousEntries)
        assertEquals(listOf("same.flac", "missing.flac"), preview.unresolvedNames)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOversizedEntrySetsBeforeMatching() {
        PlaylistFileCodec.parse((1..PlaylistImportLimits.MaxEntries + 1).joinToString("\n") { "Music/$it.flac" })
    }

    @Test fun metadataOnlyDisambiguatesCandidatesAtTheRequestedLocation() {
        val matcher = PlaylistImportMatcher(PlaylistFileCodec.parse("""
            #EXTM3U
            #EXTINF:100,Right Artist - Right Song
            same.flac
            #EXTINF:100,Unrelated Artist - Unrelated Song
            same.flac
        """.trimIndent()))
        matcher.add(listOf(
            M3uMatchRow("wrong", "Other", "Other", "A/", "content://wrong", "same.flac"),
            M3uMatchRow("right", "Right Song", "Right Artist", "B/", "content://right", "same.flac"),
            M3uMatchRow("unrelated", "Unrelated Song", "Unrelated Artist", "C/", "content://unrelated", "different.flac"),
        ))
        val preview = matcher.preview()
        assertEquals(listOf("right"), preview.trackIds)
        assertEquals(1, preview.ambiguousEntries)
    }
}
