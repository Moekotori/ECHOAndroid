package app.echo.android.feature.player

import app.echo.android.model.settings.EchoPlayerMenuAction
import app.echo.android.model.settings.EchoPlayerMenuLayout
import androidx.compose.material.icons.rounded.Edit
import app.echo.android.design.displayArtist
import app.echo.android.design.displayTrack
import app.echo.android.feature.player.R as L10nR
import app.echo.android.design.EchoPageBackHandler as BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import app.echo.android.design.EchoMotion
import app.echo.android.design.EchoStateContent
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.echoPageSection
import app.echo.android.design.rememberEchoContentMotion
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Shuffle

import androidx.compose.material3.HorizontalDivider
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
    songActions: NowPlayingSongActions,
    menuLayout: EchoPlayerMenuLayout,
    onEditMenu: () -> Unit,
    actionsEnabled: Boolean = true,
    onOpenAppearance: () -> Unit,
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
    var pendingAction by remember(status.track?.id, actionsEnabled) { mutableStateOf<(() -> Unit)?>(null) }
    val currentTrackId = androidx.compose.runtime.rememberUpdatedState(status.track?.id)
    BackHandler(enabled = visible, onBack = onDismiss)
    EchoSheetOverlay(visible = visible, onDismiss = onDismiss, modifier = modifier, onHidden = {
        val action = pendingAction
        pendingAction = null
        if (actionsEnabled) action?.invoke()
    }) {
        PlaybackSettingsSheet(
            songActions = songActions,
            menuLayout = menuLayout,
            onEditMenu = { onDismiss(); onEditMenu() },
            onSongAction = { action ->
                if (visible && actionsEnabled && pendingAction == null) {
                    val selectedId = status.track?.id
                    pendingAction = { if (currentTrackId.value == selectedId) action() }
                    onDismiss()
                }
            },
            onOpenTrackTools = onOpenTrackTools,
            onOpenAppearance = {
                onDismiss()
                onOpenAppearance()
            },
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
    onSongAction: (() -> Unit) -> Unit,
    onOpenTrackTools: () -> Unit = {},
    songActions: NowPlayingSongActions,
    menuLayout: EchoPlayerMenuLayout,
    onEditMenu: () -> Unit,
    onOpenAppearance: () -> Unit,
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
    var moreExpanded by remember { mutableStateOf(false) }
    BackHandler(enabled = moreExpanded) { moreExpanded = false }
    val contentScrollState = remember(moreExpanded) { ScrollState(0) }
    val accent = echoAccentColor()
    val visibleItems = remember(menuLayout, songActions, status.track?.id) {
        menuLayout.normalized().let { layout ->
            layout.order.filter { it !in layout.hidden && it.available(songActions, status.track != null) }
        }
    }
    val headerTitle = if (moreExpanded) stringResource(L10nR.string.playback_more_settings)
        else status.track?.let { displayTrack(it.title) }
            ?: stringResource(L10nR.string.feature_player_playback_settings_651436)
    val headerSubtitle = if (moreExpanded) playbackSettingsSummary(status)
        else status.track?.let { displayArtist(it.artist) }.orEmpty()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Keep the menu short; the body scrolls below the pinned track header.
                .heightIn(max = minOf(maxHeight * 0.60f, 560.dp))
                .settingsSheetSurface()
                .navigationBarsPadding(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                PlaybackSheetHandle(onDismiss = onDismiss)
                SettingsSheetHeader(
                    deeper = moreExpanded,
                    title = headerTitle,
                    subtitle = headerSubtitle,
                    backDescription = stringResource(L10nR.string.feature_player_playback_settings_651436),
                    closeDescription = stringResource(L10nR.string.feature_player_close_playback_settings_289e1a),
                    onBack = { moreExpanded = false },
                    onClose = onDismiss,
                    compact = true,
                    actions = {
                        if (!moreExpanded) GlyphButton(
                            icon = Icons.Rounded.Edit,
                            description = stringResource(L10nR.string.player_menu_edit),
                            touchSize = 48.dp, iconSize = 20.dp,
                            tint = MaterialTheme.colorScheme.onSurface, background = settingsPlateColor(), onClick = onEditMenu,
                        )
                    },
                ) {
                    ArtworkTile(
                        artworkUri = status.track?.artworkUri,
                        modifier = Modifier.size(40.dp),
                        accent = accent, cornerRadius = 10.dp, elevation = 0.dp,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(contentScrollState)
                    .padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
            ) {
                SettingsPageSwitcher(state = moreExpanded, isDeeper = { it }) { more ->
                    Column {
                        if (more) {
                            PlaybackAdvancedSettings(
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
                            NowPlayingSongActionStatus(songActions)
                            if (visibleItems.isEmpty()) Text(
                                stringResource(L10nR.string.player_menu_empty),
                                modifier = Modifier.padding(vertical = 12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            visibleItems.forEach { item ->
                                androidx.compose.runtime.key(item) {
                                    when (item) {
                                        EchoPlayerMenuAction.Appearance -> PlaybackActionRow(
                                            item.icon(), stringResource(item.labelRes()), onOpenAppearance,
                                            framed = false, compact = true,
                                        )
                                        EchoPlayerMenuAction.Repeat -> PlaybackRepeatMenuControl(status, onSetRepeatMode)
                                        EchoPlayerMenuAction.Shuffle -> PlaybackToggleRow(
                                            item.icon(), stringResource(item.labelRes()), status.shuffleEnabled,
                                            onCheckedChange = { onToggleShuffle() }, framed = false, compact = true,
                                        )
                                        EchoPlayerMenuAction.SleepTimer -> PlaybackSleepTimerMenuControl(
                                            status, onSetSleepTimer, onSetSleepTimerEndOfTrack, onCancelSleepTimer,
                                        )
                                        EchoPlayerMenuAction.MoreSettings -> PlaybackActionRow(
                                            item.icon(), stringResource(item.labelRes()), { moreExpanded = true },
                                            framed = false, compact = true,
                                        )
                                        else -> NowPlayingSongActionRow(item, songActions, onSongAction, onOpenQueue, status.track)
                                    }
                                }
                            }
                        }
                    }
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
internal fun sleepTimerDetail(status: EchoPlaybackStatus): String {
    if (status.sleepTimerMode == EchoSleepTimerMode.Off) {
        return stringResource(L10nR.string.feature_player_off_12ac24)
    }
    if (status.sleepTimerFinishingCurrentTrack) return stringResource(L10nR.string.sleep_timer_finishing,
        formatSleepTimerRemaining(status.sleepTimerRemainingMs))
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
internal fun repeatModeLabel(mode: EchoRepeatMode): String = when (mode) {
    EchoRepeatMode.Off -> stringResource(L10nR.string.feature_player_repeat_off_e1d801)
    EchoRepeatMode.All -> stringResource(L10nR.string.feature_player_repeat_all_751078)
    EchoRepeatMode.One -> stringResource(L10nR.string.feature_player_repeat_one_3df94f)
}
