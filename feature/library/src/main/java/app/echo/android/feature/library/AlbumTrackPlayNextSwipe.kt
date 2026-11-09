package app.echo.android.feature.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.echo.android.design.echoIconColor
import app.echo.android.design.echoRowSwipe
import app.echo.android.design.LocalEchoPageInteractive
import app.echo.android.design.rememberEchoHapticPerformer

@Composable
internal fun Modifier.albumTrackPlayNextSwipe(trackId: String, onPlayNext: (() -> Unit)?): Modifier {
    if (onPlayNext == null || !LocalEchoPageInteractive.current) return this
    val progress = remember(trackId) { mutableFloatStateOf(0f) }
    val threshold = with(LocalDensity.current) { 56.dp.toPx() }
    val maxOffset = with(LocalDensity.current) { 76.dp.toPx() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val icon = rememberVectorPainter(Icons.AutoMirrored.Rounded.PlaylistPlay)
    val tint = ColorFilter.tint(echoIconColor())
    val label = stringResource(R.string.feature_library_play_next_a1f73e)
    val haptics = rememberEchoHapticPerformer()
    return semantics {
        customActions = listOf(CustomAccessibilityAction(label) { onPlayNext(); true })
    }.echoRowSwipe(
        enabled = true,
        towardEnd = true,
        gestureKey = trackId,
        onDrag = { progress.floatValue = it.coerceAtMost(maxOffset) },
        onRelease = { distance ->
            progress.floatValue = 0f
            if (distance >= threshold) {
                haptics.tick()
                onPlayNext()
            }
        },
        onCancel = { progress.floatValue = 0f },
    ).drawWithContent {
        val distance = progress.floatValue
        if (distance == 0f) {
            drawContent()
        } else {
            val iconSize = 24.dp.toPx()
            val offset = if (rtl) -distance else distance
            clipRect {
                translate(left = offset) { this@drawWithContent.drawContent() }
                val left = if (rtl) size.width - distance else 0f
                clipRect(left = left, right = left + distance) {
                    translate(left + (distance - iconSize) / 2f, (size.height - iconSize) / 2f) {
                        with(icon) {
                            draw(Size(iconSize, iconSize), alpha = (distance / threshold).coerceIn(0f, 1f), colorFilter = tint)
                        }
                    }
                }
            }
        }
    }
}
