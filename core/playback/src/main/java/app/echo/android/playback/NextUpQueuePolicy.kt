package app.echo.android.playback

/** Pure ordering policy. Indices refer to the physical Media3 playlist. */
internal object NextUpQueuePolicy {
    fun insertionIndex(current: Int, size: Int, pending: List<Int>, first: Boolean): Int =
        if (size == 0) 0 else if (first) (current + 1).coerceIn(0, size)
        else ((pending.lastOrNull() ?: current) + 1).coerceIn(0, size)

    fun shuffledOrder(existing: List<Int>, manual: List<Int>, anchor: Int): List<Int> {
        if (manual.isEmpty()) return existing
        val manualSet = manual.toHashSet()
        val base = existing.filterNot { it in manualSet }
        val at = (base.indexOf(anchor) + 1).coerceAtLeast(0)
        return base.take(at) + manual + base.drop(at)
    }

    fun validOrder(order: List<Int>, size: Int): Boolean =
        order.size == size && order.toSet().size == size && order.all { it in 0 until size }
}
