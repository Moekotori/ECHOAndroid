package app.echo.android.data

import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import kotlin.coroutines.coroutineContext

/** Snapshot membership is not proof that the database transaction found both rows. */
internal suspend fun mergeScanAliases(
    aliases: Iterable<Pair<String, String>>,
    liveIds: MutableSet<String>,
    seenIds: MutableSet<String>,
    merge: suspend (String, String) -> Boolean,
) {
    val cueChildren = LibraryScanPolicy.cueChildrenByBase(liveIds)
    for ((oldId, targetId) in aliases) {
        coroutineContext.ensureActive()
        if (oldId == targetId || oldId !in liveIds) continue
        val target = targetId.takeIf { it in liveIds }
            ?: LibraryScanPolicy.duplicateMergeTarget(oldId, targetId, liveIds, cueChildren, seenIds)
        if (target == null || target !in liveIds) {
            seenIds.add(oldId)
            continue
        }
        if (target == oldId) continue
        if (merge(oldId, target)) liveIds.remove(oldId)
        // On failure retain the original until a later scan can resolve the destination again.
        seenIds.add(oldId)
        yield()
    }
}
