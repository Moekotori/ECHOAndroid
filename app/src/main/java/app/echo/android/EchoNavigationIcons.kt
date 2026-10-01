package app.echo.android

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** A shared 24 dp grid and stroke keeps the four navigation destinations optically balanced. */
internal object EchoNavigationIcons {
    val Home = icon("EchoHome") {
        moveTo(3.5f, 10.5f); lineTo(12f, 3.5f); lineTo(20.5f, 10.5f)
        moveTo(5.5f, 9f); lineTo(5.5f, 20f); lineTo(9.5f, 20f)
        lineTo(9.5f, 14f); lineTo(14.5f, 14f); lineTo(14.5f, 20f)
        lineTo(18.5f, 20f); lineTo(18.5f, 9f)
    }
    val Library = icon("EchoLibrary") {
        moveTo(4f, 5f); lineTo(4f, 20f); lineTo(18f, 20f)
        moveTo(8f, 3.5f); lineTo(20f, 3.5f); lineTo(20f, 16f)
        lineTo(8f, 16f); close()
        moveTo(15.5f, 7f); lineTo(15.5f, 11.3f)
        curveTo(15.5f, 12.2f, 14.6f, 12.8f, 13.7f, 12.8f)
        curveTo(12.8f, 12.8f, 12.2f, 12.3f, 12.2f, 11.7f)
        curveTo(12.2f, 11f, 12.9f, 10.5f, 13.8f, 10.5f)
        lineTo(15.5f, 10.5f)
    }
    val Connect = icon("EchoConnect") {
        moveTo(13f, 16.5f); lineTo(3.5f, 16.5f); lineTo(3.5f, 4.5f)
        lineTo(20.5f, 4.5f); lineTo(20.5f, 8f)
        moveTo(8f, 20f); lineTo(12f, 20f)
        moveTo(10f, 16.5f); lineTo(10f, 20f)
        moveTo(16f, 10.5f); lineTo(21f, 10.5f); lineTo(21f, 20f)
        lineTo(16f, 20f); close()
    }
    val Diagnostics = icon("EchoSignal") {
        moveTo(4f, 9f); lineTo(4f, 15f)
        moveTo(8f, 5.5f); lineTo(8f, 18.5f)
        moveTo(12f, 3.5f); lineTo(12f, 20.5f)
        moveTo(16f, 7f); lineTo(16f, 17f)
        moveTo(20f, 10f); lineTo(20f, 14f)
    }

    private fun icon(name: String, draw: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                pathBuilder = draw,
            )
        }.build()
}
