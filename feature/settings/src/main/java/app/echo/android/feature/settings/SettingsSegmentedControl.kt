package app.echo.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal data class SettingsSegment(
    val label: String,
    val selected: Boolean,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/** A small, always-visible exclusive choice: the selected segment is filled, the rest stay quiet. */
@Composable
internal fun SettingsSegmentedControl(
    segments: List<SettingsSegment>,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(3.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        segments.forEach { segment ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (segment.selected) scheme.primaryContainer else Color.Transparent)
                    .selectable(
                        selected = segment.selected,
                        enabled = segment.enabled,
                        role = Role.RadioButton,
                        onClick = segment.onClick,
                    )
                    .alpha(if (segment.enabled || segment.selected) 1f else 0.5f)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    segment.label,
                    color = if (segment.selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (segment.selected) FontWeight.SemiBold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
