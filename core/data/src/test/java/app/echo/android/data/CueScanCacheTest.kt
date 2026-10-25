package app.echo.android.data

import app.echo.android.model.library.CueSheet
import app.echo.android.model.library.CueSheetTrack
import app.echo.android.model.library.EchoTrack
import org.junit.Assert.*
import org.junit.Test

class CueScanCacheTest {
    private fun source(id: String = "file") = EchoTrack(id, "content://audio/$id", "Album", "Artist", durationMs = 120_000)
        .toLibraryTrackEntity().copy(fileName = "$id.flac", relativePath = "Music/Album", sizeBytes = 1000, dateModifiedSeconds = 10).withFingerprint()
    private val sheet = CueSheet(tracks = listOf(CueSheetTrack(1, "First", startMs = 0), CueSheetTrack(2, "Second", startMs = 60_000)))
    private fun snapshot(rows: List<LibraryTrackEntity>) = rows.associate { row -> row.id to row.cueSourceFacts().copy(fingerprint = row.fingerprint) }

    @Test fun successfulCacheRequiresBothThePhysicalSourceAndPublishedOutputsToMatch() {
        val source = source()
        val rows = source.splitByCue(sheet)
        val existing = snapshot(rows)
        val cache = CueScanCache()
        cache.remember(source.cueSourceFacts(), sheet, rows)
        assertEquals(rows.map { it.id }, cache.reuse(source.cueSourceFacts(), sheet, existing))
        assertNull(cache.reuse(source.cueSourceFacts().copy(sizeBytes = 1001), sheet, existing))
        assertNull(cache.reuse(source.cueSourceFacts().copy(dateModifiedSeconds = 11), sheet, existing))
        assertNull(cache.reuse(source.cueSourceFacts(), sheet.copy(album = "Changed"), existing))
        assertNull(cache.reuse(source.cueSourceFacts(), null, existing))
        assertNull(cache.reuse(source.cueSourceFacts(), sheet, existing - rows.last().id))
        assertNull(cache.reuse(source.cueSourceFacts(), sheet, existing, uncertain = true))
        assertNull(cache.reuse(source.cueSourceFacts().copy(sizeBytes = 0), sheet, existing))
    }

    @Test fun oneFailedFileDoesNotInvalidateSuccessfulFilesInTheSameScan() {
        val good = source("good")
        val failed = source("failed")
        val cache = CueScanCache()
        cache.remember(good.cueSourceFacts(), null, listOf(good))
        cache.remember(failed.cueSourceFacts(), null, listOf(failed))
        val existing = snapshot(listOf(good, failed)).toMutableMap()
        existing[failed.id] = existing.getValue(failed.id).copy(fingerprint = LibraryScanPolicy.PendingDocumentMetadataFingerprint)
        assertEquals(listOf(good.id), cache.reuse(good.cueSourceFacts(), null, existing))
        assertNull(cache.reuse(failed.cueSourceFacts(), null, existing))
    }

    @Test fun cacheSurvivesRestartAndCorruptionOnlyCausesAMiss() {
        val file = java.io.File.createTempFile("echo-cue-cache-", ".json")
        try {
            file.delete()
            val source = source()
            val rows = source.splitByCue(sheet)
            val cache = CueScanCache(file)
            cache.remember(source.cueSourceFacts(), sheet, rows)
            cache.flush()
            assertEquals(rows.map { it.id }, CueScanCache(file).reuse(source.cueSourceFacts(), sheet, snapshot(rows)))
            file.writeText("broken cache")
            assertNull(CueScanCache(file).reuse(source.cueSourceFacts(), sheet, snapshot(rows)))
        } finally { file.delete() }
    }

    @Test fun evictionsAndUncommittedOutputsCannotAuthorizeReuse() {
        val cache = CueScanCache(capacity = 1)
        val a = source("a"); val b = source("b")
        cache.remember(a.cueSourceFacts(), null, listOf(a))
        assertNull(cache.reuse(a.cueSourceFacts(), null, emptyMap()))
        cache.remember(b.cueSourceFacts(), null, listOf(b))
        assertNull(cache.reuse(a.cueSourceFacts(), null, snapshot(listOf(a, b))))
        assertEquals(listOf(b.id), cache.reuse(b.cueSourceFacts(), null, snapshot(listOf(a, b))))
    }
}
