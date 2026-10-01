package app.echo.android.feature.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** One optical grid and line weight for all standard player controls. */
internal object PlayerControlIcons {
    private val ink = SolidColor(Color.Black)
    private const val stroke = 1.85f

    val Play = vector("PlayerPlay") {
        path(fill = ink) {
            moveTo(7f, 5.8f)
            curveTo(7f, 4.35f, 8.05f, 3.8f, 9.25f, 4.55f)
            lineTo(19.15f, 10.75f)
            curveTo(20.45f, 11.55f, 20.45f, 12.45f, 19.15f, 13.25f)
            lineTo(9.25f, 19.45f)
            curveTo(8.05f, 20.2f, 7f, 19.65f, 7f, 18.2f)
            close()
        }
    }
    val Pause = vector("PlayerPause") {
        path(stroke = ink, strokeLineWidth = 3.1f, strokeLineCap = StrokeCap.Round) {
            moveTo(8f, 5.7f); lineTo(8f, 18.3f)
            moveTo(16f, 5.7f); lineTo(16f, 18.3f)
        }
    }
    val Previous = vector("PlayerPrevious") {
        path(stroke = ink, strokeLineWidth = 2.2f, strokeLineCap = StrokeCap.Round) {
            moveTo(5f, 6.2f); lineTo(5f, 17.8f)
        }
        path(fill = ink) {
            moveTo(18.6f, 6.5f)
            curveTo(18.6f, 5.25f, 17.7f, 4.85f, 16.7f, 5.5f)
            lineTo(8.5f, 10.9f)
            curveTo(7.35f, 11.65f, 7.35f, 12.35f, 8.5f, 13.1f)
            lineTo(16.7f, 18.5f)
            curveTo(17.7f, 19.15f, 18.6f, 18.75f, 18.6f, 17.5f)
            close()
        }
    }
    val Next = vector("PlayerNext") {
        path(stroke = ink, strokeLineWidth = 2.2f, strokeLineCap = StrokeCap.Round) {
            moveTo(19f, 6.2f); lineTo(19f, 17.8f)
        }
        path(fill = ink) {
            moveTo(5.4f, 6.5f)
            curveTo(5.4f, 5.25f, 6.3f, 4.85f, 7.3f, 5.5f)
            lineTo(15.5f, 10.9f)
            curveTo(16.65f, 11.65f, 16.65f, 12.35f, 15.5f, 13.1f)
            lineTo(7.3f, 18.5f)
            curveTo(6.3f, 19.15f, 5.4f, 18.75f, 5.4f, 17.5f)
            close()
        }
    }
    val Lyrics = vector("PlayerLyrics") {
        path(stroke = ink, strokeLineWidth = stroke, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(7.6f, 4.2f); lineTo(16.4f, 4.2f)
            curveTo(19f, 4.2f, 20.2f, 5.4f, 20.2f, 8f)
            lineTo(20.2f, 13.2f); curveTo(20.2f, 15.8f, 19f, 17f, 16.4f, 17f)
            lineTo(9.7f, 17f)
            curveTo(9.2f, 17f, 8.9f, 17.1f, 8.5f, 17.4f)
            lineTo(5.2f, 19.5f); curveTo(4.3f, 20.1f, 3.8f, 19.7f, 3.8f, 18.7f)
            lineTo(3.8f, 8f); curveTo(3.8f, 5.4f, 5f, 4.2f, 7.6f, 4.2f); close()
            moveTo(8f, 8.6f); lineTo(16f, 8.6f)
            moveTo(8f, 12.6f); lineTo(13.5f, 12.6f)
        }
    }
    val Queue = vector("PlayerQueue") {
        path(stroke = ink, strokeLineWidth = stroke, strokeLineCap = StrokeCap.Round) {
            moveTo(4.5f, 6.5f); lineTo(4.52f, 6.5f); moveTo(9f, 6.5f); lineTo(19.5f, 6.5f)
            moveTo(4.5f, 12f); lineTo(4.52f, 12f); moveTo(9f, 12f); lineTo(19.5f, 12f)
            moveTo(4.5f, 17.5f); lineTo(4.52f, 17.5f); moveTo(9f, 17.5f); lineTo(16.5f, 17.5f)
        }
    }
    val Settings = vector("PlayerSettings") {
        path(stroke = ink, strokeLineWidth = stroke, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(4f, 6f); lineTo(8f, 6f); moveTo(12f, 6f); lineTo(20f, 6f)
            moveTo(10f, 3.5f); lineTo(10f, 8.5f)
            moveTo(4f, 12f); lineTo(14f, 12f); moveTo(18f, 12f); lineTo(20f, 12f)
            moveTo(16f, 9.5f); lineTo(16f, 14.5f)
            moveTo(4f, 18f); lineTo(6f, 18f); moveTo(10f, 18f); lineTo(20f, 18f)
            moveTo(8f, 15.5f); lineTo(8f, 20.5f)
        }
    }
    val Cast = vector("PlayerCast") {
        path(stroke = ink, strokeLineWidth = stroke, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(3.8f, 7.5f); lineTo(3.8f, 7.2f); curveTo(3.8f, 5.2f, 4.8f, 4.5f, 6.7f, 4.5f)
            lineTo(17.3f, 4.5f); curveTo(19.4f, 4.5f, 20.2f, 5.4f, 20.2f, 7.5f)
            lineTo(20.2f, 15.5f); curveTo(20.2f, 17.6f, 19.4f, 18.5f, 17.3f, 18.5f); lineTo(14f, 18.5f)
            moveTo(3.5f, 18.5f); lineTo(3.6f, 18.5f)
            moveTo(3.5f, 14.5f); quadTo(7.5f, 14.5f, 7.5f, 18.5f)
            moveTo(3.5f, 10.5f); quadTo(11.5f, 10.5f, 11.5f, 18.5f)
        }
    }
    val Collapse = vector("PlayerCollapse") {
        path(stroke = ink, strokeLineWidth = stroke, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(6f, 9.5f); lineTo(10.6f, 14.1f)
            quadTo(12f, 15.5f, 13.4f, 14.1f); lineTo(18f, 9.5f)
        }
    }

    private fun vector(name: String, content: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(content).build()
}
