package app.echo.android.feature.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoExpand
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoRepeatMode
import app.echo.android.model.playback.EchoSleepTimerMode
import app.echo.android.feature.player.R as L10nR

@Composable
internal fun PlaybackRepeatMenuControl(status: EchoPlaybackStatus, onSetRepeatMode: (EchoRepeatMode) -> Unit) {
    var repeatExpanded by remember { mutableStateOf(false) }
    PlaybackSettingsSection(
        icon = Icons.Rounded.Repeat,
        framed = false,
        compact = true,
        showDivider = false,
        revealOrder = 1,
        title = stringResource(L10nR.string.feature_player_playback_mode),
        detail = repeatModeLabel(status.repeatMode),
        expanded = repeatExpanded,
        onToggleExpanded = { repeatExpanded = !repeatExpanded },
    ) {
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(EchoRepeatMode.Off, EchoRepeatMode.All, EchoRepeatMode.One).forEach { mode ->
                PlaybackIconChoice(
                    icon = if (mode == EchoRepeatMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    description = repeatModeLabel(mode),
                    selected = status.repeatMode == mode,
                    slashed = mode == EchoRepeatMode.Off,
                    onClick = { onSetRepeatMode(mode) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
internal fun PlaybackSleepTimerMenuControl(
    status: EchoPlaybackStatus,
    onSetSleepTimer: (Int) -> Unit,
    onSetSleepTimerEndOfTrack: () -> Unit,
    onCancelSleepTimer: () -> Unit,
) {
    val customActive = isCustomSleepTimer(status.sleepTimerMode, status.sleepTimerMinutes)
    var timerExpanded by remember { mutableStateOf(false) }
    var showCustomSleepTimer by remember { mutableStateOf(false) }
    var customMinutes by remember {
        mutableIntStateOf(status.sleepTimerMinutes?.takeIf { it !in SleepTimerPresetMinutes } ?: SleepTimerCustomDefaultMinutes)
    }
    PlaybackSettingsSection(
        icon = Icons.Rounded.Bedtime,
        framed = false,
        compact = true,
        showDivider = false,
        revealOrder = 3,
        title = stringResource(L10nR.string.feature_player_sleep_timer_108738),
        detail = sleepTimerDetail(status),
        expanded = timerExpanded,
        onToggleExpanded = { timerExpanded = !timerExpanded },
        trailing = {
            if (status.sleepTimerMode != EchoSleepTimerMode.Off) TextButton(onClick = onCancelSleepTimer) {
                Text(stringResource(L10nR.string.playback_cancel_timer))
            }
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlaybackChoiceChip(
                text = stringResource(L10nR.string.feature_player_off_12ac24),
                selected = status.sleepTimerMode == EchoSleepTimerMode.Off,
                onClick = {
                    showCustomSleepTimer = false
                    onCancelSleepTimer()
                },
                modifier = Modifier.weight(1f),
            )
            PlaybackChoiceChip(
                text = stringResource(L10nR.string.feature_player_this_track_210ea7),
                selected = status.sleepTimerMode == EchoSleepTimerMode.EndOfTrack,
                onClick = {
                    showCustomSleepTimer = false
                    onSetSleepTimerEndOfTrack()
                },
                modifier = Modifier.weight(1f),
            )
            PlaybackChoiceChip(
                text = if (customActive) {
                    "${status.sleepTimerMinutes}m"
                } else {
                    stringResource(L10nR.string.feature_player_sleep_timer_custom)
                },
                selected = customActive,
                onClick = {
                    customMinutes = status.sleepTimerMinutes
                        ?.takeIf { it !in SleepTimerPresetMinutes }
                        ?: SleepTimerCustomDefaultMinutes
                    showCustomSleepTimer = !showCustomSleepTimer
                },
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SleepTimerPresetMinutes.forEach { minutes ->
                PlaybackChoiceChip(
                    text = "${minutes}m",
                    selected = status.sleepTimerMode == EchoSleepTimerMode.Timed &&
                        status.sleepTimerMinutes == minutes,
                    onClick = {
                        showCustomSleepTimer = false
                        onSetSleepTimer(minutes)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        EchoExpand(expanded = showCustomSleepTimer) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlaybackStepper(
                    valueLabel = stringResource(
                        L10nR.string.feature_player_sleep_timer_minutes_value,
                        customMinutes,
                    ),
                    decrementEnabled = customMinutes > SleepTimerMinMinutes,
                    incrementEnabled = customMinutes < SleepTimerMaxMinutes,
                    decrementDescription = stringResource(L10nR.string.feature_player_sleep_timer_custom),
                    incrementDescription = stringResource(L10nR.string.feature_player_sleep_timer_custom),
                    valueDescription = stringResource(
                        L10nR.string.feature_player_sleep_timer_minutes_value,
                        customMinutes,
                    ),
                    onDecrement = {
                        customMinutes = stepSleepTimerMinutes(customMinutes, -SleepTimerCustomStepMinutes)
                    },
                    onIncrement = {
                        customMinutes = stepSleepTimerMinutes(customMinutes, SleepTimerCustomStepMinutes)
                    },
                    modifier = Modifier.weight(1f),
                )
                PlaybackActionChip(
                    text = stringResource(L10nR.string.feature_player_sleep_timer_apply),
                    onClick = {
                        showCustomSleepTimer = false
                        onSetSleepTimer(customMinutes)
                    },
                )
            }
        }
    }
}
