package app.echo.android.feature.player

import app.echo.android.feature.player.R as L10nR
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoExpand
import app.echo.android.design.EchoMotion
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.echoAccentColor
import app.echo.android.design.echoClickable
import app.echo.android.design.echoDarkGlassBorder
import app.echo.android.design.echoPressFeedback
import app.echo.android.design.echoTheme
import app.echo.android.design.rememberEchoHapticPerformer
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoRepeatMode
import app.echo.android.model.playback.EchoReplayGainMode
import app.echo.android.model.playback.EchoReplayGainScanFailure
import app.echo.android.model.playback.EchoReplayGainScanState
import app.echo.android.model.playback.EchoReplayGainPreampMaxDb
import app.echo.android.model.playback.EchoReplayGainPreampMinDb
import app.echo.android.model.playback.EchoSleepTimerMode
import kotlin.math.abs
import kotlin.math.roundToInt

internal val PlaybackSpeedOptions = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
internal val SleepTimerPresetMinutes = listOf(15, 30, 60)
internal const val SleepTimerCustomDefaultMinutes = 45
internal const val SleepTimerMinMinutes = 1
internal const val SleepTimerMaxMinutes = 180
internal const val SleepTimerCustomStepMinutes = 5
internal const val LyricsOffsetStepMs = 250L
internal const val ReplayGainPreampStepDb = 1f
internal const val NightcoreMinSpeed = 1.25f

internal fun isNightcorePlayback(speed: Float, pitch: Float): Boolean =
    speed > 1.01f && abs(pitch - speed) < 0.01f

internal fun playbackSpeedChoices(nightcore: Boolean): List<Float> =
    if (nightcore) PlaybackSpeedOptions.filter { it >= NightcoreMinSpeed } else PlaybackSpeedOptions

internal fun playbackSpeedForNightcoreToggle(currentSpeed: Float, nightcore: Boolean): Float =
    if (nightcore) currentSpeed.coerceAtLeast(NightcoreMinSpeed) else currentSpeed

internal fun isCustomSleepTimer(mode: EchoSleepTimerMode, minutes: Int?): Boolean =
    mode == EchoSleepTimerMode.Timed && minutes != null && minutes !in SleepTimerPresetMinutes

internal fun coerceSleepTimerMinutes(value: Int): Int =
    value.coerceIn(SleepTimerMinMinutes, SleepTimerMaxMinutes)

internal fun stepSleepTimerMinutes(current: Int, delta: Int): Int =
    coerceSleepTimerMinutes(current + delta)

