package app.echo.android.design

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import app.echo.android.model.settings.EchoWidthSizeClass

/** Available space in this pane, rather than the device's physical orientation. */
data class EchoLayoutInfo(val widthDp: Float, val heightDp: Float, val fontScale: Float = 1f) {
    val shortLandscape: Boolean
        get() = widthDp >= 480f && widthDp > heightDp && heightDp < 600f
    val compactHeader: Boolean
        get() = heightDp < 620f || widthDp > heightDp
    val navigationRailWidthDp: Int
        get() = when {
            shortLandscape -> 56
            widthDp >= 600f -> 80
            else -> 0
        }

    fun supportsTwoPanes(minimumWidthDp: Float = 840f): Boolean {
        // A phone in landscape can fit two readable panes after reserving the rail.
        // Keep tablet/portrait thresholds and enlarged-text fallbacks independent.
        val minimumWidth = if (shortLandscape) minOf(minimumWidthDp, 720f) else minimumWidthDp
        val minimumHeight = if (shortLandscape) 300f else 320f
        val textScale = fontScale.coerceAtLeast(1f)
        return widthDp >= minimumWidth * textScale && heightDp >= minimumHeight * textScale
    }
}

val LocalEchoLayout = staticCompositionLocalOf { EchoLayoutInfo(0f, 0f) }

@Composable
fun rememberEchoWindowLayout(): EchoLayoutInfo {
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    return EchoLayoutInfo(
        widthDp = if (size.width > 0) size.width / density.density else configuration.screenWidthDp.toFloat(),
        heightDp = if (size.height > 0) size.height / density.density else configuration.screenHeightDp.toFloat(),
        fontScale = density.fontScale,
    )
}

/** Rebind size decisions inside split panes and overlays; no extra state or background work. */
@Composable
fun EchoLayoutBounds(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val layout = EchoLayoutInfo(maxWidth.value, maxHeight.value, density.fontScale)
        val widthClass = EchoWidthSizeClass.fromWidthDp(maxWidth.value.toInt())
        CompositionLocalProvider(
            LocalEchoLayout provides layout,
            LocalEchoWidthSizeClass provides widthClass,
            LocalEchoContentMaxWidth provides widthClass.contentMaxWidth(),
        ) { content() }
    }
}
