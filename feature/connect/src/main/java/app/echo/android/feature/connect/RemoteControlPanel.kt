package app.echo.android.feature.connect

import app.echo.android.feature.connect.R as L10nR
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import app.echo.android.design.echoClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoArtworkImage
import app.echo.android.design.EchoArtworkSize
import app.echo.android.design.EchoDarkGlassBorder
import app.echo.android.design.EchoGlassBorder
import app.echo.android.design.EchoGlassInk
import app.echo.android.design.EchoGlassPanel
import app.echo.android.design.EchoGlassViolet
import app.echo.android.design.EchoHomeMist
import app.echo.android.design.EchoMetricTile
import app.echo.android.design.EchoPanel
import app.echo.android.design.EchoPlaceholderLine
import app.echo.android.design.EchoSectionTitle
import app.echo.android.design.EchoSegmentChip
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.echoDarkGlassBorder
import app.echo.android.model.connect.EchoRemoteConnectionState

@Composable
internal fun ServiceCard(
    name: String,
    subtitle: String,
    icon: ImageVector,
    brandColor: Color,
    statusLabel: String,
    active: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        if (dark) EchoGlassPanel.copy(alpha = 0.58f) else scheme.surface.copy(alpha = 0.70f),
                        brandColor.copy(alpha = if (dark) if (locked) 0.16f else 0.25f else if (locked) 0.08f else 0.14f),
                        if (dark) EchoGlassViolet.copy(alpha = 0.12f) else EchoHomeMist.copy(alpha = 0.28f),
                    ),
                ),
            )
            .border(
                if (dark) echoDarkGlassBorder(active) else BorderStroke(1.dp, EchoGlassBorder.copy(alpha = 0.86f)),
                RoundedCornerShape(20.dp),
            )
            .echoClickable(enabled = !locked, onClick = onClick)
            .padding(15.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(brandColor.copy(alpha = if (locked) 0.55f else 0.95f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(25.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    name,
                    color = scheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ServiceStatusPill(label = statusLabel, active = active, locked = locked)
        }
    }
}

@Composable
internal fun ServiceStatusPill(
    label: String,
    active: Boolean,
    locked: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    val background = when {
        locked -> if (dark) EchoGlassInk.copy(alpha = 0.50f) else scheme.surfaceVariant.copy(alpha = 0.52f)
        active -> Color(0xFF35C28E).copy(alpha = if (dark) 0.28f else 0.22f)
        else -> scheme.primary.copy(alpha = if (dark) 0.28f else 0.22f)
    }
    val foreground = when {
        locked -> scheme.onSurfaceVariant
        active -> Color(0xFF1A9B68)
        else -> scheme.primary
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = background,
        border = BorderStroke(1.dp, if (dark) EchoDarkGlassBorder else Color.Transparent),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                if (locked) Icons.Rounded.Lock else Icons.Rounded.Check,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(13.dp),
            )
            Text(
                label,
                color = foreground,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun PcLinkStatusStrip(connected: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (dark) EchoGlassPanel.copy(alpha = 0.44f) else scheme.surface.copy(alpha = 0.56f),
        border = BorderStroke(
            1.dp,
            if (dark) EchoDarkGlassBorder else EchoGlassBorder.copy(alpha = 0.76f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(
                    if (connected) {
                        stringResource(L10nR.string.feature_connect_control_on_phone_output_on_pc_fe2fbb)
                    } else {
                        stringResource(L10nR.string.feature_connect_waiting_for_pc_echo_pairing_e5e462)
                    },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (connected) {
                        stringResource(L10nR.string.feature_connect_queue_volume_and_next_track_will_use_the_0aa197)
                    } else {
                        stringResource(L10nR.string.feature_connect_latency_output_device_and_queue_appear_after_pairing_b8e65f)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun PcHandoffPanel(connected: Boolean) {
    EchoPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EchoSectionTitle(
                stringResource(L10nR.string.feature_connect_handoff_console_d5905c),
                if (connected) {
                    stringResource(L10nR.string.feature_connect_phone_and_pc_queues_stay_in_sync_64e284)
                } else {
                    stringResource(L10nR.string.feature_connect_become_a_remote_after_pairing_77ae56)
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                EchoSegmentChip(stringResource(L10nR.string.feature_connect_phone_control_91825f), selected = true, Modifier.weight(1f))
                EchoSegmentChip(stringResource(L10nR.string.feature_connect_pc_output_e48933), selected = connected, Modifier.weight(1f))
                EchoSegmentChip(stringResource(L10nR.string.feature_connect_queue_sync_ac9c36), selected = connected, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                EchoMetricTile(
                    stringResource(L10nR.string.feature_connect_latency_fa1715),
                    if (connected) "24ms" else "--",
                    Modifier.weight(1f),
                    detail = stringResource(L10nR.string.feature_connect_estimate_40b38b),
                )
                EchoMetricTile(
                    stringResource(L10nR.string.feature_connect_volume_6d5238),
                    if (connected) {
                        stringResource(L10nR.string.feature_connect_synced_0e5a04)
                    } else {
                        stringResource(L10nR.string.feature_connect_standby_751331)
                    },
                    Modifier.weight(1f),
                    detail = stringResource(L10nR.string.feature_connect_mapping_65a632),
                )
                EchoMetricTile(
                    stringResource(L10nR.string.feature_connect_device_d46471),
                    if (connected) {
                        stringResource(L10nR.string.feature_connect_desktop_5966da)
                    } else {
                        stringResource(L10nR.string.feature_connect_none_836573)
                    },
                    Modifier.weight(1f),
                    detail = stringResource(L10nR.string.feature_connect_output_bb8fcf),
                )
            }
            EchoPlaceholderLine(
                if (connected) {
                    stringResource(L10nR.string.feature_connect_the_next_track_will_sync_to_pc_echo_970d49)
                } else {
                    stringResource(L10nR.string.feature_connect_pc_queue_and_output_device_appear_after_pairing_7fd247)
                },
            )
            EchoPlaceholderLine(
                if (connected) {
                    stringResource(L10nR.string.feature_connect_latency_monitoring_and_volume_mapping_are_ready_2f0b1f)
                } else {
                    stringResource(L10nR.string.feature_connect_volume_latency_and_output_device_linking_are_reserved_04bcb1)
                },
            )
        }
    }
}

@Composable
internal fun RemoteNowPlaying(
    title: String,
    artist: String,
    artworkUrl: String?,
    isPlaying: Boolean,
    controlsEnabled: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (dark) EchoGlassPanel.copy(alpha = 0.44f) else scheme.surface.copy(alpha = 0.58f),
        border = BorderStroke(
            1.dp,
            if (dark) EchoDarkGlassBorder else EchoGlassBorder.copy(alpha = 0.76f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EchoArtworkImage(
                artworkUri = artworkUrl,
                contentDescription = title,
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(14.dp),
                sizeClass = EchoArtworkSize.Thumbnail,
            )
            Column(Modifier.weight(1f)) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onPrevious, enabled = controlsEnabled) {
                Icon(
                    Icons.Rounded.SkipPrevious,
                    contentDescription = stringResource(L10nR.string.feature_connect_previous_on_pc_a0f0a7),
                )
            }
            IconButton(onClick = onPlayPause, enabled = controlsEnabled) {
                Icon(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(L10nR.string.feature_connect_play_or_pause_pc_35a4a9),
                )
            }
            IconButton(onClick = onNext, enabled = controlsEnabled) {
                Icon(
                    Icons.Rounded.SkipNext,
                    contentDescription = stringResource(L10nR.string.feature_connect_next_on_pc_303358),
                )
            }
        }
    }
}

@Composable
internal fun PairingPill(
    number: String,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    val dark = LocalEchoDarkTheme.current
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = if (active) scheme.primary.copy(alpha = if (dark) 0.20f else 0.12f) else if (dark) EchoGlassPanel.copy(alpha = 0.38f) else scheme.surfaceVariant.copy(alpha = 0.34f),
        border = BorderStroke(
            1.dp,
            if (active) scheme.primary.copy(alpha = if (dark) 0.34f else 0.22f) else if (dark) EchoDarkGlassBorder else scheme.outlineVariant.copy(alpha = 0.18f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = if (active) 0.24f else 0.14f)) {
                Text(
                    number,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun remoteConnectionLabel(state: EchoRemoteConnectionState): String =
    when (state) {
        EchoRemoteConnectionState.Disconnected -> stringResource(L10nR.string.feature_connect_not_connected_c4d337)
        EchoRemoteConnectionState.Pairing -> stringResource(L10nR.string.feature_connect_pairing_1a1d00)
        EchoRemoteConnectionState.Connecting -> stringResource(L10nR.string.feature_connect_connecting_5a83dc)
        EchoRemoteConnectionState.Connected -> stringResource(L10nR.string.feature_connect_connected_6b85ee)
        EchoRemoteConnectionState.Reconnecting -> stringResource(L10nR.string.feature_connect_reconnecting_6c545f)
        EchoRemoteConnectionState.Error -> stringResource(L10nR.string.feature_connect_error_ad4bd6)
    }

