package app.echo.android.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon

@Composable
internal fun SettingsBackgroundViewfinder(
    sourceKey: String,
    viewportRatio: Float,
    viewportWidthDp: Int,
    sourceRatio: Float,
    scale: Float,
    positionX: Float,
    positionY: Float,
    loading: Boolean,
    onPreview: (Float, Float) -> Unit,
    onCommit: (Float, Float) -> Unit,
    content: @Composable BoxScope.(Boolean, Float) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var dragging by remember(sourceKey) { mutableStateOf(false) }
    val frame = RoundedCornerShape(20.dp)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(scheme.surfaceContainerHigh).padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val previewHeight = minOf(240.dp, maxWidth / viewportRatio)
            val previewScale = previewHeight.value * viewportRatio / viewportWidthDp.coerceAtLeast(1)
            Box(
                Modifier.height(previewHeight).aspectRatio(viewportRatio)
                    .shadow(3.dp, frame).clip(frame)
                    .background(scheme.surfaceVariant)
                    .border(1.dp, scheme.outlineVariant.copy(alpha = .65f), frame)
                    .backgroundPositionDrag(sourceKey, sourceRatio, scale, positionX, positionY,
                        onPreview, onCommit, onDraggingChange = { dragging = it }),
            ) {
                content(dragging, previewScale)
                if (loading) CircularProgressIndicator(Modifier.align(Alignment.Center).size(24.dp),
                    color = scheme.onSurface, strokeWidth = 2.dp)
                if (dragging) Canvas(Modifier.fillMaxSize()) {
                    val guide = Color.White.copy(alpha = .65f)
                    // Thirds help compose the artwork; a darker underlay remains visible on pale images.
                    for (part in 1..2) {
                        val x = size.width * part / 3f
                        val y = size.height * part / 3f
                        drawLine(Color.Black.copy(alpha = .2f), Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
                        drawLine(Color.Black.copy(alpha = .2f), Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
                        drawLine(guide, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                        drawLine(guide, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                    }
                    drawCircle(guide, 3.dp.toPx(), center)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            EchoIcon(Icons.Rounded.OpenWith, null, Modifier.size(16.dp), tint = scheme.onSurfaceVariant)
            Text(stringResource(if (dragging) R.string.settings_background_position_dragging
                else R.string.settings_background_position_drag_hint),
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}
