package app.echo.android.playback

object PlaybackQueueInsertPolicy {
    fun shouldReplaceQueue(queueSize: Int): Boolean = queueSize <= 0

    fun playNextIndex(
        currentIndex: Int,
        queueSize: Int,
        @Suppress("UNUSED_PARAMETER") shuffledNextIndex: Int? = null,
    ): Int {
        if (queueSize <= 0) return 0
        if (currentIndex < 0) return queueSize
        return (currentIndex + 1).coerceAtMost(queueSize)
    }

    fun shouldSkipInsert(existingIds: List<String>, insertIndex: Int, trackId: String): Boolean {
        val id = trackId.trim()
        if (id.isEmpty()) return true
        return existingIds.getOrNull(insertIndex.coerceAtLeast(0)) == id
    }

    fun shouldSkipEnqueue(existingIds: List<String>, trackId: String): Boolean {
        val id = trackId.trim()
        if (id.isEmpty()) return true
        return existingIds.lastOrNull() == id
    }

    fun moveIndex(fromIndex: Int, toIndex: Int, queueSize: Int): Pair<Int, Int>? {
        if (queueSize <= 1) return null
        if (fromIndex !in 0 until queueSize) return null
        if (toIndex !in 0 until queueSize) return null
        if (fromIndex == toIndex) return null
        return fromIndex to toIndex
    }
}
