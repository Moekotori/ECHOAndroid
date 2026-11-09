package app.echo.android.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.echo.android.design.echoRowSwipe
import app.echo.android.design.LocalEchoPageInteractive
import app.echo.android.design.rememberEchoHapticPerformer

private val QueueRemoveWidth = 104.dp

@Composable
internal fun QueueTrackSwipe(
    entryId: String?,
    enabled: Boolean,
    revealed: Boolean,
    onReveal: (Boolean) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    if (entryId == null) {
        content(modifier)
        return
    }
    val width = with(LocalDensity.current) { QueueRemoveWidth.toPx() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val progress = remember(entryId, revealed) { mutableFloatStateOf(0f) }
    var dragging by remember(entryId) { mutableStateOf(false) }
    val interactive = enabled && LocalEchoPageInteractive.current
    val haptics = rememberEchoHapticPerformer()
    val label = stringResource(R.string.feature_player_remove_track_60c516)
    val colors = MaterialTheme.colorScheme
    fun exposedWidth() = if (revealed) width - progress.floatValue else progress.floatValue

    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .then(if (interactive) Modifier.semantics {
                customActions = listOf(CustomAccessibilityAction(label) { onRemove(); true })
            } else Modifier)
            .echoRowSwipe(
                enabled = interactive,
                towardEnd = revealed,
                gestureKey = entryId,
                onDrag = {
                    dragging = true
                    progress.floatValue = it.coerceAtMost(width)
                },
                onRelease = { distance ->
                    dragging = false
                    progress.floatValue = 0f
                    if (distance >= width * 0.4f) {
                        haptics.tick()
                        onReveal(!revealed)
                    }
                },
                onCancel = {
                    dragging = false
                    progress.floatValue = 0f
                },
            ),
    ) {
        if (revealed || dragging) Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
            Box(
                Modifier.width(QueueRemoveWidth).fillMaxHeight().graphicsLayer {
                    translationX = (width - exposedWidth()) * if (rtl) -1f else 1f
                }.background(colors.errorContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (revealed) {
                    TextButton(
                        onClick = onRemove,
                        enabled = interactive,
                        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    ) {
                        QueueRemoveLabel(label)
                    }
                } else {
                    Box(Modifier.clearAndSetSemantics {}) { QueueRemoveLabel(label) }
                }
            }
        }
        content(Modifier.graphicsLayer {
            translationX = exposedWidth() * if (rtl) 1f else -1f
        })
    }
}

@Composable
private fun QueueRemoveLabel(label: String) {
    Text(
        label,
        color = MaterialTheme.colorScheme.onErrorContainer,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}
