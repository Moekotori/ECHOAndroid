package app.echo.android.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A reorderable, hideable entry for layout editors. Hidden entries fade back instead of vanishing,
 * and the numbered plate always shows where the entry currently sits.
 */
@Composable
fun EchoSheetOrderRow(
    position: Int,
    label: String,
    visible: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    moveUpDescription: String,
    moveDownDescription: String,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onVisibleChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    switchEnabled: Boolean = true,
) {
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val dim by animateFloatAsState(
        targetValue = if (visible) 1f else 0.5f,
        animationSpec = if (lightweight) snap() else EchoMotion.silkFloat(EchoMotion.FeedbackMs),
        label = "echo-order-row-dim",
    )
    Row(
        modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.weight(1f).graphicsLayer { alpha = dim },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(34.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    position.toString(),
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            EchoIcon(Icons.Rounded.KeyboardArrowUp, moveUpDescription)
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            EchoIcon(Icons.Rounded.KeyboardArrowDown, moveDownDescription)
        }
        EchoSwitch(
            checked = visible, onCheckedChange = onVisibleChange, enabled = switchEnabled,
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

/** Reset on the start side, cancel and the filled apply action on the end. */
@Composable
fun EchoSheetActionBar(
    applyLabel: String,
    onApply: () -> Unit,
    cancelLabel: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    resetLabel: String? = null,
    onReset: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (resetLabel != null && onReset != null) TextButton(onClick = onReset) { Text(resetLabel) }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onCancel) { Text(cancelLabel) }
        Button(onClick = onApply, shape = RoundedCornerShape(16.dp)) { Text(applyLabel) }
    }
}
