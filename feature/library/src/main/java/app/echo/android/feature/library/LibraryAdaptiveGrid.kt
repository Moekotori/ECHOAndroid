package app.echo.android.feature.library

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.ui.unit.Density

internal fun libraryWallColumns(availableWidthDp: Float, fontScale: Float): Int {
    val scale = fontScale.coerceAtLeast(1f)
    // Retain the familiar phone wall; wider panes add covers rather than enlarging them.
    if (scale <= 1.15f && availableWidthDp in 300f..479f) return 3
    return (availableWidthDp / (144f * scale)).toInt().coerceIn(1, 6)
}

/** The grid's measured width already excludes pane padding, rails and split dividers. */
internal object LibraryAdaptiveGridCells : GridCells {
    override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
        val columns = libraryWallColumns(availableSize / density, fontScale)
        return with(GridCells.Fixed(columns)) { calculateCrossAxisCellSizes(availableSize, spacing) }
    }
}
