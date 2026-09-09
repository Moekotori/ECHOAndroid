package app.echo.android.feature.connect

import app.echo.android.feature.connect.R as L10nR
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.connect.EchoLinkRemoteControlHold
import app.echo.android.design.EchoArtworkImage
import app.echo.android.design.EchoArtworkSize
import app.echo.android.design.formatDuration
import app.echo.android.model.connect.EchoRemoteConnectionState
import kotlinx.coroutines.delay

@Composable
internal fun RemoteNowPlaying(
    title: String,
    artist: String,
    artworkUrl: String?,
    isPlaying: Boolean,
    controlsEnabled: Boolean,
    positionMs: Long,
    durationMs: Long,
    volume: Float,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolume: (Float) -> Unit,
    queueTitles: List<String> = emptyList(),
) {
    var anchoredAtMs by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    var tickMs by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    var seekDragging by remember { mutableStateOf(false) }
    var seekSlider by remember { mutableFloatStateOf(positionMs.toFloat()) }
    var volumeDragging by remember { mutableStateOf(false) }
    var volumeSlider by remember { mutableFloatStateOf(volume.coerceIn(0f, 1f)) }
    var committedPositionMs by remember { mutableStateOf<Long?>(null) }
    var committedPositionAtMs by remember { mutableStateOf<Long?>(null) }
    var committedVolume by remember { mutableStateOf<Float?>(null) }
    var committedVolumeAtMs by remember { mutableStateOf<Long?>(null) }
    val nowElapsedMs = android.os.SystemClock.elapsedRealtime()
    LaunchedEffect(positionMs, isPlaying) {
        anchoredAtMs = android.os.SystemClock.elapsedRealtime()
        tickMs = anchoredAtMs
        if (!isPlaying) return@LaunchedEffect
        while (true) {
            delay(400)
            tickMs = android.os.SystemClock.elapsedRealtime()
        }
    }
    LaunchedEffect(positionMs, committedPositionMs, committedPositionAtMs) {
        val hold = EchoLinkRemoteControlHold.shouldHoldCommittedPosition(
            committedPositionMs = committedPositionMs,
            committedAtElapsedMs = committedPositionAtMs,
            remotePositionMs = positionMs,
            nowElapsedMs = android.os.SystemClock.elapsedRealtime(),
        )
        if (!hold) {
            committedPositionMs = null
            committedPositionAtMs = null
            if (!seekDragging) seekSlider = positionMs.coerceAtLeast(0L).toFloat()
        }
    }
    LaunchedEffect(volume, committedVolume, committedVolumeAtMs) {
        val hold = EchoLinkRemoteControlHold.shouldHoldCommittedVolume(
            committedVolume = committedVolume,
            committedAtElapsedMs = committedVolumeAtMs,
            remoteVolume = volume,
            nowElapsedMs = android.os.SystemClock.elapsedRealtime(),
        )
        if (!hold) {
            committedVolume = null
            committedVolumeAtMs = null
            if (!volumeDragging) volumeSlider = volume.coerceIn(0f, 1f)
        }
    }
    val safeDuration = durationMs.coerceAtLeast(0L)
    val livePosition = if (isPlaying && !seekDragging) {
        (positionMs + (tickMs - anchoredAtMs).coerceAtLeast(0L))
            .coerceAtMost(if (safeDuration > 0L) safeDuration else Long.MAX_VALUE)
    } else {
        seekSlider.toLong()
    }
    val sliderPosition = EchoLinkRemoteControlHold.displayedPositionMs(
        remotePositionMs = positionMs,
        livePositionMs = livePosition,
        committedPositionMs = committedPositionMs,
        committedAtElapsedMs = committedPositionAtMs,
        nowElapsedMs = nowElapsedMs,
        draggingPositionMs = if (seekDragging) seekSlider.toLong() else null,
    ).toFloat()
    val shownVolume = EchoLinkRemoteControlHold.displayedVolume(
        remoteVolume = volume,
        committedVolume = committedVolume,
        committedAtElapsedMs = committedVolumeAtMs,
        nowElapsedMs = nowElapsedMs,
        draggingVolume = if (volumeDragging) volumeSlider else null,
    )
    ConnectSection(stringResource(L10nR.string.feature_connect_playing_on_pc_580a8a)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            EchoArtworkImage(artworkUri = artworkUrl, contentDescription = null, modifier = Modifier.size(72.dp),
                shape = ConnectControlShape, sizeClass = EchoArtworkSize.Thumbnail)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title.ifBlank { stringResource(L10nR.string.feature_connect_no_track_selected_258d56) },
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                if (artist.isNotBlank()) ConnectNote(artist)
            }
        }
        if (safeDuration > 0L) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Slider(
                    value = sliderPosition.coerceIn(0f, safeDuration.toFloat()),
                    onValueChange = {
                        seekDragging = true
                        seekSlider = it
                    },
                    onValueChangeFinished = {
                        val committed = seekSlider.toLong().coerceIn(0L, safeDuration)
                        seekDragging = false
                        committedPositionMs = committed
                        committedPositionAtMs = android.os.SystemClock.elapsedRealtime()
                        onSeek(committed)
                    },
                    valueRange = 0f..safeDuration.toFloat(),
                    enabled = controlsEnabled,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ConnectNote(formatDuration(sliderPosition.toLong().coerceAtLeast(0L)))
                    ConnectNote(formatDuration(safeDuration))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious, enabled = controlsEnabled, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.SkipPrevious, stringResource(L10nR.string.feature_connect_previous_on_pc_a0f0a7))
            }
            Button(onClick = onPlayPause, enabled = controlsEnabled, shape = ConnectControlShape, modifier = Modifier.padding(horizontal = 16.dp).heightIn(min = 48.dp)) {
                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (isPlaying) stringResource(L10nR.string.feature_connect_pause_pc_003bcf) else stringResource(L10nR.string.feature_connect_play_on_pc_aa41d1))
            }
            IconButton(onClick = onNext, enabled = controlsEnabled, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.SkipNext, stringResource(L10nR.string.feature_connect_next_on_pc_303358))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ConnectNote(stringResource(L10nR.string.feature_connect_volume_8f3a19))
            Slider(
                value = shownVolume,
                onValueChange = {
                    volumeDragging = true
                    volumeSlider = it
                },
                onValueChangeFinished = {
                    volumeDragging = false
                    committedVolume = volumeSlider
                    committedVolumeAtMs = android.os.SystemClock.elapsedRealtime()
                    onVolume(volumeSlider)
                },
                valueRange = 0f..1f,
                enabled = controlsEnabled,
            )
        }
        if (queueTitles.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ConnectNote(stringResource(L10nR.string.feature_connect_pc_queue_2e91c4))
                queueTitles.take(12).forEachIndexed { index, title ->
                    Text(
                        "${index + 1}. $title",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
internal fun remoteConnectionLabel(state: EchoRemoteConnectionState): String = when (state) {
    EchoRemoteConnectionState.Disconnected -> stringResource(L10nR.string.feature_connect_not_connected_c4d337)
    EchoRemoteConnectionState.Pairing -> stringResource(L10nR.string.feature_connect_pairing_1a1d00)
    EchoRemoteConnectionState.Connecting -> stringResource(L10nR.string.feature_connect_connecting_5a83dc)
    EchoRemoteConnectionState.Connected -> stringResource(L10nR.string.feature_connect_connected_6b85ee)
    EchoRemoteConnectionState.Reconnecting -> stringResource(L10nR.string.feature_connect_reconnecting_6c545f)
    EchoRemoteConnectionState.Error -> stringResource(L10nR.string.feature_connect_connection_failed_321409)
}
