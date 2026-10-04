package app.echo.android.feature.player

import app.echo.android.feature.player.R as L10nR
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.ArtworkTile
import app.echo.android.design.EchoExpand
import app.echo.android.design.EchoSheetOverlay
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.echoAccentColor
import app.echo.android.design.echoTheme
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoRepeatMode
import app.echo.android.model.playback.EchoReplayGainMode
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

internal fun playbackSettingsHeadline(formatChips: List<String>, outputLabel: String): String {
    val format = formatChips.joinToString(" · ")
    return if (format.isBlank()) outputLabel else "$format · $outputLabel"
}

internal fun playModeDetail(repeatLabel: String, shuffleEnabled: Boolean, shuffleOnLabel: String): String =
    if (shuffleEnabled) "$repeatLabel · $shuffleOnLabel" else repeatLabel

@Composable
internal fun PlaybackSettingsDrawer(
    onOpenTrackTools: () -> Unit = {},
    appearance: PlayerAppearance,
    onAppearancePreview: (PlayerAppearance) -> Unit,
    onAppearanceCommit: () -> Unit,
    visible: Boolean,
    status: EchoPlaybackStatus,
    onSetRepeatMode: (EchoRepeatMode) -> Unit,
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
    lyricsOffsetMs: Long,
    onAdjustLyricsOffset: (Long) -> Unit,
    onResetLyricsOffset: () -> Unit,
    onOpenQueue: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = visible, onBack = onDismiss)
    EchoSheetOverlay(visible = visible, onDismiss = onDismiss, modifier = modifier) {
        PlaybackSettingsSheet(
            onOpenTrackTools = onOpenTrackTools,
            appearance = appearance,
            onAppearancePreview = onAppearancePreview,
            onAppearanceCommit = onAppearanceCommit,
            status = status,
            onSetRepeatMode = onSetRepeatMode,
            onToggleShuffle = onToggleShuffle,
            onSetPlaybackSpeed = onSetPlaybackSpeed,
            onSetSleepTimer = onSetSleepTimer,
            onSetSleepTimerEndOfTrack = onSetSleepTimerEndOfTrack,
            onCancelSleepTimer = onCancelSleepTimer,
            onSetReplayGain = onSetReplayGain,
            onAdjustReplayGainPreamp = onAdjustReplayGainPreamp,
            replayGainScanState = replayGainScanState,
            onScanReplayGain = onScanReplayGain,
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

@Composable
private fun PlaybackSettingsSheet(
    onOpenTrackTools: () -> Unit = {},
    appearance: PlayerAppearance,
    onAppearancePreview: (PlayerAppearance) -> Unit,
    onAppearanceCommit: () -> Unit,
    status: EchoPlaybackStatus,
    onSetRepeatMode: (EchoRepeatMode) -> Unit,
    onToggleShuffle: () -> Unit,
    onSetPlaybackSpeed: (Float, Boolean) -> Unit,
    onSetSleepTimer: (Int) -> Unit,
    onSetSleepTimerEndOfTrack: () -> Unit,
    onCancelSleepTimer: () -> Unit,
    onSetReplayGain: (Boolean, Float) -> Unit,
    onAdjustReplayGainPreamp: (Float) -> Unit,
    replayGainScanState: EchoReplayGainScanState = EchoReplayGainScanState.Idle,
    onScanReplayGain: () -> Unit = {},
    lyricsOffsetMs: Long,
    onAdjustLyricsOffset: (Long) -> Unit,
    onResetLyricsOffset: () -> Unit,
    onOpenQueue: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dark = LocalEchoDarkTheme.current
    val customActive = isCustomSleepTimer(status.sleepTimerMode, status.sleepTimerMinutes)
    var timerExpanded by remember { mutableStateOf(false) }
    var repeatExpanded by remember { mutableStateOf(false) }
    var moreExpanded by remember { mutableStateOf(false) }
    var showCustomSleepTimer by remember { mutableStateOf(false) }
    var customMinutes by remember {
        mutableIntStateOf(status.sleepTimerMinutes?.takeIf { it !in SleepTimerPresetMinutes } ?: SleepTimerCustomDefaultMinutes)
    }
    BackHandler(enabled = moreExpanded) { moreExpanded = false }
    val contentScrollState = remember(moreExpanded) { ScrollState(0) }
    val panelShape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    val titleColor = if (dark) Color.White else echoTheme().heading
    val mutedColor = if (dark) Color.White.copy(alpha = 0.65f) else echoTheme().muted
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight * 0.88f)
                .clip(panelShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .navigationBarsPadding(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PlaybackSheetHandle(onDismiss = onDismiss)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (moreExpanded) {
                        GlyphButton(
                            icon = Icons.AutoMirrored.Rounded.ArrowBack,
                            description = stringResource(L10nR.string.feature_player_playback_settings_651436),
                            touchSize = 48.dp, iconSize = 22.dp, tint = titleColor,
                            background = Color.Transparent, onClick = { moreExpanded = false },
                        )
                    } else {
                        ArtworkTile(
                            artworkUri = status.track?.artworkUri,
                            modifier = Modifier.size(48.dp),
                            accent = echoAccentColor(), cornerRadius = 10.dp, elevation = 0.dp,
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            if (moreExpanded) stringResource(L10nR.string.playback_more_settings)
                            else status.track?.title?.takeIf { it.isNotBlank() }
                                ?: stringResource(L10nR.string.feature_player_playback_settings_651436),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = titleColor,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            if (moreExpanded) playbackSettingsSummary(status)
                            else status.track?.artist.orEmpty(),
                            color = mutedColor,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    GlyphButton(
                        icon = Icons.Rounded.Close,
                        description = stringResource(L10nR.string.feature_player_close_playback_settings_289e1a),
                        touchSize = 48.dp,
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
                    .verticalScroll(contentScrollState)
                    .padding(start = 20.dp, end = 20.dp, bottom = 18.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (moreExpanded) {
                    PlaybackAdvancedSettings(
                        appearance = appearance,
                        onAppearancePreview = onAppearancePreview,
                        onAppearanceCommit = onAppearanceCommit,
                        status = status,
                        onSetPlaybackSpeed = onSetPlaybackSpeed,
                        onSetReplayGain = onSetReplayGain,
                        onAdjustReplayGainPreamp = onAdjustReplayGainPreamp,
                        replayGainScanState = replayGainScanState,
                        onScanReplayGain = onScanReplayGain,
                        lyricsOffsetMs = lyricsOffsetMs,
                        onAdjustLyricsOffset = onAdjustLyricsOffset,
                        onResetLyricsOffset = onResetLyricsOffset,
                        onOpenTrackTools = onOpenTrackTools,
                        onDismiss = onDismiss,
                    )
                } else {
                    PlaybackSettingsSection(
                        icon = Icons.Rounded.Repeat,
                        framed = false,
                        showDivider = false,
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
                    PlaybackToggleRow(
                        icon = Icons.Rounded.Shuffle,
                        title = stringResource(L10nR.string.feature_player_shuffle),
                        checked = status.shuffleEnabled,
                        framed = false,
                        onCheckedChange = { onToggleShuffle() },
                    )
                    PlaybackActionRow(
                        icon = Icons.AutoMirrored.Rounded.QueueMusic,
                        text = stringResource(L10nR.string.feature_player_queue_37fa6a),
                        framed = false,
                        onClick = onOpenQueue,
                    )

                    PlaybackSettingsSection(
                        icon = Icons.Rounded.Bedtime,
                        framed = false,
                        showDivider = false,
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

                    PlaybackActionRow(
                        icon = Icons.Rounded.Settings,
                        text = stringResource(L10nR.string.playback_more_settings),
                        framed = false,
                        onClick = { moreExpanded = true },
                    )
                }

            }
        }
    }
}

@Composable
private fun playbackSettingsSummary(status: EchoPlaybackStatus): String {
    val diagnostics = status.diagnostics
    val output = if (diagnostics.usbDeviceName != null) {
        stringResource(L10nR.string.feature_player_usb_output_c6800e)
    } else {
        stringResource(L10nR.string.feature_player_system_output_856789)
    }
    return playbackSettingsHeadline(
        formatChips = playbackFormatChips(
            diagnostics = diagnostics,
            pcmRateLabel = ::formatSampleRate,
        ),
        outputLabel = output,
    )
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
