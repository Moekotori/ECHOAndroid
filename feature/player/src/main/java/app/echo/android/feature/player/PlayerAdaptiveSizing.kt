package app.echo.android.feature.player

/** Returns a readable side pane only when both panes fit the available window. */
internal fun playerSidePaneWidth(
    widthDp: Float,
    heightDp: Float,
    fontScale: Float,
    minimumControlsWidthDp: Float,
    minimumContentWidthDp: Float,
    gapDp: Float,
    preferredControlsFraction: Float,
): Float? {
    if (!widthDp.isFinite() || !heightDp.isFinite() || widthDp <= 0 || heightDp <= 0) return null
    val scale = fontScale.takeIf { it.isFinite() }?.coerceAtLeast(1f) ?: 1f
    val shortLandscape = widthDp > heightDp && heightDp < 600f
    if (!shortLandscape && widthDp < 720f * scale) return null
    val minimum = minimumControlsWidthDp * scale
    val maximum = widthDp - gapDp - minimumContentWidthDp
    if (widthDp < 480f || maximum < minimum) return null
    return ((widthDp - gapDp) * preferredControlsFraction).coerceIn(minimum, maximum)
}

internal data class PlayerDialogSizing(val heightDp: Float, val bodyHeightDp: Float)

/** Reserve the dialog title/actions and system bars before allocating its scrollable body. */
internal fun playerDialogSizing(windowHeightDp: Float, verticalInsetsDp: Float, fontScale: Float): PlayerDialogSizing {
    val height = (windowHeightDp - verticalInsetsDp - 32f).coerceAtLeast(48f)
    val scale = fontScale.takeIf { it.isFinite() }?.coerceAtLeast(1f) ?: 1f
    val body = (height - 160f * scale).coerceIn(24f, height)
    return PlayerDialogSizing(height, body)
}
