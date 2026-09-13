package app.echo.android.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Theme-backed frosted material for persistent controls above scrolling content.
 * The tinted substrate preserves contrast over artwork. This is composited glass,
 * not a live backdrop blur: it needs no screen capture or continuously updated texture.
 */
@Composable
fun Modifier.echoFrostedGlass(shape: Shape, elevation: Dp = 8.dp): Modifier {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val substrate = scheme.surface.copy(alpha = if (lightweight) 0.97f else 0.94f)
    val wash = remember(scheme, dark) {
        Brush.linearGradient(
            0f to Color.White.copy(alpha = if (dark) 0.075f else 0.42f),
            0.48f to scheme.primary.copy(alpha = if (dark) 0.055f else 0.025f),
            1f to scheme.secondary.copy(alpha = if (dark) 0.045f else 0.055f),
        )
    }
    val rim = remember(scheme, dark, lightweight) {
        Brush.linearGradient(
            0f to Color.White.copy(alpha = if (dark) { if (lightweight) 0.23f else 0.30f } else 0.94f),
            0.38f to scheme.primary.copy(alpha = if (dark) 0.09f else 0.12f),
            0.72f to scheme.onSurface.copy(alpha = if (dark) 0.035f else 0.07f),
            1f to Color.White.copy(alpha = if (dark) { if (lightweight) 0.11f else 0.14f } else 0.58f),
        )
    }
    return this
        .then(
            if (lightweight) Modifier else Modifier.shadow(
                elevation = elevation,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = if (dark) 0.22f else 0.06f),
                spotColor = scheme.primary.copy(alpha = if (dark) 0.12f else 0.10f),
            ),
        )
        .clip(shape)
        .background(substrate)
        .then(if (lightweight) Modifier else Modifier.background(wash))
        .border(BorderStroke(0.75.dp, rim), shape)
}
