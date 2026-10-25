package app.echo.android.data

data class BookmarkMergeMove(
    val original: LibraryBookmarkEntity,
    val destination: LibraryBookmarkEntity,
    val sourceStartMs: Long,
    val targetStartMs: Long,
    val targetDurationMs: Long,
) {
    fun endPosition(endMs: Long): Long? {
        if (endMs < 0 || endMs > Long.MAX_VALUE - sourceStartMs) return null
        val absolute = endMs + sourceStartMs
        if (absolute < targetStartMs) return null
        val relative = absolute - targetStartMs
        return (if (targetDurationMs > 0) minOf(relative, targetDurationMs) else relative)
            .takeIf { it > destination.positionMs }
    }
}

data class BookmarkMergePlan(
    val source: LibraryTrackEntity,
    val targets: List<LibraryTrackEntity>,
    val originals: List<LibraryBookmarkEntity>,
    val moves: List<BookmarkMergeMove>,
) {
    val complete: Boolean get() = targets.size <= 4096 && originals.size <= 10000 && moves.size == originals.size
}

internal fun bookmarkMergePlan(source: LibraryTrackEntity, targets: List<LibraryTrackEntity>, bookmarks: List<LibraryBookmarkEntity>): BookmarkMergePlan {
    val ordered = targets.sortedByDescending { it.clipStartMs }
    val moves = bookmarks.mapNotNull { bookmark ->
        val position = bookmark.positionMs
        if (position < 0 || source.clipStartMs < 0 || position > Long.MAX_VALUE - source.clipStartMs) return@mapNotNull null
        val absolute = source.clipStartMs + position
        val target = ordered.firstOrNull { candidate ->
            if (candidate.clipStartMs < 0 || absolute < candidate.clipStartMs) false
            else {
                val relative = absolute - candidate.clipStartMs
                (candidate.clipEndMs <= 0 || absolute < candidate.clipEndMs) &&
                    (candidate.durationMs <= 0 || relative < candidate.durationMs)
            }
        } ?: return@mapNotNull null
        val relative = absolute - target.clipStartMs
        val destination = bookmark.copy(id = "${target.id}:$relative", trackId = target.id, positionMs = relative,
            titleSnapshot = target.title, artistSnapshot = target.artist, uriSnapshot = target.contentUri, durationSnapshot = target.durationMs)
        BookmarkMergeMove(bookmark, destination, source.clipStartMs, target.clipStartMs, target.durationMs)
    }
    return BookmarkMergePlan(source, targets, bookmarks, moves)
}
