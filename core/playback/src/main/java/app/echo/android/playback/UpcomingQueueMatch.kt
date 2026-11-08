package app.echo.android.playback

/** Only future occurrences are eligible; current playback and history stay intact. */
internal fun upcomingQueueMatch(order: List<Int>, current: Int, ids: List<String>, target: String): Int? {
    val at = order.indexOf(current)
    if (at < 0 || target.isBlank()) return null
    return order.drop(at + 1).firstOrNull { ids.getOrNull(it) == target }
}
