package app.echo.android.data

import app.echo.android.model.library.EchoTrack
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RemoteCueScanTest {
    @Test fun unchangedDeferredCueSourcesNeedNoTagReadDatabaseLoadWriteOrMerge() = runBlocking {
        val source = track("album.flac")
        val rows = source.splitByCue(sheet, "album.flac")
        val existing = rows.associate { it.id to fingerprint(it) }
        val cache = CueScanCache()
        val localized = sheet.forDirectoryAudio(listOf("album.flac"), "album.cue").getValue("album.flac")
        cache.remember(source.cueSourceFacts(), localized, rows)
        val scan = RemoteCueScan(cache = cache)
        scan.collect(source); scan.defer(source); scan.collect(track("album.cue"))
        val seen = mutableSetOf<String>()
        var sidecars = 0
        scan.apply(existing, seen, readCue = { _, _ -> sidecars++; sheet },
            readEmbedded = { error("No embedded read") }, load = { error("No database load") },
            write = { error("No writes") }, merge = { error("No merge") }, readSource = { error("No tag read") })
        assertEquals(1, sidecars) // Independently changing external CUE content is still checked.
        assertEquals(existing.keys, seen)
        assertFalse(scan.incomplete)
    }

    @Test fun changedCueMaterializesOnlyItsSourceWithoutCreatingATemporaryWholeFile() = runBlocking {
        val source = track("album.flac")
        val oldRows = source.splitByCue(sheet, "album.flac")
        val cache = CueScanCache()
        cache.remember(source.cueSourceFacts(), sheet.forDirectoryAudio(listOf("album.flac"), "album.cue").getValue("album.flac"), oldRows)
        val scan = RemoteCueScan(cache = cache)
        scan.collect(source); scan.defer(source); scan.collect(track("album.cue"))
        var reads = 0
        var writes = 0
        val changed = sheet.copy(album = "New album")
        scan.apply(oldRows.associate { it.id to fingerprint(it) }, mutableSetOf(), readCue = { _, _ -> changed },
            readEmbedded = { error("Sidecar wins") }, load = { error("Deferred source is materialized directly") },
            write = { rows -> writes++; assertTrue(rows.all { it.id.contains("#cue:") && it.album == "New album" }) },
            merge = { error("There is no temporary base row to merge") }, readSource = { reads++; it })
        assertEquals(1, reads)
        assertEquals(1, writes)
    }

    private fun track(name: String) = EchoTrack("dav:$name", "https://dav.example/music/$name", name, "Artist", durationMs = 120_000)
        .toLibraryTrackEntity().copy(fileName = name, relativePath = "album", sizeBytes = 1000, dateModifiedSeconds = 10)
    private val sheet = CueSheetParser.parseText("FILE \"album.wav\" WAVE\nTRACK 01 AUDIO\nTITLE \"One\"\nINDEX 01 00:00:00\nTRACK 02 AUDIO\nTITLE \"Two\"\nINDEX 01 01:00:00")!!

    private fun fingerprint(track: LibraryTrackEntity) = TrackFingerprint(track.id, track.contentUri, track.sampleRateHz,
        track.fingerprint, track.sizeBytes, track.dateModifiedSeconds, track.relativePath, track.durationMs, track.fileName)

    @Test fun externalSidecarsExpandAfterAudioAndTransferTheWholeFileIdentity() = runBlocking {
        val source = track("album.flac")
        val scan = RemoteCueScan()
        assertFalse(scan.collect(source))
        assertTrue(scan.collect(track("album.cue"))) // Listing order does not need to put the sidecar first.
        val store = linkedMapOf(source.id to source)
        val seen = mutableSetOf(source.id)
        val aliases = ArrayList<Pair<String, String>>()
        scan.apply(mapOf(source.id to fingerprint(source)), seen,
            readCue = { _, reference -> assertTrue(reference("album.wav")); sheet },
            readEmbedded = { error("Sidecar must win") },
            load = { ids -> ids.mapNotNull(store::get) },
            write = { rows -> rows.forEach { store[it.id] = it } },
            merge = { rows -> aliases += rows; rows.forEach { store.remove(it.first) } },
        )
        assertFalse(scan.incomplete)
        assertEquals(listOf("One", "Two"), store.values.map { it.title })
        assertEquals(listOf(0L, 60_000L), store.values.map { it.clipStartMs })
        assertEquals(listOf(source.id to store.values.first().id), aliases)
        assertFalse(source.id in seen)
    }

    @Test fun unreadableSidecarRetainsOldMovementsAndBlocksDestructiveCleanup() = runBlocking {
        val source = track("album.flac")
        val old = source.splitByCue(sheet, "album.flac")
        val store = (old + source).associateBy { it.id }.toMutableMap()
        val existing = old.associate { it.id to fingerprint(it) }
        val scan = RemoteCueScan()
        scan.collect(source); scan.collect(track("album.cue"))
        val seen = mutableSetOf(source.id)
        scan.apply(existing, seen,
            readCue = { _, _ -> throw IOException("offline") },
            readEmbedded = { error("Do not fall through after failed sidecar") },
            load = { ids -> ids.mapNotNull(store::get) },
            write = { error("Failure cannot overwrite old metadata") },
            merge = { rows -> rows.forEach { store.remove(it.first) } },
        )
        assertTrue(scan.incomplete)
        assertTrue(seen.containsAll(existing.keys))
        assertEquals(existing.keys, store.keys)
    }

    @Test fun removingACueRestoresWholeFileAndStableFilesAvoidRepeatedNetworkReads() = runBlocking {
        val source = track("album.flac").withFingerprint()
        val old = source.splitByCue(sheet, "album.flac")
        val aliases = ArrayList<Pair<String, String>>()
        val scan = RemoteCueScan()
        scan.collect(source)
        scan.apply(old.associate { it.id to fingerprint(it) }, mutableSetOf(source.id),
            readCue = { _, _ -> error("No sidecar") }, readEmbedded = { null },
            load = { listOf(source) }, write = { assertEquals(listOf(source), it) }, merge = { aliases += it },
        )
        assertEquals(old.map { it.id to source.id }, aliases)
        val stable = RemoteCueScan(refreshEmbedded = false)
        stable.collect(source)
        stable.apply(mapOf(source.id to fingerprint(source)), mutableSetOf(source.id),
            readCue = { _, _ -> error("No sidecar") }, readEmbedded = { error("Unchanged file needs no network read") },
            load = { listOf(source) }, write = { error("Unchanged") }, merge = { error("Unchanged") },
        )
        assertFalse(stable.incomplete)
    }

    @Test fun caseDistinctRemoteDirectoriesCannotBorrowEachOthersSidecars() = runBlocking {
        val upper = track("album.flac").copy(id = "upper", relativePath = "Disc")
        val lower = upper.copy(id = "lower", relativePath = "disc", contentUri = "https://dav.example/disc/album.flac")
        val scan = RemoteCueScan()
        scan.collect(upper); scan.collect(lower)
        scan.collect(track("album.cue").copy(relativePath = "Disc"))
        val written = ArrayList<LibraryTrackEntity>()
        val store = listOf(upper, lower).associateBy { it.id }
        scan.apply(emptyMap(), mutableSetOf(), readCue = { _, _ -> sheet }, readEmbedded = { null },
            load = { ids -> ids.mapNotNull(store::get) }, write = { written += it }, merge = {},
        )
        assertEquals(setOf("upper#cue:1", "upper#cue:2"), written.map { it.id }.toSet())
        assertFalse(scan.incomplete)
    }
}
