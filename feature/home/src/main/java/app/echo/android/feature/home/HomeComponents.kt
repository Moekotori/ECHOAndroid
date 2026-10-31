package app.echo.android.feature.home

import app.echo.android.design.displayArtist
import app.echo.android.feature.home.R as L10nR
import app.echo.android.design.displayTrack
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import app.echo.android.design.echoClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import app.echo.android.design.EchoIcon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.echoAccentColor
import app.echo.android.design.echoOnAccentColor
import app.echo.android.design.LocalEchoCustomBackgroundActive
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.rememberEchoHapticPerformer
import app.echo.android.design.formatDuration
import app.echo.android.design.progressFraction
import app.echo.android.design.echoTheme
import app.echo.android.model.playback.EchoPlaybackState
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoRepeatMode

enum class SearchResultType { Track, Album, Artist }

data class SearchResult(
    val type: SearchResultType,
    val title: String,
    val subtitle: String,
    val id: String,
    val artworkUri: String? = null,
)


@Composable
internal fun homePanelColor(lightAlpha: Float = 0.90f): Color {
    return if (LocalEchoDarkTheme.current) {
        echoTheme().panel.copy(alpha = (lightAlpha * 0.58f).coerceIn(0.42f, 0.62f))
    } else {
        Color.White.copy(alpha = if (LocalEchoCustomBackgroundActive.current) {
            (lightAlpha * 0.78f).coerceIn(0.56f, 0.78f)
        } else {
            lightAlpha.coerceIn(0.95f, 1.00f)
        })
    }
}

@Composable
internal fun homePanelBorder(lightAlpha: Float = 0.94f): BorderStroke {
    return BorderStroke(
        1.dp,
        if (LocalEchoDarkTheme.current) echoTheme().glassBorder else echoTheme().softLine.copy(alpha = lightAlpha.coerceIn(0.74f, 0.96f)),
    )
}

@Composable
internal fun homeTitleColor(): Color =
    if (LocalEchoDarkTheme.current) Color.White.copy(alpha = 0.92f) else echoTheme().heading

@Composable
internal fun homeBodyColor(): Color =
    if (LocalEchoDarkTheme.current) Color.White.copy(alpha = 0.66f) else echoTheme().muted.copy(alpha = 0.94f)

