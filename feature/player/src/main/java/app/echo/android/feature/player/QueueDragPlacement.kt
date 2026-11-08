package app.echo.android.feature.player

internal data class QueueDragTarget(val queueIndex: Int, val row: Int, val lastRow: Int, val group: Int)

internal fun queueDragCenter(pointerY: Float, grabOffsetY: Float, rowHeight: Int): Float =
    pointerY - grabOffsetY + rowHeight / 2f

internal fun queueDragHitAllowed(x: Float, width: Float, menuWidth: Float): Boolean =
    x >= 0f && x < width - menuWidth

/** Preview the insertion without changing the playback queue until release. */
internal fun queueDragDisplacement(
    row: Int,
    group: Int,
    source: QueueDragTarget?,
    target: QueueDragTarget?,
    rowHeight: Int,
): Float = when {
    source == null || target == null || group != source.group || group != target.group -> 0f
    source.row < target.row && row > source.row && row <= target.row -> -rowHeight.toFloat()
    source.row > target.row && row >= target.row && row < source.row -> rowHeight.toFloat()
    else -> 0f
}
