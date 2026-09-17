package app.echo.android.feature.player

import app.echo.android.feature.player.R as L10nR
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoExpand
import app.echo.android.design.EchoSwitch
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.echoAccentColor
import app.echo.android.design.echoClickable
import app.echo.android.design.echoDarkGlassBorder
import app.echo.android.design.echoPressFeedback
import app.echo.android.design.echoTheme
import app.echo.android.design.rememberEchoHapticPerformer

@Composable
internal fun PlaybackSettingsSection(
    icon: ImageVector,
    title: String,
    detail: String,
    expanded: Boolean = true,
    onToggleExpanded: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val dark = LocalEchoDarkTheme.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(if (dark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.62f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().then(if (onToggleExpanded != null)
                Modifier.echoClickable(role = Role.Button, onClick = onToggleExpanded) else Modifier)
                .heightIn(min = 48.dp).padding(horizontal = 2.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, null, tint = echoAccentColor(), modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = if (dark) Color.White else echoTheme().heading,
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (detail.isNotBlank()) Text(detail, color = if (dark) Color.White.copy(alpha = 0.65f) else echoTheme().muted,
                    style = MaterialTheme.typography.bodySmall)
            }
            trailing()
            if (onToggleExpanded != null) Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = stringResource(if (expanded) L10nR.string.playback_collapse else L10nR.string.playback_expand),
                tint = echoTheme().muted, modifier = Modifier.size(20.dp))
        }
        EchoExpand(expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
        }
    }
}

@Composable
internal fun RowScope.PlaybackChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaybackChoiceChip(
        text = text,
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        fillWidth = true,
    )
}

