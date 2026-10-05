package app.echo.android.feature.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Shared, cached outlines keep category and search icons at the same visual weight. */
internal object SettingsCategoryIcons {
    val Usb = outline("SettingsUsb") {
        moveTo(12f, 21f); verticalLineTo(3f)
        moveTo(9.5f, 5.5f); lineTo(12f, 3f); lineTo(14.5f, 5.5f)
        moveTo(12f, 16f); lineTo(6f, 12f); verticalLineTo(8f)
        moveTo(12f, 13f); lineTo(18f, 9f); verticalLineTo(6f)
        moveTo(16.5f, 3f); horizontalLineTo(19.5f); verticalLineTo(6f)
        horizontalLineTo(16.5f); verticalLineTo(3f); close()
        moveTo(8f, 6f)
        curveTo(8f, 8.7f, 4f, 8.7f, 4f, 6f)
        curveTo(4f, 3.3f, 8f, 3.3f, 8f, 6f); close()
    }

    val Connect = outline("SettingsConnect") {
        moveTo(2f, 8f); curveTo(7.5f, 2.7f, 16.5f, 2.7f, 22f, 8f)
        moveTo(5.5f, 11.5f); curveTo(9.1f, 8f, 14.9f, 8f, 18.5f, 11.5f)
        moveTo(9f, 15f); curveTo(10.7f, 13.3f, 13.3f, 13.3f, 15f, 15f)
        moveTo(12f, 19f); lineTo(12f, 19.1f)
    }

    val Scan = outline("SettingsScan") {
        moveTo(8f, 20f); horizontalLineTo(4f)
        curveTo(2.9f, 20f, 2f, 19.1f, 2f, 18f)
        verticalLineTo(5f); curveTo(2f, 3.9f, 2.9f, 3f, 4f, 3f)
        horizontalLineTo(9f); lineTo(12f, 6f); horizontalLineTo(19f)
        curveTo(20.1f, 6f, 21f, 6.9f, 21f, 8f); verticalLineTo(11f)
        moveTo(18f, 16f)
        curveTo(18f, 18.8f, 15.8f, 21f, 13f, 21f)
        curveTo(10.2f, 21f, 8f, 18.8f, 8f, 16f)
        curveTo(8f, 13.2f, 10.2f, 11f, 13f, 11f)
        curveTo(15.8f, 11f, 18f, 13.2f, 18f, 16f); close()
        moveTo(16.5f, 19.5f); lineTo(21f, 23f)
    }

    val Sound = outline("SettingsSound") {
        moveTo(4f, 6f); horizontalLineTo(7f)
        moveTo(11f, 6f); horizontalLineTo(20f)
        moveTo(4f, 12f); horizontalLineTo(13f)
        moveTo(17f, 12f); horizontalLineTo(20f)
        moveTo(4f, 18f); horizontalLineTo(7f)
        moveTo(11f, 18f); horizontalLineTo(20f)
        moveTo(11f, 6f)
        curveTo(11f, 8.7f, 7f, 8.7f, 7f, 6f)
        curveTo(7f, 3.3f, 11f, 3.3f, 11f, 6f); close()
        moveTo(17f, 12f)
        curveTo(17f, 14.7f, 13f, 14.7f, 13f, 12f)
        curveTo(13f, 9.3f, 17f, 9.3f, 17f, 12f); close()
        moveTo(11f, 18f)
        curveTo(11f, 20.7f, 7f, 20.7f, 7f, 18f)
        curveTo(7f, 15.3f, 11f, 15.3f, 11f, 18f); close()
    }

    val Appearance = outline("SettingsAppearance") {
        moveTo(12f, 3f)
        curveTo(7f, 3f, 3f, 7f, 3f, 12f)
        curveTo(3f, 17f, 7f, 21f, 12f, 21f)
        horizontalLineTo(13f)
        curveTo(14.5f, 21f, 15.3f, 19.3f, 14.3f, 18.2f)
        curveTo(13.3f, 17f, 14.2f, 15f, 16f, 15f)
        horizontalLineTo(18f)
        curveTo(20f, 15f, 21f, 13.5f, 21f, 12f)
        curveTo(21f, 7f, 17f, 3f, 12f, 3f)
        close()
        moveTo(7.5f, 11f); lineTo(7.5f, 11.1f)
        moveTo(10f, 7.5f); lineTo(10f, 7.6f)
        moveTo(14.5f, 7.5f); lineTo(14.5f, 7.6f)
        moveTo(17.5f, 11f); lineTo(17.5f, 11.1f)
    }

    val Interface = outline("SettingsInterface") {
        moveTo(4f, 7f); horizontalLineTo(8f)
        moveTo(12f, 7f); horizontalLineTo(20f)
        moveTo(10f, 4f); verticalLineTo(10f)
        moveTo(4f, 17f); horizontalLineTo(12f)
        moveTo(16f, 17f); horizontalLineTo(20f)
        moveTo(14f, 14f); verticalLineTo(20f)
    }

