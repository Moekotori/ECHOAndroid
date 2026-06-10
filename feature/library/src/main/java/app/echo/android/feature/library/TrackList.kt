package app.echo.android.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.LazyPagingItems
import app.echo.android.design.ArtworkTile
import app.echo.android.design.EchoAccent
import app.echo.android.design.EchoGlassPanel
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.formatDuration
import app.echo.android.model.library.EchoTrack

@Composable
internal fun TrackList(
    tracks: LazyPagingItems<EchoTrack>,
    onPlayTrack: (EchoTrack) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = LibraryBottomControlsPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            count = tracks.itemCount,
            key = { index: Int -> tracks.peek(index)?.id ?: "track-$index" },
        ) { index: Int ->
            tracks[index]?.let { track ->
                TrackRow(
                    track = track,
                    onClick = { onPlayTrack(track) },
                )
            }
        }
    }
}

@Composable
internal fun TrackList(
    tracks: List<EchoTrack>,
    onPlayTrack: (EchoTrack) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = LibraryBottomControlsPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            count = tracks.size,
            key = { index -> tracks[index].id },
        ) { index ->
            val track = tracks[index]
            TrackRow(
                track = track,
                onClick = { onPlayTrack(track) },
            )
        }
    }
}

@Composable
internal fun TrackRow(
    track: EchoTrack,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    val subtitle = remember(track.artist, track.album) { trackSubtitle(track) }
    val duration = remember(track.durationMs) { formatDuration(track.durationMs) }
    val sampleRate = remember(track.sampleRateHz) { track.sampleRateHz?.let(::formatTrackSampleRate) }
    val format = remember(track.mimeType) { formatTrackMimeType(track.mimeType) }
    val hasTags = format != null || sampleRate != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (dark) EchoGlassPanel.copy(alpha = 0.46f) else Color.Transparent)
            .border(
                BorderStroke(1.dp, if (dark) Color.White.copy(alpha = 0.18f) else Color.Transparent),
                RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 92.dp)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ArtworkTile(
                track.artworkUri,
                Modifier.size(68.dp),
                accent = EchoAccent,
                cornerRadius = 12.dp,
                elevation = 3.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    track.title,
                    color = if (dark) Color.White.copy(alpha = 0.98f) else scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (dark) Color.White.copy(alpha = 0.80f) else scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (hasTags) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        format?.let { value ->
                            TrackInfoTag(
                                text = value,
                                tone = TrackInfoTagTone.Format,
                            )
                        }
                        sampleRate?.let { value ->
                            TrackInfoTag(
                                text = value,
                                tone = if (isHiResSampleRate(track.sampleRateHz)) {
                                    TrackInfoTagTone.Gold
                                } else {
                                    TrackInfoTagTone.Neutral
                                },
                            )
                        }
                    }
                }
            }
            Column(
                modifier = Modifier.widthIn(min = 50.dp, max = 74.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    duration,
                    color = if (dark) Color.White.copy(alpha = 0.82f) else scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .padding(start = 82.dp)
                .background(if (dark) Color.White.copy(alpha = 0.16f) else scheme.outlineVariant.copy(alpha = 0.36f)),
        )
    }
}

@Composable
private fun TrackInfoTag(
    text: String,
    tone: TrackInfoTagTone,
) {
    Surface(
        shape = RoundedCornerShape(7.dp),
        color = tone.background,
        border = BorderStroke(1.dp, tone.border),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            color = tone.content,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                lineHeight = 12.sp,
            ),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

internal fun trackSubtitle(track: EchoTrack): String =
    listOf(track.artist, track.album)
        .mapNotNull { value -> value?.takeIf { it.isNotBlank() } }
        .ifEmpty { listOf("本机音频") }
        .joinToString(" / ")

private fun formatTrackSampleRate(hz: Int): String =
    if (hz % 1000 == 0) {
        "${hz / 1000}kHz"
    } else {
        String.format("%.1fkHz", hz / 1000.0)
    }

private fun formatTrackMimeType(mimeType: String?): String? {
    val raw = mimeType
        ?.substringAfter("audio/", missingDelimiterValue = mimeType)
        ?.substringBefore(";")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: return null
    return when {
        raw.equals("mpeg", ignoreCase = true) || raw.equals("mp3", ignoreCase = true) -> "MP3"
        raw.equals("mp4", ignoreCase = true) || raw.equals("mp4a-latm", ignoreCase = true) -> "AAC"
        raw.equals("x-wav", ignoreCase = true) || raw.equals("wav", ignoreCase = true) -> "WAV"
        raw.equals("x-flac", ignoreCase = true) || raw.equals("flac", ignoreCase = true) -> "FLAC"
        raw.equals("ogg", ignoreCase = true) || raw.equals("vorbis", ignoreCase = true) -> "OGG"
        else -> raw.uppercase()
    }
}

private fun isHiResSampleRate(sampleRateHz: Int?): Boolean =
    sampleRateHz != null && sampleRateHz > 48_000

private enum class TrackInfoTagTone(
    val background: Color,
    val border: Color,
    val content: Color,
) {
    Format(
        background = Color(0xFFEFF7FF),
        border = Color(0xFFCFE7FF),
        content = Color(0xFF1375B8),
    ),
    Neutral(
        background = Color(0xFFF4F4F5),
        border = Color(0xFFDDDEE2),
        content = Color(0xFF646870),
    ),
    Gold(
        background = Color(0xFFFFF5DF),
        border = Color(0xFFEAD09A),
        content = Color(0xFF765516),
    ),
}

internal val LibraryBottomControlsPadding = 150.dp
