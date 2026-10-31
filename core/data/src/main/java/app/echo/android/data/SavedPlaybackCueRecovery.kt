package app.echo.android.data

import app.echo.android.model.library.CueSheetPolicy
import app.echo.android.model.playback.EchoTrackRef

/** Old queues stored CUE identities but omitted the physical playback range. */
internal suspend fun EchoSavedPlaybackSession.restorePlaybackSession(
    resume: EchoSavedPlaybackResume?,
    loadTracks: suspend (List<String>) -> List<LibraryTrackEntity>,
): EchoSavedPlaybackSession {
    // The resume belongs to the stored queue identity, before adding the missing ranges.
    val restored = withPlaybackResume(resume)
    val missingIds = restored.queue.asSequence().filter(EchoTrackRef::needsCueRange)
        .map { it.id }.distinct().toList()
    if (missingIds.isEmpty()) return restored

    val ranges = HashMap<String, LibraryTrackEntity>()
    for (ids in missingIds.chunked(256)) {
        for (row in loadTracks(ids)) {
            if (row.id in ids && row.clipStartMs >= 0L &&
                (row.clipStartMs > 0L || row.clipEndMs > 0L) &&
                (row.clipEndMs == 0L || row.clipEndMs > row.clipStartMs)) {
                ranges[row.id] = row
            }
        }
    }
    if (ranges.isEmpty()) return restored
    return restored.copy(queue = restored.queue.map { track ->
        val row = ranges[track.id]?.takeIf { track.needsCueRange() } ?: return@map track
        track.copy(
            clipStartMs = row.clipStartMs,
            clipEndMs = row.clipEndMs,
            durationMs = if (row.clipEndMs > row.clipStartMs) row.clipEndMs - row.clipStartMs
                else row.durationMs.takeIf { it > 0L } ?: track.durationMs,
        )
    })
}

private fun EchoTrackRef.needsCueRange(): Boolean =
    CueSheetPolicy.isCueTrackId(id) && clipStartMs == 0L && clipEndMs == 0L