@Composable
internal fun PlaybackChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = false,
) {
    val dark = LocalEchoDarkTheme.current
    val accent = echoAccentColor()
    val haptics = rememberEchoHapticPerformer()
    val interactionSource = remember { MutableInteractionSource() }
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            accent.copy(alpha = if (dark) 0.22f else 0.16f)
        } else {
            if (dark) echoTheme().panel.copy(alpha = 0.46f) else Color.White.copy(alpha = 0.56f)
        },
        animationSpec = tween(durationMillis = if (app.echo.android.design.LocalEchoEffectivePerformanceMode.current.isLightweight) 0 else 180, easing = LyricsSettingsMotionEasing),
        label = "playback-choice-container",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.72f) else if (dark) echoTheme().glassBorder else Color.Transparent,
        animationSpec = tween(durationMillis = if (app.echo.android.design.LocalEchoEffectivePerformanceMode.current.isLightweight) 0 else 180, easing = LyricsSettingsMotionEasing),
        label = "playback-choice-border",
    )
    Box(
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor)
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(14.dp))
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
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (dark) Color.White else echoTheme().heading,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun PlaybackToggleChip(
    icon: ImageVector,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: Role = Role.Switch,
) {
    val dark = LocalEchoDarkTheme.current
    val accent = echoAccentColor()
    val haptics = rememberEchoHapticPerformer()
    val interactionSource = remember { MutableInteractionSource() }
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            accent.copy(alpha = if (dark) 0.24f else 0.18f)
        } else {
            if (dark) echoTheme().panel.copy(alpha = 0.46f) else Color.White.copy(alpha = 0.56f)
        },
        animationSpec = tween(durationMillis = if (app.echo.android.design.LocalEchoEffectivePerformanceMode.current.isLightweight) 0 else 180, easing = LyricsSettingsMotionEasing),
        label = "playback-toggle-container",
    )
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor)
            .border(
                BorderStroke(1.dp, if (selected) accent.copy(alpha = 0.72f) else if (dark) echoTheme().glassBorder else Color.Transparent),
                RoundedCornerShape(14.dp),
            )
            .echoPressFeedback(interactionSource)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = role,
                onClick = {
                    haptics.tick()
                    onClick()
                },
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (dark) Color.White.copy(alpha = if (selected) 0.96f else 0.78f) else echoTheme().heading,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text,
            color = if (dark) Color.White else echoTheme().heading,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun PlaybackToggleRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    framed: Boolean = true,
    enabled: Boolean = true,
) {
    val dark = LocalEchoDarkTheme.current
    val accent = echoAccentColor()
    val interactionSource = remember { MutableInteractionSource() }
    val containerColor by animateColorAsState(
        targetValue = if (!framed) {
            Color.Transparent
        } else if (checked) {
            accent.copy(alpha = if (dark) 0.22f else 0.16f)
        } else {
            if (dark) echoTheme().panel.copy(alpha = 0.50f) else Color.White.copy(alpha = 0.48f)
        },
        animationSpec = tween(durationMillis = if (app.echo.android.design.LocalEchoEffectivePerformanceMode.current.isLightweight) 0 else 180, easing = LyricsSettingsMotionEasing),
        label = "playback-toggle-row",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor)
            .then(
                if (framed) {
                    Modifier.border(
                        BorderStroke(
                            1.dp,
                            if (checked) accent.copy(alpha = 0.62f) else if (dark) echoTheme().glassBorder else Color.Transparent,
                        ),
                        RoundedCornerShape(14.dp),
                    )
                } else {
                    Modifier
                },
            )
            .echoPressFeedback(interactionSource)
            .toggleable(
                value = checked,
                enabled = enabled,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = if (framed) 14.dp else 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = (if (dark) Color.White else echoTheme().heading).copy(alpha = if (enabled) 1f else 0.38f),
            modifier = Modifier.size(18.dp),
        )
        Text(
            title,
            modifier = Modifier.weight(1f),
            color = (if (dark) Color.White else echoTheme().heading).copy(alpha = if (enabled) 1f else 0.38f),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        EchoSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
internal fun PlaybackActionRow(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    detail: String? = null,
    modifier: Modifier = Modifier,
    framed: Boolean = true,
) {
    val dark = LocalEchoDarkTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (!framed) Color.Transparent
                else if (dark) echoTheme().panel.copy(alpha = 0.50f)
                else Color.White.copy(alpha = 0.48f),
            )
            .then(
                if (framed) {
                    Modifier.border(
                        if (dark) echoDarkGlassBorder() else BorderStroke(1.dp, Color.Transparent),
                        RoundedCornerShape(14.dp),
                    )
                } else {
                    Modifier
                },
            )
            .echoClickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = if (framed) 14.dp else 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (dark) Color.White else echoTheme().heading, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(text, color = if (dark) Color.White else echoTheme().heading,
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (!detail.isNullOrBlank()) Text(detail, color = if (dark) Color.White.copy(alpha = 0.65f) else echoTheme().muted,
                style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null,
            tint = echoTheme().muted, modifier = Modifier.size(20.dp))

    }
}

@Composable
internal fun PlaybackActionChip(
    text: String,
    onClick: () -> Unit,
) {
    val dark = LocalEchoDarkTheme.current
    val accent = echoAccentColor()
    val haptics = rememberEchoHapticPerformer()
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = if (dark) 0.28f else 0.18f))
            .border(BorderStroke(1.dp, accent.copy(alpha = 0.62f)), RoundedCornerShape(14.dp))
            .echoClickable(role = Role.Button, onClick = {
                haptics.confirm()
                onClick()
            })
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (dark) Color.White else echoTheme().heading,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun PlaybackStepper(
    valueLabel: String,
    decrementEnabled: Boolean,
    incrementEnabled: Boolean,
    decrementDescription: String,
    incrementDescription: String,
    valueDescription: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    onValueClick: (() -> Unit)? = null,
    decrementIcon: ImageVector = Icons.Rounded.Remove,
    incrementIcon: ImageVector = Icons.Rounded.Add,
) {
    val dark = LocalEchoDarkTheme.current
    val haptics = rememberEchoHapticPerformer()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (dark) echoTheme().panel.copy(alpha = 0.50f) else Color.White.copy(alpha = 0.48f))
            .border(
                if (dark) echoDarkGlassBorder() else BorderStroke(1.dp, Color.Transparent),
                RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaybackStepperButton(
            icon = decrementIcon,
            description = decrementDescription,
            enabled = decrementEnabled,
            onClick = {
                haptics.tick()
                onDecrement()
            },
        )
        Text(
            valueLabel,
            modifier = Modifier
                .weight(1f)
                .then(
                    if (onValueClick != null) {
                        Modifier.echoClickable(onClickLabel = valueDescription, onClick = {
                            haptics.tick()
                            onValueClick()
                        })
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 8.dp, vertical = 10.dp),
            color = if (dark) Color.White else echoTheme().heading,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        PlaybackStepperButton(
            icon = incrementIcon,
            description = incrementDescription,
            enabled = incrementEnabled,
            onClick = {
                haptics.tick()
                onIncrement()
            },
        )
    }
}

@Composable
internal fun PlaybackStepperButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val dark = LocalEchoDarkTheme.current
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .echoClickable(enabled = enabled, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = (if (dark) Color.White else echoTheme().heading).copy(alpha = if (enabled) 0.92f else 0.28f),
            modifier = Modifier.size(20.dp),
        )
    }
}

