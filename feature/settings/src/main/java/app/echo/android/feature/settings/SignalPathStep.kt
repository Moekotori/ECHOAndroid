package app.echo.android.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun SignalPathStep(stage: SignalPathStage, index: Int, last: Boolean, expanded: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val tint = if (stage.highlighted) scheme.primary else scheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier.width(36.dp).fillMaxHeight().drawBehind {
                if (!last) drawLine(
                    scheme.outlineVariant, Offset(size.width / 2, 56.dp.toPx()),
                    Offset(size.width / 2, size.height - 6.dp.toPx()), 1.dp.toPx(),
                )
            }, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                Icon(stage.icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = tint)
            }
            Text((index + 1).toString().padStart(2, '0'), Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = scheme.onSurfaceVariant)
        }
        Column(Modifier.weight(1f).padding(bottom = if (last) 0.dp else 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stage.label, style = MaterialTheme.typography.labelMedium, color = tint)
            Text(stage.value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (!stage.detail.isNullOrBlank()) SignalNote(stage.detail)
            if (expanded && stage.facts.isNotEmpty()) {
                Column(Modifier.padding(top = 5.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                    stage.facts.forEach { (label, value) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(label, Modifier.weight(0.42f), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                            Text(value, Modifier.weight(0.58f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}
