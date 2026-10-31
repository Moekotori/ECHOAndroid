package app.echo.android.widget

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.color.ColorProvider

// Glance's single-color ColorProvider factory is @RestrictTo; day == night pins one color.
internal fun widgetSolidColor(color: Color) = ColorProvider(color, color)

/**
 * Widget palette. Android 12+ resolves Material You colors from the wallpaper; older releases use
 * the Material 3 baseline. Fixed colors are only for content drawn on top of album artwork.
 */
internal val widgetForeground: androidx.glance.unit.ColorProvider @Composable get() = GlanceTheme.colors.onSurface
internal val widgetSecondary: androidx.glance.unit.ColorProvider @Composable get() = GlanceTheme.colors.onSurfaceVariant
internal val widgetArtworkPlaceholder: androidx.glance.unit.ColorProvider @Composable get() = GlanceTheme.colors.surfaceVariant
internal val widgetAccent: androidx.glance.unit.ColorProvider @Composable get() = GlanceTheme.colors.primary
internal val widgetOnAccent: androidx.glance.unit.ColorProvider @Composable get() = GlanceTheme.colors.onPrimary
internal val widgetTonal: androidx.glance.unit.ColorProvider @Composable get() = GlanceTheme.colors.secondaryContainer
internal val widgetOnTonal: androidx.glance.unit.ColorProvider @Composable get() = GlanceTheme.colors.onSecondaryContainer

internal val ArtworkOverlayForeground = Color.White
internal val ArtworkOverlaySecondary = Color(0xFFE4E4EA)
internal val ArtworkOverlayGlass = Color.White.copy(alpha = 0.20f)
internal val ArtworkOverlayOnPrimary = Color(0xFF1B1B1F)

/** Marks the widget root so launchers can clip it to their own shape, with the system radius when exposed. */
@Composable
internal fun GlanceModifier.widgetSurface(): GlanceModifier {
    val shaped = appWidgetBackground()
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        shaped.cornerRadius(android.R.dimen.system_app_widget_background_radius)
    } else shaped.cornerRadius(24.dp)
}

/** Inner elements (cover, footer) use the launcher's inner radius so nested corners stay concentric. */
internal fun GlanceModifier.widgetInnerCorners(size: Dp): GlanceModifier = when {
    size < 48.dp -> cornerRadius(10.dp)
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> cornerRadius(android.R.dimen.system_app_widget_inner_radius)
    else -> cornerRadius(16.dp)
}
