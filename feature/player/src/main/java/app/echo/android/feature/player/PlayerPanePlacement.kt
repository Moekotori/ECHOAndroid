package app.echo.android.feature.player

internal data class PlayerPaneBounds(val x: Int, val y: Int, val width: Int, val height: Int)
internal data class PlayerPanePlacement(val cover: PlayerPaneBounds, val lyrics: PlayerPaneBounds? = null) {
    val isSplit: Boolean get() = lyrics != null
}

/** Applies hinge coordinates after window insets, the header, and centered content padding. */
internal fun playerPanePlacement(
    width: Int, height: Int, spacing: Int,
    fold: PlayerFold? = null, originX: Int = 0, originY: Int = 0,
    preferSplit: Boolean = true, allowSplit: Boolean = true,
    minimumPaneWidth: Int = 0, minimumPaneHeight: Int = 0, rtl: Boolean = false,
): PlayerPanePlacement {
    val gap = spacing.coerceIn(0, minOf(width, height).coerceAtLeast(0))
    if (fold != null) {
        val start = if (fold.horizontal) fold.top - originY else fold.left - originX
        val end = if (fold.horizontal) fold.bottom - originY else fold.right - originX
        val extent = if (fold.horizontal) height else width
        val crossStart = if (fold.horizontal) fold.left - originX else fold.top - originY
        val crossEnd = if (fold.horizontal) fold.right - originX else fold.bottom - originY
        val crossExtent = if (fold.horizontal) width else height
        if (end >= 0 && start <= extent && crossEnd > 0 && crossStart < crossExtent) {
            val before = (start - gap / 2).coerceIn(0, extent)
            val after = (end + gap / 2).coerceIn(0, extent)
            val first = if (fold.horizontal) PlayerPaneBounds(0, 0, width, before) else PlayerPaneBounds(0, 0, before, height)
            val second = if (fold.horizontal) PlayerPaneBounds(0, after, width, height - after)
                else PlayerPaneBounds(after, 0, width - after, height)
            fun PlayerPaneBounds.fits() = this.width >= minimumPaneWidth && this.height >= minimumPaneHeight &&
                this.width > 0 && this.height > 0
            if (allowSplit && first.fits() && second.fits()) {
                return if (fold.horizontal || rtl) PlayerPanePlacement(second, first) else PlayerPanePlacement(first, second)
            }
            // Keep the pager (and its lyrics entry) inside the larger usable region.
            return PlayerPanePlacement(if (first.width.toLong() * first.height >= second.width.toLong() * second.height) first else second)
        }
    }
    if (!allowSplit || !preferSplit || height < minimumPaneHeight || (width - gap) / 2 < minimumPaneWidth) {
        return PlayerPanePlacement(PlayerPaneBounds(0, 0, width, height))
    }
    val first = ((width - gap) / 2).coerceAtLeast(0)
    val left = PlayerPaneBounds(0, 0, first, height)
    val right = PlayerPaneBounds(first + gap, 0, (width - first - gap).coerceAtLeast(0), height)
    return if (rtl) PlayerPanePlacement(right, left) else PlayerPanePlacement(left, right)
}
