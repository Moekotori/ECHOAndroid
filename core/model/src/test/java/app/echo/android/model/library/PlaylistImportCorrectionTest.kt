package app.echo.android.model.library

import org.junit.Assert.*
import org.junit.Test

class PlaylistImportCorrectionTest {
    @Test fun correctingMissingRowsKeepsOriginalOrderAndUpdatesCounts() {
        val a = PlaylistImportCandidate("a", "A", "Artist", "a.mp3")
        val b = a.copy(id = "b", title = "B")
        val c = a.copy(id = "c", title = "C")
        val preview = playlistImportPreview(listOf(
            PlaylistImportEntry(0, "A", PlaylistImportMatch.Missing),
            PlaylistImportEntry(1, "B", PlaylistImportMatch.Matched, b),
            PlaylistImportEntry(2, "C", PlaylistImportMatch.Ambiguous, candidates = listOf(a, c))))
        val fixed = preview.corrected(2, c).corrected(0, a)
        assertEquals(listOf("a", "b", "c"), fixed.trackIds)
        assertEquals(0, fixed.unmatchedEntries + fixed.ambiguousEntries)
        val duplicate = fixed.corrected(2, a)
        assertEquals(listOf("a", "b"), duplicate.trackIds)
        assertEquals(1, duplicate.duplicateEntries)
        assertEquals(1, fixed.corrected(1, null).skippedEntries)
        assertEquals(3, fixed.totalEntries)
    }
}