internal fun formatPlaybackSpeedLabel(speed: Float): String {
    val rounded = (speed * 100f).roundToInt() / 100f
    return if (abs(rounded - rounded.toInt()) < 0.01f) {
        "${rounded.toInt()}x"
    } else {
        "${"%.2f".format(rounded).trimEnd('0').trimEnd('.')}x"
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

internal fun formatReplayGainDb(value: Float): String {
    val rounded = (value * 10f).roundToInt() / 10f
    val sign = if (rounded > 0f) "+" else ""
    return if (abs(rounded - rounded.toInt()) < 0.01f) {
        "$sign${rounded.toInt()}dB"
    } else {
        "$sign${"%.1f".format(rounded)}dB"
    }
}

internal fun formatSleepTimerRemaining(remainingMs: Long): String {
    val totalMinutes = ((remainingMs + 59_999L) / 60_000L).coerceAtLeast(1L)
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) {
        "${hours}h ${minutes}m"
    } else {
        "${minutes}m"
    }
}

internal fun canLowerReplayGainPreamp(preampDb: Float): Boolean =
    preampDb - ReplayGainPreampStepDb >= EchoReplayGainPreampMinDb - 0.01f

internal fun canRaiseReplayGainPreamp(preampDb: Float): Boolean =
    preampDb + ReplayGainPreampStepDb <= EchoReplayGainPreampMaxDb + 0.01f

@Composable
internal fun PlaybackSettingsDrawer(
    visible: Boolean,
    status: EchoPlaybackStatus,
    onCycleRepeatMode: () -> Unit,
    onToggleShuffle: () -> Unit,
    onSetPlaybackSpeed: (Float, Boolean) -> Unit,
    onSetSleepTimer: (Int) -> Unit,
    onSetSleepTimerEndOfTrack: () -> Unit = {},
    onCancelSleepTimer: () -> Unit,
    onSetReplayGain: (Boolean, Float) -> Unit,
    onSetReplayGainMode: (EchoReplayGainMode) -> Unit = {},
    onAdjustReplayGainPreamp: (Float) -> Unit,
    replayGainScanState: EchoReplayGainScanState = EchoReplayGainScanState.Idle,
    onScanReplayGain: () -> Unit = {},
    onSetSkipSilenceEnabled: (Boolean) -> Unit,
    lyricsOffsetMs: Long,
    onAdjustLyricsOffset: (Long) -> Unit,
    onResetLyricsOffset: () -> Unit,
    onOpenQueue: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = visible, onBack = onDismiss)
    val drawerState = remember { MutableTransitionState(false) }
    drawerState.targetState = visible
    AnimatedVisibility(
        visibleState = drawerState,
        enter = fadeIn(tween(durationMillis = 90, easing = LyricsSettingsMotionEasing)),
        exit = fadeOut(tween(durationMillis = 180, easing = LyricsSettingsMotionEasing)),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.18f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
            AnimatedVisibility(
                visibleState = drawerState,
                enter = slideInVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ) { it } +
                    expandVertically(
                        expandFrom = Alignment.Bottom,
                        animationSpec = EchoMotion.silkSize(360),
                    ) +
                    fadeIn(tween(durationMillis = 260, delayMillis = 35, easing = LyricsSettingsMotionEasing)) +
                    scaleIn(
                        initialScale = 0.965f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ),
                exit = slideOutVertically(EchoMotion.silkOffset(260)) { it } +
                    shrinkVertically(
                        shrinkTowards = Alignment.Bottom,
                        animationSpec = EchoMotion.silkSize(260),
                    ) +
                    fadeOut(tween(durationMillis = 160, easing = LyricsSettingsMotionEasing)) +
                    scaleOut(
                        targetScale = 0.98f,
                        animationSpec = EchoMotion.silkFloat(260),
                    ),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                PlaybackSettingsSheet(
                    status = status,
                    onCycleRepeatMode = onCycleRepeatMode,
                    onToggleShuffle = onToggleShuffle,
                    onSetPlaybackSpeed = onSetPlaybackSpeed,
                    onSetSleepTimer = onSetSleepTimer,
                    onSetSleepTimerEndOfTrack = onSetSleepTimerEndOfTrack,
                    onCancelSleepTimer = onCancelSleepTimer,
                    onSetReplayGain = onSetReplayGain,
                    onSetReplayGainMode = onSetReplayGainMode,
                    onAdjustReplayGainPreamp = onAdjustReplayGainPreamp,
                    replayGainScanState = replayGainScanState,
                    onScanReplayGain = onScanReplayGain,
                    onSetSkipSilenceEnabled = onSetSkipSilenceEnabled,
                    lyricsOffsetMs = lyricsOffsetMs,
                    onAdjustLyricsOffset = onAdjustLyricsOffset,
                    onResetLyricsOffset = onResetLyricsOffset,
                    onOpenQueue = {
                        onDismiss()
                        onOpenQueue()
                    },
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun PlaybackSettingsSheet(
    status: EchoPlaybackStatus,
    onCycleRepeatMode: () -> Unit,
    onToggleShuffle: () -> Unit,
    onSetPlaybackSpeed: (Float, Boolean) -> Unit,
    onSetSleepTimer: (Int) -> Unit,
    onSetSleepTimerEndOfTrack: () -> Unit,
    onCancelSleepTimer: () -> Unit,
    onSetReplayGain: (Boolean, Float) -> Unit,
    onSetReplayGainMode: (EchoReplayGainMode) -> Unit,
    onAdjustReplayGainPreamp: (Float) -> Unit,
    replayGainScanState: EchoReplayGainScanState = EchoReplayGainScanState.Idle,
    onScanReplayGain: () -> Unit = {},
    onSetSkipSilenceEnabled: (Boolean) -> Unit,
    lyricsOffsetMs: Long,
    onAdjustLyricsOffset: (Long) -> Unit,
    onResetLyricsOffset: () -> Unit,
    onOpenQueue: () -> Unit,
    onDismiss: () -> Unit,
) {
    val nightcore = isNightcorePlayback(status.playbackSpeed, status.playbackPitch)
    val dark = LocalEchoDarkTheme.current
    val haptics = rememberEchoHapticPerformer()
    val customActive = isCustomSleepTimer(status.sleepTimerMode, status.sleepTimerMinutes)
    var showCustomSleepTimer by remember { mutableStateOf(false) }
    var customMinutes by remember {
        mutableIntStateOf(status.sleepTimerMinutes?.takeIf { it !in SleepTimerPresetMinutes } ?: SleepTimerCustomDefaultMinutes)
    }
    val panelShape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    val titleColor = if (dark) Color.White else echoTheme().heading
    val mutedColor = if (dark) Color.White.copy(alpha = 0.76f) else echoTheme().muted
    val accentColor = echoAccentColor()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight * 0.78f)
                .navigationBarsPadding()
                .clip(panelShape)
                .background(
                    if (dark) {
                        Brush.verticalGradient(
                            listOf(
                                echoTheme().panel.copy(alpha = 0.98f),
                                echoTheme().ink.copy(alpha = 0.98f),
                                echoTheme().night.copy(alpha = 0.98f),
                            ),
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFFF7F5F6).copy(alpha = 0.98f),
                                Color(0xFFEFECEE).copy(alpha = 0.98f),
                            ),
                        )
                    },
                )
                .border(
                    BorderStroke(1.dp, if (dark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.72f)),
                    panelShape,
                ),
        ) {
            Column(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 48.dp, height = 5.dp)
                        .clip(CircleShape)
                        .background(if (dark) Color.White.copy(alpha = 0.28f) else Color(0xFF2A282E).copy(alpha = 0.22f)),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Settings,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(L10nR.string.feature_player_playback_settings_651436),
                            color = titleColor,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            playbackSettingsSummary(status),
                            color = mutedColor,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    GlyphButton(
                        icon = Icons.Rounded.Close,
                        description = stringResource(L10nR.string.feature_player_close_playback_settings_289e1a),
                        touchSize = 42.dp,
                        iconSize = 22.dp,
                        tint = titleColor,
                        background = Color.Transparent,
                        onClick = onDismiss,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PlaybackSettingsSection(
                    icon = Icons.Rounded.Bedtime,
                    title = stringResource(L10nR.string.feature_player_sleep_timer_108738),
                    detail = sleepTimerDetail(status),
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

                PlaybackSettingsSection(
                    icon = if (status.repeatMode == EchoRepeatMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    title = stringResource(L10nR.string.feature_player_playback_mode),
                    detail = listOf(
                        repeatModeLabel(status.repeatMode),
                        if (status.shuffleEnabled) {
                            stringResource(L10nR.string.feature_player_shuffle_on_c7c5c4)
                        } else {
                            stringResource(L10nR.string.feature_player_in_order_47b60a)
                        },
                    ).joinToString(" · "),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PlaybackToggleChip(
                            icon = if (status.repeatMode == EchoRepeatMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            text = repeatModeLabel(status.repeatMode),
                            selected = status.repeatMode != EchoRepeatMode.Off,
                            onClick = onCycleRepeatMode,
                            modifier = Modifier.weight(1f),
                            role = Role.Button,
                        )
                        PlaybackToggleChip(
                            icon = Icons.Rounded.Shuffle,
                            text = stringResource(L10nR.string.feature_player_shuffle),
                            selected = status.shuffleEnabled,
                            onClick = onToggleShuffle,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    PlaybackActionRow(
                        icon = Icons.AutoMirrored.Rounded.QueueMusic,
                        text = stringResource(L10nR.string.feature_player_queue_37fa6a),
                        onClick = onOpenQueue,
                    )
                }

                PlaybackSettingsSection(
                    icon = Icons.Rounded.Speed,
                    title = stringResource(L10nR.string.feature_player_speed_1d93fc),
                    detail = listOf(
                        formatPlaybackSpeedLabel(status.playbackSpeed),
                        if (nightcore) {
                            stringResource(L10nR.string.feature_player_nightcore)
                        } else {
                            stringResource(L10nR.string.feature_player_normal_speed_a8fc98)
                        },
                    ).joinToString(" · "),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PlaybackToggleChip(
                            icon = Icons.Rounded.Speed,
                            text = stringResource(L10nR.string.feature_player_normal_speed_a8fc98),
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
                }

                PlaybackSettingsSection(
                    icon = Icons.Rounded.GraphicEq,
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
                ) {
                    PlaybackToggleRow(
                        icon = Icons.Rounded.GraphicEq,
                        title = stringResource(L10nR.string.feature_player_replay_gain),
                        checked = status.replayGainEnabled,
                        onCheckedChange = { enabled ->
                            haptics.confirm()
                            onSetReplayGain(enabled, status.replayGainPreampDb)
                        },
                    )
                    EchoExpand(expanded = status.replayGainEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                EchoReplayGainMode.entries.forEach { mode ->
                                    PlaybackChoiceChip(
                                        text = replayGainModeLabel(mode),
                                        selected = status.replayGainMode == mode,
                                        onClick = { onSetReplayGainMode(mode) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
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
                                fontWeight = FontWeight.SemiBold,
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

                PlaybackToggleRow(
                    icon = Icons.AutoMirrored.Rounded.VolumeOff,
                    title = stringResource(L10nR.string.feature_player_skip_silence_d27e03),
                    checked = status.skipSilenceEnabled,
                    onCheckedChange = { enabled ->
                        haptics.confirm()
                        onSetSkipSilenceEnabled(enabled)
                    },
                )

                PlaybackSettingsSection(
                    icon = Icons.Rounded.Lyrics,
                    title = stringResource(L10nR.string.feature_player_lyrics_offset),
                    detail = formatLyricsOffset(lyricsOffsetMs),
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
                }
            }
        }
    }
}

@Composable
private fun PlaybackSettingsSection(
    icon: ImageVector,
    title: String,
    detail: String,
    content: @Composable () -> Unit,
) {
    val dark = LocalEchoDarkTheme.current
    val titleColor = if (dark) Color.White else echoTheme().heading
    val mutedColor = if (dark) Color.White.copy(alpha = 0.78f) else echoTheme().muted
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
            Text(title, color = titleColor, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            Text(
                detail,
                color = mutedColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        content()
    }
}

@Composable
private fun RowScope.PlaybackChoiceChip(
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
private fun PlaybackChoiceChip(
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
        animationSpec = tween(durationMillis = 180, easing = LyricsSettingsMotionEasing),
        label = "playback-choice-container",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.72f) else if (dark) echoTheme().glassBorder else Color.White.copy(alpha = 0.68f),
        animationSpec = tween(durationMillis = 180, easing = LyricsSettingsMotionEasing),
        label = "playback-choice-border",
    )
    val chipScale by animateFloatAsState(
        targetValue = if (selected) 1.02f else 1f,
        animationSpec = tween(durationMillis = 220, easing = LyricsSettingsMotionEasing),
        label = "playback-choice-scale",
    )
    Box(
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = 44.dp)
            .graphicsLayer {
                scaleX = chipScale
                scaleY = chipScale
            }
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
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PlaybackToggleChip(
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
        animationSpec = tween(durationMillis = 180, easing = LyricsSettingsMotionEasing),
        label = "playback-toggle-container",
    )
    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor)
            .border(
                BorderStroke(1.dp, if (selected) accent.copy(alpha = 0.72f) else if (dark) echoTheme().glassBorder else Color.White.copy(alpha = 0.68f)),
                RoundedCornerShape(14.dp),
            )
            .echoPressFeedback(interactionSource)
            .toggleable(
                value = selected,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = role,
                onValueChange = {
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
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlaybackToggleRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val dark = LocalEchoDarkTheme.current
    val accent = echoAccentColor()
    val interactionSource = remember { MutableInteractionSource() }
    val containerColor by animateColorAsState(
        targetValue = if (checked) {
            accent.copy(alpha = if (dark) 0.22f else 0.16f)
        } else {
            if (dark) echoTheme().panel.copy(alpha = 0.50f) else Color.White.copy(alpha = 0.48f)
        },
        animationSpec = tween(durationMillis = 180, easing = LyricsSettingsMotionEasing),
        label = "playback-toggle-row",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor)
            .border(
                BorderStroke(1.dp, if (checked) accent.copy(alpha = 0.62f) else if (dark) echoTheme().glassBorder else Color.White.copy(alpha = 0.66f)),
                RoundedCornerShape(14.dp),
            )
            .echoPressFeedback(interactionSource)
            .toggleable(
                value = checked,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (dark) Color.White else echoTheme().heading, modifier = Modifier.size(18.dp))
        Text(
            title,
            modifier = Modifier.weight(1f),
            color = if (dark) Color.White else echoTheme().heading,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            stringResource(if (checked) L10nR.string.feature_player_on_3062f9 else L10nR.string.feature_player_off_12ac24),
            color = if (dark) Color.White.copy(alpha = 0.78f) else echoTheme().muted,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun PlaybackActionRow(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
) {
    val dark = LocalEchoDarkTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (dark) echoTheme().panel.copy(alpha = 0.50f) else Color.White.copy(alpha = 0.48f))
            .border(
                if (dark) echoDarkGlassBorder() else BorderStroke(1.dp, Color.White.copy(alpha = 0.66f)),
                RoundedCornerShape(14.dp),
            )
            .echoClickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (dark) Color.White else echoTheme().heading, modifier = Modifier.size(18.dp))
        Text(
            text,
            color = if (dark) Color.White else echoTheme().heading,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlaybackActionChip(
    text: String,
    onClick: () -> Unit,
) {
    val dark = LocalEchoDarkTheme.current
    val accent = echoAccentColor()
    val haptics = rememberEchoHapticPerformer()
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
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
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun PlaybackStepper(
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
                if (dark) echoDarkGlassBorder() else BorderStroke(1.dp, Color.White.copy(alpha = 0.66f)),
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
            fontWeight = FontWeight.Bold,
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
private fun PlaybackStepperButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val dark = LocalEchoDarkTheme.current
    Box(
        modifier = Modifier
            .size(40.dp)
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

@Composable
private fun playbackSettingsSummary(status: EchoPlaybackStatus): String {
    val diagnostics = status.diagnostics
    val format = playbackFormatChips(
        diagnostics = diagnostics,
        pcmRateLabel = ::formatSampleRate,
    ).joinToString(" · ").ifBlank {
        stringResource(L10nR.string.feature_player_waiting_for_audio_info_a869fd)
    }
    val output = if (diagnostics.usbDeviceName != null) {
        stringResource(L10nR.string.feature_player_usb_output_c6800e)
    } else {
        stringResource(L10nR.string.feature_player_system_output_856789)
    }
    val mode = if (isNightcorePlayback(status.playbackSpeed, status.playbackPitch)) {
        stringResource(L10nR.string.feature_player_nightcore)
    } else {
        stringResource(L10nR.string.feature_player_normal_speed_a8fc98)
    }
    val silence = if (status.skipSilenceEnabled) {
        stringResource(L10nR.string.feature_player_skip_silence_d27e03)
    } else {
        null
    }
    return listOfNotNull(format, output, mode, silence).joinToString(" · ")
}

@Composable
private fun sleepTimerDetail(status: EchoPlaybackStatus): String {
    if (status.sleepTimerMode == EchoSleepTimerMode.Off) {
        return stringResource(L10nR.string.feature_player_off_12ac24)
    }
    if (status.sleepTimerMode == EchoSleepTimerMode.EndOfTrack) {
        val remaining = status.sleepTimerRemainingMs
        return if (remaining in 1 until 12 * 60 * 60 * 1000L) {
            stringResource(
                L10nR.string.feature_player_sleep_timer_this_track_clock_e76a6a,
                formatSleepTimerRemaining(remaining),
            )
        } else {
            stringResource(L10nR.string.feature_player_this_track_210ea7)
        }
    }
    return if (status.sleepTimerRemainingMs > 0L) {
        formatSleepTimerRemaining(status.sleepTimerRemainingMs)
    } else {
        stringResource(L10nR.string.feature_player_off_12ac24)
    }
}

@Composable
private fun repeatModeLabel(mode: EchoRepeatMode): String = when (mode) {
    EchoRepeatMode.Off -> stringResource(L10nR.string.feature_player_repeat_off_e1d801)
    EchoRepeatMode.All -> stringResource(L10nR.string.feature_player_repeat_all_751078)
    EchoRepeatMode.One -> stringResource(L10nR.string.feature_player_repeat_one_3df94f)
}

@Composable
private fun replayGainModeLabel(mode: EchoReplayGainMode): String = when (mode) {
    EchoReplayGainMode.Auto -> stringResource(L10nR.string.feature_player_replay_gain_auto)
    EchoReplayGainMode.Track -> stringResource(L10nR.string.feature_player_replay_gain_track)
    EchoReplayGainMode.Album -> stringResource(L10nR.string.feature_player_replay_gain_album)
}

