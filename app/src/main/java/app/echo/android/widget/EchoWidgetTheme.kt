package app.echo.android.widget

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import app.echo.android.R
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.color.ColorProvider

// Glance's single-color ColorProvider factory is @RestrictTo; day == night pins one color.
internal fun widgetSolidColor(color: Color) = ColorProvider(color, color)

/** Neutral day/night surfaces let the record artwork supply the color, independently of wallpaper. */
internal val widgetForeground get() = androidx.glance.unit.ColorProvider(R.color.playback_widget_foreground)
internal val widgetSecondary get() = androidx.glance.unit.ColorProvider(R.color.playback_widget_secondary)
internal val widgetArtworkPlaceholder get() = androidx.glance.unit.ColorProvider(R.color.playback_widget_artwork_background)
internal val widgetAccent get() = androidx.glance.unit.ColorProvider(R.color.playback_widget_accent)
internal val widgetOnAccent get() = androidx.glance.unit.ColorProvider(R.color.playback_widget_on_accent)
internal val widgetTonal get() = androidx.glance.unit.ColorProvider(R.color.playback_widget_tonal)
internal val widgetOnTonal get() = androidx.glance.unit.ColorProvider(R.color.playback_widget_on_tonal)

internal val ArtworkOverlayForeground = Color.White
internal val ArtworkOverlaySecondary = Color(0xFFE4E4EA)
internal val ArtworkOverlayOnPrimary = Color(0xFF1B1B1F)

/** Marks the widget root so launchers can clip it to their own shape, with the system radius when exposed. */
@Composable
internal fun GlanceModifier.widgetSurface(): GlanceModifier {
    val shaped = appWidgetBackground()
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        shaped.cornerRadius(android.R.dimen.system_app_widget_background_radius)
    } else shaped.cornerRadius(24.dp)
}

/** Small artwork keeps a tighter corner than the surrounding widget. */
internal fun GlanceModifier.widgetInnerCorners(size: Dp): GlanceModifier = when {
    size < 48.dp -> cornerRadius(10.dp)
    else -> cornerRadius(12.dp)
}
