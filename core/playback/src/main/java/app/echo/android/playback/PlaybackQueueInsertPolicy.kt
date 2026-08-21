package app.echo.android.playback

object PlaybackQueueInsertPolicy {
    fun shouldReplaceQueue(queueSize: Int): Boolean = queueSize <= 0

    fun playNextIndex(currentIndex: Int, queueSize: Int): Int {
        if (queueSize <= 0) return 0
        if (currentIndex < 0) return queueSize
        return (currentIndex + 1).coerceAtMost(queueSize)
    }
}
