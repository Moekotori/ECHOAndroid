package app.echo.android.data

import app.echo.android.model.library.CueSheet
import app.echo.android.model.library.CueSheetTrack
import app.echo.android.model.library.EchoTrack
import java.io.IOException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class CueScanRecoveryTest {
    private fun source() = EchoTrack("saf:file", "content://audio/file", "Original", "Original artist",
        album = "Original album", durationMs = 120_000L).toLibraryTrackEntity()
    private val movements = CueSheet(tracks = listOf(
        CueSheetTrack(1, "First", startMs = 0L), CueSheetTrack(2, "Second", startMs = 60_000L),
    ))

    @Test
    fun transientEmbeddedFailurePreservesOldIdsAndTheNextScanCanRetry() {
        val source = source()
        val oldTracks = source.splitByCue(movements)
        val oldIds = oldTracks.map { it.id }
        assertSame(CueExpansion.ReadFailed, source.resolveLocalCue(null, "file.flac") { throw IOException("temporarily unavailable") })
        val seen = CueScanState.preservedIds(source.id, baseExists = false, oldIds).toSet()
        assertTrue(LibraryScanPolicy.unseenIds(oldIds, seen).isEmpty())
        assertFalse(source.id in seen)
        assertNull(LibraryScanPolicy.rememberUnchangedCueIds(source.id, oldIds, hasCueSheet = false))
        val retried = source.resolveLocalCue(null, "file.flac") { movements } as CueExpansion.Success
        assertEquals(oldIds, retried.tracks.map { it.id })
        assertEquals(oldTracks.map { it.clipStartMs to it.clipEndMs }, retried.tracks.map { it.clipStartMs to it.clipEndMs })
    }

    @Test
    fun unreadableSidecarNeverFallsThroughToEmbeddedOrWholeFileReplacement() {
        var embeddedReads = 0
        val failed = source().resolveLocalCue(null, "file.flac", externalReadFailed = true) { embeddedReads++; null }
        assertSame(CueExpansion.ReadFailed, failed)
        assertEquals(0, embeddedReads)
        assertSame(CueExpansion.ReadFailed, source().resolveLocalCue(null, "file.flac") {
            FlacEmbeddedCueReader.readChecked("fLaC".toByteArray().inputStream())
        })
        assertTrue(source().resolveLocalCue(movements, null, externalReadFailed = false) { error("sidecar wins") } is CueExpansion.Success)
    }

    @Test
    fun removingWholeFileCueRestoresOriginalMetadataAndClearsProvenance() {
        val original = source()
        val cue = original.splitByCue(CueSheet(album = "Cue album", performer = "Cue artist", tracks = listOf(
            CueSheetTrack(1, "Cue title", startMs = 0L),
        ))).single().withScanMetadata(1L).withFingerprint()
        assertEquals(original.id, cue.id)
        assertTrue(CueScanState.isWholeFileCue(cue.fingerprint))
        assertNull(LibraryScanPolicy.rememberUnchangedCueIds(cue.id, emptyList(),
            hasCueSheet = CueScanState.isWholeFileCue(cue.fingerprint)))
        // The forced scan supplies freshly read original tags, rather than reusing the CUE row.
        val restored = (original.resolveLocalCue(null, "file.wav") { null } as CueExpansion.Success).tracks.single()
        assertEquals(listOf(original.title, original.artist, original.album), listOf(restored.title, restored.artist, restored.album))
        assertEquals(original.id, restored.id)
        assertFalse(CueScanState.isWholeFileCue(restored.withScanMetadata(2L).fingerprint))
        assertEquals(listOf(restored.id), LibraryScanPolicy.rememberUnchangedCueIds(restored.id, emptyList(), false))
    }

    @Test
    fun discoveryFailureProtectsUnknownDescendantsButNotUnrelatedDirectories() {
        val catalog = MediaStoreCueCatalog.CueSheetCatalog(emptyMap(), emptyList(), setOf("music/disc"))
        assertTrue(catalog.isUncertain("Music/Disc/Sub/"))
        assertFalse(catalog.isUncertain("Music/Other/"))
        assertTrue(catalog.copy(discoveryFailed = true).isUncertain("Music/Other/"))
    }

    @Test
    fun cancellationStillCancelsScanning() {
        assertThrows(CancellationException::class.java) {
            source().resolveLocalCue(null, "file.flac") { throw CancellationException("cancelled") }
        }
    }
}
