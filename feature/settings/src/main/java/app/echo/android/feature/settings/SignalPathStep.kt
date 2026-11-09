package app.echo.android.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.design.EchoMotion
import app.echo.android.design.LocalEchoEffectivePerformanceMode

@Composable
internal fun SignalPathStep(
    stage: SignalPathStage,
    index: Int,
    last: Boolean,
    expanded: Boolean,
    accent: Color,
    onToggle: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val first = index == 0
    val endpoint = first || last
    val railColor = accent.copy(alpha = 0.33f)
    val nodeSize = 32.dp
    val nodeInset = 4.dp
    val nodeTop = 8.dp
    val nodeGap = 12.dp
    val nodeShape = if (endpoint) RoundedCornerShape(11.dp) else CircleShape
    val nodeBase = if (stage.highlighted || expanded) {
        lerp(scheme.surfaceContainerLow, accent, if (expanded) 0.19f else 0.10f)
    } else scheme.surfaceContainerLow
    val nodeRim = accent.copy(alpha = when {
        expanded -> 0.76f
        stage.highlighted || endpoint -> 0.56f
        else -> 0.27f
    })
    val animateDisclosure = onToggle != null && !LocalEchoEffectivePerformanceMode.current.isLightweight
    val detailsAvailable = stage.facts.isNotEmpty() || !stage.detail.isNullOrBlank()
    val headerAction = if (onToggle == null) Modifier else Modifier.clickable(
        role = Role.Button,
        onClickLabel = stringResource(if (expanded) R.string.path_hide_details else R.string.path_show_details),
        onClick = onToggle,
    )
    Column(Modifier.fillMaxWidth().drawBehind {
        val inset = (nodeInset + nodeSize / 2).toPx()
        val x = if (layoutDirection == LayoutDirection.Ltr) inset else size.width - inset
        val nodeCenter = (nodeTop + nodeSize / 2).toPx()
        val start = Offset(x, if (first) nodeCenter else 0f)
        val end = Offset(x, if (last) nodeCenter else size.height)
        drawLine(accent.copy(alpha = 0.06f), start, end, strokeWidth = 6.dp.toPx())
        drawLine(railColor, start, end, strokeWidth = 1.5.dp.toPx())
    }) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                .then(headerAction).padding(horizontal = nodeInset),
            horizontalArrangement = Arrangement.spacedBy(nodeGap),
        ) {
            Box(
                Modifier.padding(top = nodeTop).size(nodeSize)
                    .background(nodeBase, nodeShape)
                    .border(1.dp, nodeRim, nodeShape),
                contentAlignment = Alignment.Center,
            ) {
                EchoIcon(stage.icon, contentDescription = null, modifier = Modifier.size(17.dp),
                    tint = if (stage.highlighted) accent else scheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                Text(stage.label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                Text(stage.value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
            }
            if (onToggle != null) {
                val targetAngle = if (expanded) 180f else 0f
                val angle = if (animateDisclosure) {
                    animateFloatAsState(targetAngle, tween(200, easing = EchoMotion.Silk), label = "signal-path-chevron")
                } else null
                EchoIcon(
                    Icons.Rounded.ExpandMore, contentDescription = null,
                    modifier = Modifier.padding(top = 15.dp).size(18.dp)
                        .graphicsLayer { rotationZ = angle?.value ?: targetAngle },
                    tint = if (expanded) scheme.onSurface else scheme.onSurfaceVariant,
                )
            }
        }
        val detailsModifier = Modifier.padding(start = nodeInset + nodeSize + nodeGap, end = 4.dp, bottom = 8.dp)
        if (animateDisclosure) {
            AnimatedVisibility(
                visible = expanded && detailsAvailable,
                enter = expandVertically(tween(220, easing = EchoMotion.Silk), expandFrom = Alignment.Top) +
                    fadeIn(tween(160, easing = EchoMotion.Silk)),
                exit = shrinkVertically(tween(170, easing = EchoMotion.SilkExit), shrinkTowards = Alignment.Top) +
                    fadeOut(tween(110, easing = EchoMotion.SilkExit)),
            ) {
                SignalPathDetails(stage, detailsModifier)
            }
        } else if (expanded && detailsAvailable) {
            SignalPathDetails(stage, detailsModifier)
        }
    }
}

@Composable
private fun SignalPathDetails(stage: SignalPathStage, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier.fillMaxWidth().background(scheme.surfaceContainerHigh, shape)
            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.48f), shape).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        stage.facts.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, Modifier.weight(0.44f), style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant)
                Text(value, Modifier.weight(0.56f), style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium)
            }
        }
        stage.detail?.takeIf { it.isNotBlank() }?.let {
            if (stage.facts.isNotEmpty()) HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.55f))
            SignalNote(it)
        }
    }
}
