package app.echo.android.feature.player

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoExpand
import app.echo.android.design.EchoSwitch
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.echoTheme
import app.echo.android.design.rememberEchoHapticPerformer
import app.echo.android.model.playback.*
import kotlin.math.abs
import app.echo.android.feature.player.R as L10nR

@Composable
internal fun PlaybackAdvancedSettings(
    appearance: PlayerAppearance,
    onAppearancePreview: (PlayerAppearance) -> Unit,
    onAppearanceCommit: () -> Unit,
    status: EchoPlaybackStatus,
    onSetPlaybackSpeed: (Float, Boolean) -> Unit,
    onSetReplayGain: (Boolean, Float) -> Unit,
    onAdjustReplayGainPreamp: (Float) -> Unit,
    replayGainScanState: EchoReplayGainScanState,
    onScanReplayGain: () -> Unit,
    lyricsOffsetMs: Long,
    onAdjustLyricsOffset: (Long) -> Unit,
    onResetLyricsOffset: () -> Unit,
    onOpenTrackTools: () -> Unit,
    onDismiss: () -> Unit,
) {
    val nightcore = isNightcorePlayback(status.playbackSpeed, status.playbackPitch)
    val dark = LocalEchoDarkTheme.current
    val haptics = rememberEchoHapticPerformer()
    var speedExpanded by remember { mutableStateOf(false) }
    var lyricsOffsetExpanded by remember { mutableStateOf(false) }
    PlayerAppearanceSettings(appearance, onAppearancePreview, onAppearanceCommit)
    PlaybackSettingsSection(
        icon = Icons.Rounded.Speed,
        framed = false,
        title = stringResource(L10nR.string.feature_player_speed_1d93fc),
        detail = listOf(
            formatPlaybackSpeedLabel(status.playbackSpeed),
            if (nightcore) {
                stringResource(L10nR.string.feature_player_nightcore)
            } else {
                stringResource(L10nR.string.playback_keep_pitch)
            },
        ).joinToString(" · "),
        expanded = speedExpanded,
        onToggleExpanded = { speedExpanded = !speedExpanded },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlaybackToggleChip(
                icon = Icons.Rounded.Speed,
                text = stringResource(L10nR.string.playback_keep_pitch),
                selected = !nightcore,
                onClick = {
                    onSetPlaybackSpeed(status.playbackSpeed, false)
                },
                modifier = Modifier.weight(1f),
                role = Role.RadioButton,
            )
            PlaybackToggleChip(
                icon = if (nightcore) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                text = stringResource(L10nR.string.feature_player_nightcore),
                selected = nightcore,
                onClick = {
                    onSetPlaybackSpeed(
                        playbackSpeedForNightcoreToggle(status.playbackSpeed, true),
                        true,
                    )
                },
                modifier = Modifier.weight(1f),
                role = Role.RadioButton,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            playbackSpeedChoices(nightcore).forEach { speed ->
                PlaybackChoiceChip(
                    text = formatPlaybackSpeedLabel(speed),
                    selected = abs(status.playbackSpeed - speed) < 0.01f,
                    onClick = { onSetPlaybackSpeed(speed, nightcore) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        TextButton(onClick = { onSetPlaybackSpeed(1f, false) }, enabled = nightcore || abs(status.playbackSpeed - 1f) > 0.01f) {
            Text(stringResource(L10nR.string.playback_reset_speed))
        }
    }

    PlaybackSettingsSection(
        icon = Icons.Rounded.GraphicEq,
        framed = false,
        title = stringResource(L10nR.string.feature_player_replay_gain),
        detail = if (status.replayGainEnabled) {
            listOf(
                stringResource(L10nR.string.feature_player_enabled_889420),
                replayGainModeLabel(status.replayGainMode),
                formatReplayGainDb(status.replayGainPreampDb),
            ).joinToString(" · ")
        } else {
            stringResource(L10nR.string.feature_player_disabled_3bd0d0)
        },
        trailing = {
            EchoSwitch(
                checked = status.replayGainEnabled,
                onCheckedChange = { enabled ->
                    haptics.confirm()
                    onSetReplayGain(enabled, status.replayGainPreampDb)
                },
                modifier = Modifier.semantics { contentDescription = "ReplayGain" },
            )
        },
    ) {
        EchoExpand(expanded = status.replayGainEnabled) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PlaybackStepper(
                    valueLabel = "${stringResource(L10nR.string.feature_player_replay_gain_preamp)} ${formatReplayGainDb(status.replayGainPreampDb)}",
                    decrementEnabled = canLowerReplayGainPreamp(status.replayGainPreampDb),
                    incrementEnabled = canRaiseReplayGainPreamp(status.replayGainPreampDb),
                    decrementDescription = stringResource(L10nR.string.feature_player_replay_gain_preamp_down),
                    incrementDescription = stringResource(L10nR.string.feature_player_replay_gain_preamp_up),
                    valueDescription = stringResource(L10nR.string.feature_player_replay_gain_reset_preamp),
                    onDecrement = { onAdjustReplayGainPreamp(-ReplayGainPreampStepDb) },
                    onIncrement = { onAdjustReplayGainPreamp(ReplayGainPreampStepDb) },
                    onValueClick = {
                        if (abs(status.replayGainPreampDb) >= 0.01f) {
                            onSetReplayGain(true, 0f)
                        }
                    },
                    decrementIcon = Icons.Rounded.Remove,
                    incrementIcon = Icons.Rounded.Add,
                )
                Text(
                    stringResource(
                        L10nR.string.feature_player_tag_status_replaygaintrackgaindb_let_formatreplaygaindb_unread_p_9c7a19,
                        status.replayGainTrackGainDb?.let(::formatReplayGainDb)
                            ?: stringResource(
                                if (status.replayGainTagsLoaded) {
                                    L10nR.string.feature_player_replay_gain_none
                                } else {
                                    L10nR.string.feature_player_replay_gain_unread
                                },
                            ),
                        formatReplayGainDb(status.replayGainPreampDb),
                    ),
                    color = if (dark) Color.White.copy(alpha = 0.62f) else echoTheme().muted,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                )
                TextButton(
                    onClick = onScanReplayGain,
                    enabled = replayGainScanState !is EchoReplayGainScanState.Scanning &&
                        status.track != null,
                ) {
                    Text(replayGainScanLabel(replayGainScanState))
                }
            }
        }
    }

    PlaybackSettingsSection(
        icon = Icons.Rounded.Lyrics,
        framed = false,
        title = stringResource(L10nR.string.feature_player_lyrics_offset),
        detail = formatLyricsOffset(lyricsOffsetMs),
        expanded = lyricsOffsetExpanded,
        onToggleExpanded = { lyricsOffsetExpanded = !lyricsOffsetExpanded },
    ) {
        PlaybackStepper(
            valueLabel = formatLyricsOffset(lyricsOffsetMs),
            decrementEnabled = true,
            incrementEnabled = true,
            decrementDescription = stringResource(L10nR.string.feature_player_lyrics_earlier_by_0_25s_0605b3),
            incrementDescription = stringResource(L10nR.string.feature_player_lyrics_later_by_0_25s_f31341),
            valueDescription = stringResource(L10nR.string.feature_player_reset_lyrics_offset_df9dcc),
            onDecrement = { onAdjustLyricsOffset(-LyricsOffsetStepMs) },
            onIncrement = { onAdjustLyricsOffset(LyricsOffsetStepMs) },
            onValueClick = if (lyricsOffsetMs != 0L) onResetLyricsOffset else null,
            decrementIcon = Icons.Rounded.FastRewind,
            incrementIcon = Icons.Rounded.FastForward,
        )
        TextButton(onClick = onResetLyricsOffset, enabled = lyricsOffsetMs != 0L) {
            Text(stringResource(L10nR.string.feature_player_reset_lyrics_offset_df9dcc))
        }
    }
    if (status.track != null && !app.echo.android.model.radio.EchoRadioStation.isRadio(status.track?.id)) {
        PlaybackActionRow(
            icon = Icons.Rounded.Settings,
            text = stringResource(L10nR.string.track_tools_title),
            framed = false,
            onClick = { onDismiss(); onOpenTrackTools() },
        )
    }
}

@Composable
private fun replayGainScanLabel(state: EchoReplayGainScanState): String = when (state) {
    EchoReplayGainScanState.Idle -> stringResource(L10nR.string.feature_player_replay_gain_scan)
    EchoReplayGainScanState.Scanning -> stringResource(L10nR.string.feature_player_replay_gain_scanning)
    is EchoReplayGainScanState.Written -> stringResource(
        L10nR.string.feature_player_replay_gain_scanned,
        formatReplayGainDb(state.gainDb),
    )
    is EchoReplayGainScanState.Failed -> stringResource(
        when (state.reason) {
            EchoReplayGainScanFailure.NotLocal -> L10nR.string.feature_player_replay_gain_scan_not_local
            EchoReplayGainScanFailure.Unsupported -> L10nR.string.feature_player_replay_gain_scan_unsupported
            EchoReplayGainScanFailure.DecodeFailed -> L10nR.string.feature_player_replay_gain_scan_decode
            EchoReplayGainScanFailure.WriteFailed -> L10nR.string.feature_player_replay_gain_scan_write
        },
    )
}

@Composable
private fun replayGainModeLabel(mode: EchoReplayGainMode): String = when (mode) {
    EchoReplayGainMode.Auto -> stringResource(L10nR.string.feature_player_replay_gain_auto)
    EchoReplayGainMode.Track -> stringResource(L10nR.string.feature_player_replay_gain_track)
    EchoReplayGainMode.Album -> stringResource(L10nR.string.feature_player_replay_gain_album)
}
