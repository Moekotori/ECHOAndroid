package app.echo.android.model.connect

/** Portable metadata only. No file paths, URIs, credentials or audio cross this contract. */
data class EchoSyncTrackRef(val title: String, val artist: String, val album: String?, val durationMs: Long)
data class EchoSyncCollection(val key: String, val name: String, val trackCount: Int, val favorites: Boolean = false)
data class EchoSyncBatch(val collection: EchoSyncCollection, val tracks: List<EchoSyncTrackRef>)
data class EchoSyncBatchResult(val matched: Int = 0, val skipped: Int = 0)
data class EchoSyncSelection(val collection: EchoSyncCollection, val fromPc: Boolean)
data class EchoSyncPlan(val selections: List<EchoSyncSelection>, val batches: List<Pair<Boolean, EchoSyncBatch>>,
    val matched: Int, val skipped: Int)
class EchoSyncUnsupportedException : Exception()
class EchoSyncPairingRequiredException : Exception()
