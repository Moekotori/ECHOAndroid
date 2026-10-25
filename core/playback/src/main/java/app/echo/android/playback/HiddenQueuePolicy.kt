package app.echo.android.playback

internal object HiddenQueuePolicy {
    fun ranges(count: Int, idAt: (Int) -> String, hidden: Set<String>): List<QueueRemovalRange> {
        val ranges = ArrayList<QueueRemovalRange>()
        var start = -1
        for (index in 0 until count) {
            val remove = idAt(index) in hidden
            if (remove && start < 0) start = index
            if (!remove && start >= 0) { ranges += QueueRemovalRange(start, index); start = -1 }
        }
        if (start >= 0) ranges += QueueRemovalRange(start, count)
        return ranges
    }
}

fun EchoPlaybackSessionSnapshot.withoutHiddenTracks(hidden: Set<String>): EchoPlaybackSessionSnapshot? {
    if (hidden.isEmpty()) return this
    val indices = queue.indices.filter { queue[it].id !in hidden }
    if (indices.isEmpty()) return null
    val remap = indices.withIndex().associate { it.value to it.index }
    val currentKept = currentIndex in remap
    val nextIndex = if (currentKept) currentIndex else indices.firstOrNull { it > currentIndex } ?: indices.first()
    return copy(queue = indices.map(queue::get), currentIndex = remap.getValue(nextIndex),
        positionMs = if (currentKept) positionMs else 0L,
        playWhenReady = playWhenReady && currentKept,
        shuffleOrder = shuffleOrder.mapNotNull(remap::get))
}
