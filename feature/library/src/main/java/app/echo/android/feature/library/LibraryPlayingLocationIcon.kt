package app.echo.android.feature.library

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Playback at the centre of a fine locating ring, rather than a filled GPS dot. */
internal val LibraryPlayingLocationIcon: ImageVector = ImageVector.Builder(
    name = "LocatePlayingSong",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.6f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(20f, 12f)
        arcTo(8f, 8f, 0f, true, true, 4f, 12f)
        arcTo(8f, 8f, 0f, true, true, 20f, 12f)
        moveTo(12f, 2f); lineTo(12f, 5f)
        moveTo(12f, 19f); lineTo(12f, 22f)
        moveTo(2f, 12f); lineTo(5f, 12f)
        moveTo(19f, 12f); lineTo(22f, 12f)
    }
    path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
        moveTo(10f, 8.5f)
        lineTo(15.5f, 12f)
        lineTo(10f, 15.5f)
        close()
    }
}.build()
