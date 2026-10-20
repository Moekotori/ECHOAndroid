package app.echo.android.feature.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.design.EchoMotion
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.echoAccentColor
import app.echo.android.design.echoPressFeedback
import app.echo.android.design.echoTheme
import app.echo.android.design.rememberEchoHapticPerformer

/** One pickable choice: [thumbnail] is a tiny static drawing of what the option looks like. */
internal class SettingsOption(
    val id: String,
    val label: String,
    val thumbnail: DrawScope.(accent: Color) -> Unit,
)

/** Two-column gallery of option cards. A lone last card keeps its column width. */
@Composable
internal fun SettingsOptionGrid(
    options: List<SettingsOption>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { option ->
                    SettingsOptionCard(
                        option = option,
                        selected = option.id == selectedId,
                        onClick = { onSelect(option.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SettingsOptionCard(
    option: SettingsOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = LocalEchoDarkTheme.current
    val accent = echoAccentColor()
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val haptics = rememberEchoHapticPerformer()
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(18.dp)
    val plate by animateColorAsState(
        targetValue = when {
            selected -> accent.copy(alpha = if (dark) 0.16f else 0.10f)
            dark -> Color.White.copy(alpha = 0.05f)
            else -> echoTheme().muted.copy(alpha = 0.08f)
        },
        animationSpec = tween(if (lightweight) 0 else 220, easing = LyricsSettingsMotionEasing),
        label = "option-card-plate",
    )
    val ring by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.85f) else Color.Transparent,
        animationSpec = tween(if (lightweight) 0 else 220, easing = LyricsSettingsMotionEasing),
        label = "option-card-ring",
    )
    val check by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = if (lightweight) snap() else EchoMotion.silkFloat(280),
        label = "option-card-check",
    )
    Column(
        modifier = modifier
            .clip(shape)
            .background(plate)
            .border(BorderStroke(1.5.dp, ring), shape)
            .echoPressFeedback(interactionSource)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.RadioButton,
                onClick = {
                    if (!selected) haptics.tick()
                    onClick()
                },
            )
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(13.dp))) {
            Canvas(Modifier.matchParentSize()) { option.thumbnail(this, accent) }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 6.dp, end = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                option.label,
                modifier = Modifier.weight(1f),
                color = if (dark) Color.White else echoTheme().heading,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                Modifier.size(20.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.95f * check))
                    .graphicsLayer {
                        val s = 0.6f + 0.4f * check
                        scaleX = s
                        scaleY = s
                    },
                contentAlignment = Alignment.Center,
            ) {
                EchoIcon(
                    Icons.Rounded.Check, contentDescription = null,
                    tint = Color.White.copy(alpha = check),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

