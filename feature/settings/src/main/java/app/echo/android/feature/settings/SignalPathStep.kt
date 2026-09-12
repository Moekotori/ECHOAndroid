package app.echo.android.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun SignalPathStep(
    index: String,
    label: String,
    value: String,
    detail: String?,
    active: Boolean,
    last: Boolean = false,
    icon: ImageVector = Icons.Rounded.AudioFile,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.width(32.dp).fillMaxHeight().drawBehind {
                val centerX = size.width / 2f
                val nodeY = 16.dp.toPx()
                val rail = scheme.primary.copy(alpha = 0.22f)
                if (index != "01") drawLine(rail, Offset(centerX, 0f), Offset(centerX, nodeY), 1.dp.toPx())
                if (!last) drawLine(rail, Offset(centerX, nodeY), Offset(centerX, size.height), 1.dp.toPx())
            },
        ) {
            Surface(
                shape = CircleShape,
                color = scheme.surfaceContainerLow,
                contentColor = if (active) scheme.primary else scheme.onSurfaceVariant,
                border = BorderStroke(1.dp, if (active) scheme.primary.copy(alpha = 0.36f) else scheme.outlineVariant),
            ) {
                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Column(
            Modifier.weight(1f).padding(top = 2.dp, bottom = if (last) 0.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                Text(index, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
            if (!detail.isNullOrBlank() && detail != value) SignalNote(detail)
        }
    }
}
