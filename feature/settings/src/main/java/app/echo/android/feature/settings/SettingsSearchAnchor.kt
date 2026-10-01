package app.echo.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

internal data class SettingsSearchFocus(val title: String, val requestId: Int)

internal val LocalSettingsSearchFocus = compositionLocalOf<SettingsSearchFocus?> { null }

@Composable
internal fun Modifier.settingsSearchAnchor(title: String): Modifier {
    val focus = LocalSettingsSearchFocus.current
    // Ordinary rows need no requester, size state or layout-driven coroutine.
    if (focus?.title != title) return this
    val requester = remember { BringIntoViewRequester() }
    val density = LocalDensity.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(focus, title, size) {
        if (size != IntSize.Zero) {
            withFrameNanos { }
            // The mini player and dock float over the bottom of the scroll viewport.
            val visibleTopHeight = minOf(size.height.toFloat(), with(density) { 64.dp.toPx() })
            requester.bringIntoView(Rect(
                0f, 0f, size.width.toFloat(),
                visibleTopHeight + with(density) { 176.dp.toPx() },
            ))
        }
    }
    return this.bringIntoViewRequester(requester).onSizeChanged { size = it }.background(
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        SettingsShape,
    )
}