@Composable
internal fun EmptyRecentAlbumsCard(
    title: String = stringResource(L10nR.string.feature_home_no_albums_yet_93b5cb),
    subtitle: String = stringResource(L10nR.string.feature_home_appears_after_you_scan_your_library_5ae1b0),
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .echoClickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(homePanelColor(0.74f)),
            contentAlignment = Alignment.Center,
        ) {
            EchoIcon(
                Icons.Rounded.LibraryMusic,
                contentDescription = null,
                tint = echoAccentColor(),
                modifier = Modifier.size(34.dp),
            )
        }
        Text(
            title,
            color = homeTitleColor(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
        Text(
            subtitle,
            color = homeBodyColor(),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun HomeModeChip(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .heightIn(min = 58.dp)
            .clip(RoundedCornerShape(24.dp))
            .echoClickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = if (selected) 0.24f else 0.14f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = if (selected) 0.42f else 0.24f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            EchoIcon(icon, contentDescription = label, tint = if (selected) echoAccentColor() else Color.White.copy(alpha = 0.82f), modifier = Modifier.size(21.dp))
            Text(label, color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun QueuePreviewList(
    status: EchoPlaybackStatus,
    compact: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        QueuePreviewItem(
            icon = Icons.Rounded.MusicNote,
            label = stringResource(L10nR.string.feature_home_now_124d95),
            title = status.track?.let { displayTrack(it.title) } ?: stringResource(L10nR.string.feature_home_nothing_playing_b290fe),
            detail = status.track?.let { displayArtist(it.artist) } ?: stringResource(L10nR.string.feature_home_pick_a_song_from_your_library_320636),
            active = status.track != null,
        )
        if (!compact) {
            QueuePreviewItem(
                icon = Icons.Rounded.LibraryMusic,
                label = stringResource(L10nR.string.feature_home_next_d67904),
                title = stringResource(L10nR.string.feature_home_smart_queue_814a45),
                detail = if (status.track != null) {
                    stringResource(L10nR.string.feature_home_continues_from_the_local_queue_2f45cb)
                } else {
                    stringResource(L10nR.string.feature_home_upcoming_tracks_appear_after_you_pick_a_song_7e7129)
                },
                active = false,
            )
            QueuePreviewItem(
                icon = Icons.Rounded.Devices,
                label = stringResource(L10nR.string.feature_home_handoff_2f82d3),
                title = "PC ECHO",
                detail = if (status.track != null) {
                    stringResource(L10nR.string.feature_home_switch_to_desktop_output_c19745)
                } else {
                    stringResource(L10nR.string.feature_home_pair_to_take_over_remote_playback_5e3e4c)
                },
                active = false,
            )
        }
    }
}

@Composable
internal fun PlaybackModeControls(
    repeatMode: EchoRepeatMode,
    shuffleEnabled: Boolean,
    onCycleRepeatMode: () -> Unit,
    onToggleShuffle: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        PlaybackModeButton(
            icon = if (repeatMode == EchoRepeatMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            title = repeatModeLabel(repeatMode),
            detail = stringResource(L10nR.string.feature_home_tap_to_switch_47d6a3),
            selected = repeatMode != EchoRepeatMode.Off,
            onClick = onCycleRepeatMode,
            modifier = Modifier.weight(1f),
        )
        PlaybackModeButton(
            icon = Icons.Rounded.Shuffle,
            title = if (shuffleEnabled) {
                stringResource(L10nR.string.feature_home_shuffle_on_c7c5c4)
            } else {
                stringResource(L10nR.string.feature_home_in_order_47b60a)
            },
            detail = if (shuffleEnabled) {
                stringResource(L10nR.string.feature_home_queue_is_shuffled_45cabc)
            } else {
                stringResource(L10nR.string.feature_home_follows_queue_order_0926fb)
            },
            selected = shuffleEnabled,
            onClick = onToggleShuffle,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun PlaybackModeButton(
    icon: ImageVector,
    title: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.echoClickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) scheme.primary.copy(alpha = 0.14f) else homePanelColor(0.60f),
        border = if (selected) BorderStroke(1.dp, scheme.primary.copy(alpha = 0.28f)) else homePanelBorder(0.66f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EchoIcon(
                icon,
                contentDescription = title,
                tint = if (selected) scheme.primary else scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(
                    detail,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
internal fun QueuePreviewItem(
    icon: ImageVector,
    label: String,
    title: String,
    detail: String,
    active: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (active) scheme.primary.copy(alpha = 0.12f) else homePanelColor(0.56f),
        border = if (active) BorderStroke(1.dp, scheme.primary.copy(alpha = 0.24f)) else homePanelBorder(0.62f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = scheme.primary.copy(alpha = if (active) 0.18f else 0.12f),
            ) {
                EchoIcon(
                    icon,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.padding(8.dp).size(20.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(label, color = scheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(
                    detail,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
internal fun PlaybackHandoffFlow(active: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = homePanelColor(0.58f),
        border = homePanelBorder(0.64f),
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(L10nR.string.feature_home_handoff_path_d70cad), fontWeight = FontWeight.SemiBold)
                Text(
                    if (active) {
                        stringResource(L10nR.string.feature_home_ready_to_hand_off_2e2884)
                    } else {
                        stringResource(L10nR.string.feature_home_pick_a_track_first_bcdca7)
                    },
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                HandoffStep("1", stringResource(L10nR.string.feature_home_play_on_device_4765ae), selected = true, modifier = Modifier.weight(1f))
                HandoffStep("2", stringResource(L10nR.string.feature_home_connect_pc_724ba0), selected = active, modifier = Modifier.weight(1f))
                HandoffStep("3", stringResource(L10nR.string.feature_home_pc_output_e48933), selected = active, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun HandoffStep(
    number: String,
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) scheme.primary.copy(alpha = 0.14f) else homePanelColor(0.60f),
        border = if (selected) BorderStroke(1.dp, scheme.primary.copy(alpha = 0.24f)) else homePanelBorder(0.62f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(number, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
internal fun PlaybackActionCard(
    icon: ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .echoClickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = homePanelColor(0.58f),
        border = homePanelBorder(0.64f),
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            EchoIcon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                detail,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun CompactFact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun TransportControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    val haptics = rememberEchoHapticPerformer()
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = {
                haptics.tick()
                onPrevious()
            },
            modifier = Modifier.size(44.dp),
        ) {
            EchoIcon(
                Icons.Rounded.SkipPrevious,
                contentDescription = stringResource(L10nR.string.feature_home_previous_af0264),
                tint = echoAccentColor(),
                modifier = Modifier.size(28.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(56.dp)
                .shadow(elevation = 10.dp, shape = CircleShape, clip = false)
                .clip(CircleShape)
                .background(echoAccentColor())
                .echoClickable(
                    onClick = {
                        haptics.confirm()
                        onPlayPause()
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            EchoIcon(
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = stringResource(L10nR.string.feature_home_play_or_pause_37a70f),
                tint = echoOnAccentColor(),
                modifier = Modifier.size(30.dp),
            )
        }
        IconButton(
            onClick = {
                haptics.tick()
                onNext()
            },
            modifier = Modifier.size(44.dp),
        ) {
            EchoIcon(
                Icons.Rounded.SkipNext,
                contentDescription = stringResource(L10nR.string.feature_home_next_d67904),
                tint = echoAccentColor(),
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
internal fun PlaybackProgress(positionMs: Long, durationMs: Long, light: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    val foreground = if (light) Color.White else echoAccentColor()
    val secondary = when {
        light -> Color.White.copy(alpha = 0.70f)
        dark -> Color.White.copy(alpha = 0.62f)
        else -> scheme.onSurfaceVariant
    }
    val trackColor = when {
        light -> Color.White.copy(alpha = 0.18f)
        dark -> Color.White.copy(alpha = 0.12f)
        else -> scheme.outlineVariant.copy(alpha = 0.90f)
    }
    val fraction = progressFraction(positionMs, durationMs).coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(trackColor),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(foreground),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(positionMs), color = secondary, style = MaterialTheme.typography.labelSmall)
            Text(formatDuration(durationMs), color = secondary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
internal fun playbackStateLabel(state: EchoPlaybackState): String =
    when (state) {
        EchoPlaybackState.Idle -> stringResource(L10nR.string.feature_home_idle_3e0cc6)
        EchoPlaybackState.Loading -> stringResource(L10nR.string.feature_home_loading_a4ce8b)
        EchoPlaybackState.Playing -> stringResource(L10nR.string.feature_home_playing_d86b54)
        EchoPlaybackState.Paused -> stringResource(L10nR.string.feature_home_paused_3d8ed2)
        EchoPlaybackState.Seeking -> stringResource(L10nR.string.feature_home_seeking_ea77e5)
        EchoPlaybackState.Buffering -> stringResource(L10nR.string.feature_home_buffering_24542e)
        EchoPlaybackState.Ended -> stringResource(L10nR.string.feature_home_ended_6503ae)
        EchoPlaybackState.Stopped -> stringResource(L10nR.string.feature_home_stopped_6b0d06)
        EchoPlaybackState.Error -> stringResource(L10nR.string.feature_home_error_ad4bd6)
    }

@Composable
internal fun repeatModeLabel(mode: EchoRepeatMode): String =
    when (mode) {
        EchoRepeatMode.Off -> stringResource(L10nR.string.feature_home_repeat_off_e1d801)
        EchoRepeatMode.All -> stringResource(L10nR.string.feature_home_repeat_all_b8cce1)
        EchoRepeatMode.One -> stringResource(L10nR.string.feature_home_repeat_one_3df94f)
    }


@Composable
internal fun HomeLibraryNotice(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HomeBlock(modifier = modifier, onClick = onClick, verticalPadding = 12.dp) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EchoIcon(Icons.Rounded.LibraryMusic, contentDescription = null,
                tint = echoAccentColor(), modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = homeTitleColor(), style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = homeBodyColor(), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
