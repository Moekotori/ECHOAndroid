package app.echo.android

import app.echo.android.connect.EchoLibrarySyncSession
import app.echo.android.data.*
import app.echo.android.model.connect.*
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext

/** Manual additive sync. Plans hold at most 10,000 small metadata references and die with the sheet. */
internal class LibrarySyncController(private val repository: EchoLibraryRepository) {
    private val operation = Mutex()
    suspend fun localCollections() = repository.syncCollections()
    suspend fun preview(session: EchoLibrarySyncSession, selections: List<EchoSyncSelection>): EchoSyncPlan = operation.withLock {
        require(selections.sumOf { it.collection.trackCount.toLong() } <= 10000)
        val batches = ArrayList<Pair<Boolean, EchoSyncBatch>>()
        var matched = 0; var skipped = 0
        var referenceCount = 0
        for (selection in selections) {
            val collection = selection.collection
            // Empty playlists also travel; offsets count source rows, including unsupported entries.
            for (offset in 0 until collection.trackCount.coerceAtLeast(1) step 200) {
                coroutineContext.ensureActive()
                val batch = if (selection.fromPc) session.exportBatch(collection, offset)
                    else repository.syncExportBatch(collection, offset)
                referenceCount += batch.tracks.size
                require(referenceCount <= 10000)
                val result = if (selection.fromPc) repository.syncImportBatch(batch, true) else session.importBatch(batch, true)
                matched += result.matched
                skipped += result.skipped + ((collection.trackCount - offset).coerceIn(0, 200) - batch.tracks.size).coerceAtLeast(0)
                batches += selection.fromPc to batch
            }
        }
        EchoSyncPlan(selections, batches, matched, skipped)
    }
    suspend fun apply(session: EchoLibrarySyncSession, plan: EchoSyncPlan): EchoSyncBatchResult = operation.withLock {
        var matched = 0
        var skipped = (plan.selections.sumOf { it.collection.trackCount } - plan.batches.sumOf { it.second.tracks.size }).coerceAtLeast(0)
        for ((fromPc, batch) in plan.batches) {
            coroutineContext.ensureActive()
            val result = if (fromPc) repository.syncImportBatch(batch, false) else session.importBatch(batch, false)
            matched += result.matched; skipped += result.skipped
        }
        EchoSyncBatchResult(matched, skipped)
    }
}
