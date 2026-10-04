package app.echo.android.playback

/** Keep older albums before the selected album, with a bounded base queue. */
internal object AlbumQueuePolicy {
    const val BASE_QUEUE_LIMIT = 1_000

    fun retainedIndices(
        existingIds: List<String>,
        nextUpIndices: Set<Int>,
        albumIds: List<String>,
        currentIndex: Int = -1,
    ): List<Int> {
        val selected = albumIds.toHashSet()
        val older = existingIds.indices.filter { it !in nextUpIndices && existingIds[it] !in selected }
            .takeLast((BASE_QUEUE_LIMIT - albumIds.size).coerceAtLeast(0)).toHashSet()
        return existingIds.indices.filter { it in older || (it in nextUpIndices && it != currentIndex) }
    }
}
