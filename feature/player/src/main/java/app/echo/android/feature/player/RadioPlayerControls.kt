package app.echo.android.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import app.echo.android.design.EchoIcon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.design.rememberEchoHapticPerformer

@Composable
internal fun RadioListeningActions(
    colors: RadioPlayerColors,
    timerActive: Boolean,
    onSetSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit,
) {
    var timerMenuVisible by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = 58.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EchoIcon(Icons.Outlined.Headphones, contentDescription = null, tint = colors.ink, modifier = Modifier.size(28.dp))
            Text(stringResource(R.string.radio_online), color = colors.ink,
                fontFamily = RecordSleeveStyle.BodyFont, fontSize = 14.sp, lineHeight = 20.sp)
        }
        Box(Modifier.width(0.5.dp).height(32.dp).background(colors.rule))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            RadioTextAction(Icons.Outlined.Schedule, stringResource(R.string.radio_timer),
                if (timerActive) colors.accent else colors.ink, { timerMenuVisible = true })
            DropdownMenu(expanded = timerMenuVisible, onDismissRequest = { timerMenuVisible = false }) {
                SleepTimerPresetMinutes.forEach { minutes ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.radio_timer_minutes, minutes)) },
                        onClick = { timerMenuVisible = false; onSetSleepTimer(minutes) },
                    )
                }
                if (timerActive) DropdownMenuItem(
                    text = { Text(stringResource(R.string.radio_timer_cancel)) },
                    onClick = { timerMenuVisible = false; onCancelSleepTimer() },
                )
            }
        }
    }
}

@Composable
internal fun RadioTransport(colors: RadioPlayerColors, isPlaying: Boolean,
    onPrevious: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit) {
    val haptics = rememberEchoHapticPerformer()
    Row(Modifier.fillMaxWidth().height(92.dp), horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically) {
        PlayerControlButton(PlayerControlIcons.Previous, stringResource(R.string.feature_player_previous_af0264),
            { haptics.tick(); onPrevious() }, touchSize = 56.dp, iconSize = 32.dp, tint = colors.ink)
        PlayerControlButton(if (isPlaying) PlayerControlIcons.Pause else PlayerControlIcons.Play,
            stringResource(R.string.feature_player_play_or_pause_37a70f),
            { haptics.confirm(); onPlayPause() }, touchSize = 72.dp, iconSize = 54.dp, tint = colors.accent)
        PlayerControlButton(PlayerControlIcons.Next, stringResource(R.string.feature_player_next_d67904),
            { haptics.tick(); onNext() }, touchSize = 56.dp, iconSize = 32.dp, tint = colors.ink)
    }
}

@Composable
internal fun RadioUtilities(colors: RadioPlayerColors, onOpenQueue: () -> Unit,
    onCast: (() -> Unit)?, castActive: Boolean) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        RadioTextAction(PlayerControlIcons.Queue, stringResource(R.string.radio_queue),
            colors.ink, onOpenQueue)
        if (onCast != null) RadioTextAction(PlayerControlIcons.Cast,
            stringResource(if (castActive) R.string.feature_player_cast_active else R.string.radio_cast),
            if (castActive) colors.accent else colors.ink, onCast)
    }
}

@Composable
private fun RadioTextAction(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Row(Modifier.heightIn(min = 48.dp).clickable(
        interactionSource = remember { MutableInteractionSource() }, indication = null,
        role = Role.Button, onClick = onClick,
    ).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        EchoIcon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(27.dp))
        Text(label, color = tint, fontFamily = RecordSleeveStyle.BodyFont, fontSize = 14.sp, lineHeight = 20.sp)
    }
}
