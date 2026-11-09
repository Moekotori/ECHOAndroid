package app.echo.android.feature.settings

import app.echo.android.design.EchoIcon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
    val railColor = scheme.outlineVariant
    val nodeColor = if (stage.highlighted) scheme.primaryContainer else scheme.surfaceContainerHigh
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.width(34.dp).fillMaxHeight().drawBehind {
                val x = size.width / 2
                val center = 17.dp.toPx()
                drawLine(
                    railColor, Offset(x, if (index == 0) center else 0f),
                    Offset(x, if (last) center else size.height), 1.dp.toPx(),
                )
            },
        ) {
            Box(Modifier.size(34.dp).background(nodeColor, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center) {
                EchoIcon(stage.icon, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
        Column(Modifier.weight(1f).padding(top = 2.dp, bottom = if (last) 4.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text((index + 1).toString().padStart(2, '0'), style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace, color = scheme.onSurfaceVariant)
                Text(stage.label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            }
            Text(stage.value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (expanded && !stage.detail.isNullOrBlank()) SignalNote(stage.detail)
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
