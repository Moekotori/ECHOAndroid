package app.echo.android.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.design.ArtworkTile
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.displayMetadataOrUnknown
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.EchoTrackMetadataUpdate
import app.echo.android.model.library.libraryDisplayTitle

internal val LibraryTrackRowMinHeight = 74.dp

@Composable
internal fun TrackRow(
    track: EchoTrack,
    onClick: () -> Unit,
    onUpdateTrackMetadata: (suspend (EchoTrackMetadataUpdate) -> Boolean)? = null,
    onImportLyrics: ((EchoTrack) -> Unit)? = null,
    onPickArtwork: ((EchoTrack) -> Unit)? = null,
    onMatchNeteaseMetadata: ((EchoTrack) -> Unit)? = null,
    onAddToPlaylist: ((EchoTrack) -> Unit)? = null,
    onPlayNext: ((EchoTrack) -> Unit)? = null,
    onEnqueue: ((EchoTrack) -> Unit)? = null,
    onRemoveFromPlaylist: ((EchoTrack) -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onOpenArtist: ((EchoTrack) -> Unit)? = null,
    onOpenAlbum: ((EchoTrack) -> Unit)? = null,
    onPlayOnPc: (() -> Unit)? = null,
    showAudioInfoTags: Boolean = true,
    modifier: Modifier = Modifier,
    isCurrentTrack: Boolean = false,
    isPlaying: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val badge = remember(showAudioInfoTags, track.mimeType, track.sampleRateHz) {
        if (showAudioInfoTags) {
            formatTrackMimeType(track.mimeType)
                ?: track.sampleRateHz?.takeIf { it > 0 }?.let(::formatTrackSampleRate)
        } else null
    }
    val highResolution = (track.sampleRateHz ?: 0) >= 88_200 && isLosslessFormat(badge)

    TrackContextMenu(
        track = track,
        onPlay = onClick,
        onUpdateTrackMetadata = onUpdateTrackMetadata,
        onImportLyrics = onImportLyrics,
        onPickArtwork = onPickArtwork,
        onMatchNeteaseMetadata = onMatchNeteaseMetadata,
        onAddToPlaylist = onAddToPlaylist,
        onPlayNext = onPlayNext,
        onEnqueue = onEnqueue,
        onRemoveFromPlaylist = onRemoveFromPlaylist,
        onMoveUp = onMoveUp,
        onMoveDown = onMoveDown,
        onOpenArtist = onOpenArtist?.let { open -> { open(track) } },
        onOpenAlbum = onOpenAlbum?.let { open -> { open(track) } },
        onPlayOnPc = onPlayOnPc,
        showMoreAction = true,
        inlineSongActions = true,
        modifier = modifier.fillMaxWidth(),
    ) { pressModifier ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(pressModifier)
                .heightIn(min = LibraryTrackRowMinHeight)
                .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ArtworkTile(
                track.artworkUri,
                Modifier.size(52.dp),
                accent = rememberLibraryArtworkAccent(),
                cornerRadius = 4.dp,
                elevation = 0.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (isCurrentTrack) AlbumPlayingIndicator(isPlaying = isPlaying)
                    Text(
                        text = displayMetadataOrUnknown(
                            track.libraryDisplayTitle(LocalLibraryFileNames.current),
                            unknownTrackLabel(),
                        ),
                        color = scheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge,
                        fontSize = 17.sp,
                        lineHeight = 21.sp,
                        letterSpacing = 0.sp,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    if (badge != null) TrackFormatBadge(badge, highResolution)
                    Text(
                        text = trackSubtitle(track, separator = " - "),
                        modifier = Modifier.weight(1f),
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackFormatBadge(label: String, highResolution: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    val background = when {
        !highResolution -> scheme.surfaceVariant
        dark -> Color(0xFF553D13)
        else -> Color(0xFFF9E2A6)
    }
    val foreground = when {
        !highResolution -> scheme.onSurfaceVariant
        dark -> Color(0xFFF9E2A6)
        else -> Color(0xFF624309)
    }
    Text(
        text = label,
        modifier = Modifier
            .widthIn(max = 66.dp)
            .background(background, RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp),
        color = foreground,
        style = MaterialTheme.typography.labelSmall,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
