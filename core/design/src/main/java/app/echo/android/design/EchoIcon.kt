package app.echo.android.design

import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.painter.Painter

/** Icons have a neutral foreground independent of the selected accent palette. */
@Composable
fun echoIconColor(): Color = neutralIconColor(LocalEchoDarkTheme.current)

internal fun neutralIconColor(dark: Boolean): Color =
    if (dark) Color(0xFFF1F1F3) else Color(0xFF303036)

/** Preserve explicit artwork/semantic colours and opacity; neutralise theme colour roles. */
@Composable
private fun iconForeground(tint: Color): Color {
    if (tint == Color.Unspecified) return tint
    val scheme = MaterialTheme.colorScheme
    val theme = echoTheme()
    val opaque = tint.copy(alpha = 1f)
    // Filled controls keep a black/white foreground appropriate to their background.
    if (opaque == scheme.onPrimary.copy(alpha = 1f) ||
        opaque == scheme.onSecondary.copy(alpha = 1f) ||
        opaque == scheme.onTertiary.copy(alpha = 1f) ||
        opaque == theme.onAccent.copy(alpha = 1f)
    ) {
        return (if (opaque.luminance() > 0.5f) Color.White else Color(0xFF202024))
            .copy(alpha = tint.alpha)
    }
    val themed = opaque == scheme.primary.copy(alpha = 1f) ||
        opaque == scheme.secondary.copy(alpha = 1f) ||
        opaque == scheme.tertiary.copy(alpha = 1f) ||
        opaque == scheme.onPrimaryContainer.copy(alpha = 1f) ||
        opaque == scheme.onSecondaryContainer.copy(alpha = 1f) ||
        opaque == scheme.onTertiaryContainer.copy(alpha = 1f) ||
        opaque == theme.accent.copy(alpha = 1f) ||
        opaque == theme.accentDeep.copy(alpha = 1f) ||
        opaque == theme.accentText.copy(alpha = 1f) ||
        opaque == theme.secondary.copy(alpha = 1f) ||
        opaque == scheme.onBackground.copy(alpha = 1f) ||
        opaque == scheme.onSurface.copy(alpha = 1f) ||
        opaque == scheme.onSurfaceVariant.copy(alpha = 1f) ||
        opaque == theme.heading.copy(alpha = 1f) ||
        opaque == theme.muted.copy(alpha = 1f)
    return if (themed) echoIconColor().copy(alpha = tint.alpha) else tint
}

@Composable
fun EchoIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    MaterialIcon(imageVector, contentDescription, modifier, iconForeground(tint))
}

@Composable
fun EchoIcon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    MaterialIcon(painter, contentDescription, modifier, iconForeground(tint))
}
