package app.echo.android.model.playback

data class PlaybackQueueState(
    val items: List<EchoTrackRef> = emptyList(),
    val currentIndex: Int = -1,
    val playOrder: List<Int> = items.indices.toList(),
    val repeatAll: Boolean = false,
) {
    val nextUpIndices: List<Int>
        get() = items.indices.filter { it != currentIndex && items[it].queueContext?.nextUp == true }

    val source: String?
        get() = currentItem?.queueContext?.source ?: items.firstOrNull { it.queueContext?.nextUp != true }?.queueContext?.source

    val continuationIndices: List<Int>
        get() {
            val at = playOrder.indexOf(currentIndex)
            val upcoming = playOrder.drop(at + 1) + if (repeatAll && at >= 0) playOrder.take(at) else emptyList()
            return upcoming.filter { items[it].queueContext?.nextUp != true }
        }

    val isEmpty: Boolean
        get() = items.isEmpty()

    val currentItem: EchoTrackRef?
        get() = items.getOrNull(currentIndex)
}
