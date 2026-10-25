package app.echo.android.feature.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.design.EchoMotion
import app.echo.android.design.EchoSwitch
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun SettingsAutoMixCard(checked: Boolean, bypassed: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val active = checked && !bypassed
    val fill by animateColorAsState(
        if (active) scheme.primaryContainer.copy(alpha = 0.42f) else scheme.surfaceContainer,
        if (lightweight) snap() else tween(360), label = "automix-card-fill")
    val reveal by animateFloatAsState(if (active) 1f else 0f,
        if (lightweight) snap() else tween(680, easing = EchoMotion.Silk), label = "automix-curve-reveal")
    val shape = RoundedCornerShape(24.dp)
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp).clip(shape).background(fill)
        .border(1.dp, scheme.outlineVariant.copy(alpha = 0.55f), shape)
        .settingsSearchAnchor(stringResource(R.string.settings_smart_transition))
        .toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange)
        .padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(scheme.surface.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center) {
                EchoIcon(Icons.Rounded.AutoAwesome, null, Modifier.size(21.dp), tint = scheme.onSurface)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("AutoMix", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface)
                Text(stringResource(R.string.settings_smart_transition), style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant)
            }
            EchoSwitch(checked, onCheckedChange = null)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(when { !checked -> R.string.automix_off; bypassed -> R.string.automix_standby; else -> R.string.automix_on }),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
            Text(stringResource(R.string.automix_illustration), modifier = Modifier.widthIn(max = 100.dp),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant)
        }
        val outgoing = scheme.onSurfaceVariant
        val incoming = if (active) scheme.primary else scheme.onSurfaceVariant
        Canvas(Modifier.fillMaxWidth().height(72.dp).clearAndSetSemantics {}) {
            val left = 2.dp.toPx()
            val width = size.width - left * 2
            val top = 6.dp.toPx()
            val bottom = size.height - 6.dp.toPx()
            val height = bottom - top
            drawLine(outgoing.copy(alpha = 0.14f), Offset(left, bottom), Offset(left + width, bottom), 1.dp.toPx())
            fun curve(incomingCurve: Boolean): Path = Path().apply {
                repeat(65) { i ->
                    val p = i / 64f
                    val gain = if (incomingCurve) sin(p * PI / 2).toFloat() else cos(p * PI / 2).toFloat()
                    val x = left + width * p
                    val y = bottom - height * gain
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
            }
            val a = curve(false)
            val b = curve(true)
            val area = Path().apply { addPath(b); lineTo(left + width, bottom); lineTo(left, bottom); close() }
            drawPath(area, Brush.verticalGradient(listOf(incoming.copy(alpha = 0.16f * reveal), incoming.copy(alpha = 0f))))
            drawPath(a, outgoing.copy(alpha = 0.5f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            drawPath(b, incoming.copy(alpha = 0.45f + 0.55f * reveal), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            val p = 0.28f + reveal * 0.34f
            val x = left + width * p
            val y = bottom - height * sin(p * PI / 2).toFloat()
            drawLine(incoming.copy(alpha = 0.18f), Offset(x, top), Offset(x, bottom), 1.dp.toPx())
            drawCircle(incoming.copy(alpha = 0.1f), 8.dp.toPx(), Offset(x, y))
            drawCircle(incoming, 3.5.dp.toPx(), Offset(x, y))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.automix_current), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            Text(stringResource(R.string.automix_next), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        }
        Text(stringResource(if (checked && bypassed) R.string.settings_smart_transition_bypass else R.string.automix_description),
            style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    }
}