    val Accessibility = outline("SettingsAccessibility") {
        moveTo(14f, 4f)
        curveTo(14f, 6.7f, 10f, 6.7f, 10f, 4f)
        curveTo(10f, 1.3f, 14f, 1.3f, 14f, 4f); close()
        moveTo(4f, 8f); lineTo(12f, 10f); lineTo(20f, 8f)
        moveTo(12f, 10f); verticalLineTo(15f)
        moveTo(8f, 21f); lineTo(12f, 15f); lineTo(16f, 21f)
    }

    val Playback = outline("SettingsPlayback") {
        moveTo(4f, 13f); verticalLineTo(11f)
        curveTo(4f, 6.6f, 7.6f, 3f, 12f, 3f)
        curveTo(16.4f, 3f, 20f, 6.6f, 20f, 11f)
        verticalLineTo(13f)
        moveTo(6f, 12f); horizontalLineTo(7f); verticalLineTo(20f); horizontalLineTo(6f)
        curveTo(4.3f, 20f, 3f, 18.7f, 3f, 17f)
        verticalLineTo(15f); curveTo(3f, 13.3f, 4.3f, 12f, 6f, 12f); close()
        moveTo(18f, 12f); horizontalLineTo(17f); verticalLineTo(20f); horizontalLineTo(18f)
        curveTo(19.7f, 20f, 21f, 18.7f, 21f, 17f)
        verticalLineTo(15f); curveTo(21f, 13.3f, 19.7f, 12f, 18f, 12f); close()
    }

    val Services = outline("SettingsServices") {
        moveTo(12f, 17f); horizontalLineTo(5f)
        curveTo(3.9f, 17f, 3f, 16.1f, 3f, 15f)
        verticalLineTo(6f); curveTo(3f, 4.9f, 3.9f, 4f, 5f, 4f)
        horizontalLineTo(18f); curveTo(19.1f, 4f, 20f, 4.9f, 20f, 6f)
        verticalLineTo(8f)
        moveTo(8f, 21f); horizontalLineTo(12f)
        moveTo(10f, 17f); verticalLineTo(21f)
        moveTo(16f, 10f); horizontalLineTo(20f)
        curveTo(20.6f, 10f, 21f, 10.4f, 21f, 11f)
        verticalLineTo(20f); curveTo(21f, 20.6f, 20.6f, 21f, 20f, 21f)
        horizontalLineTo(16f); curveTo(15.4f, 21f, 15f, 20.6f, 15f, 20f)
        verticalLineTo(11f); curveTo(15f, 10.4f, 15.4f, 10f, 16f, 10f); close()
    }

    val Library = outline("SettingsLibrary") {
        moveTo(4f, 5f); verticalLineTo(19f)
        curveTo(4f, 20.1f, 4.9f, 21f, 6f, 21f); horizontalLineTo(18f)
        moveTo(9f, 3f); horizontalLineTo(19f)
        curveTo(20.1f, 3f, 21f, 3.9f, 21f, 5f)
        verticalLineTo(16f); curveTo(21f, 17.1f, 20.1f, 18f, 19f, 18f)
        horizontalLineTo(9f); curveTo(7.9f, 18f, 7f, 17.1f, 7f, 16f)
        verticalLineTo(5f); curveTo(7f, 3.9f, 7.9f, 3f, 9f, 3f); close()
        moveTo(15f, 12f); verticalLineTo(7f); lineTo(18f, 6f)
        moveTo(15f, 12f)
        curveTo(15f, 10f, 11f, 10f, 11f, 12f)
        curveTo(11f, 14f, 15f, 14f, 15f, 12f); close()
    }

    val Plugins = outline("SettingsPlugins") {
        moveTo(9f, 5f); horizontalLineTo(5f); verticalLineTo(10f)
        curveTo(1f, 8f, 1f, 16f, 5f, 14f)
        verticalLineTo(19f); horizontalLineTo(10f)
        curveTo(8f, 15f, 16f, 15f, 14f, 19f)
        horizontalLineTo(19f); verticalLineTo(14f)
        curveTo(23f, 16f, 23f, 8f, 19f, 10f)
        verticalLineTo(5f); horizontalLineTo(13f)
        curveTo(15f, 1f, 7f, 1f, 9f, 5f); close()
    }

    val Permissions = outline("SettingsPermissions") {
        moveTo(12f, 3f); lineTo(20f, 6f); verticalLineTo(12f)
        curveTo(20f, 16f, 17f, 19f, 12f, 21f)
        curveTo(7f, 19f, 4f, 16f, 4f, 12f)
        verticalLineTo(6f); lineTo(12f, 3f); close()
        moveTo(8f, 12f); lineTo(11f, 15f); lineTo(16f, 9f)
    }

    val About = outline("SettingsAbout") {
        moveTo(21f, 12f)
        curveTo(21f, 17f, 17f, 21f, 12f, 21f)
        curveTo(7f, 21f, 3f, 17f, 3f, 12f)
        curveTo(3f, 7f, 7f, 3f, 12f, 3f)
        curveTo(17f, 3f, 21f, 7f, 21f, 12f); close()
        moveTo(12f, 11f); verticalLineTo(17f)
        moveTo(12f, 7f); lineTo(12f, 7.1f)
    }
}

private fun outline(name: String, draw: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = draw,
        )
    }.build()
