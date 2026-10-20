package app.echo.android.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.ArtworkTile
import app.echo.android.design.displayMetadataOrUnknown
import app.echo.android.design.formatDuration
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.EchoTrackMetadataUpdate

@Composable
internal fun PlaylistTrackPlaceholder(editing: Boolean) {
    // Paging placeholders must reserve row height instead of making the lazy list lay out the entire catalog.
    Spacer(Modifier.fillMaxWidth().height(if (editing) 116.dp else 68.dp))
}

@Composable
internal fun PlaylistTrackRow(
    track: EchoTrack,
    accent: Color,
    onClick: () -> Unit,
    onUpdateTrackMetadata: (suspend (EchoTrackMetadataUpdate) -> Boolean)?,
    onImportLyrics: ((EchoTrack) -> Unit)?,
    onPickArtwork: ((EchoTrack) -> Unit)?,
    onMatchNeteaseMetadata: ((EchoTrack) -> Unit)?,
    onAddToPlaylist: ((EchoTrack) -> Unit)?,
    onPlayNext: ((EchoTrack) -> Unit)?,
    onAddNextUp: ((EchoTrack) -> Unit)?,
    onEnqueue: ((EchoTrack) -> Unit)?,
    onRemoveFromPlaylist: ((EchoTrack) -> Unit)?,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onOpenArtist: ((EchoTrack) -> Unit)? = null,
    onOpenAlbum: ((EchoTrack) -> Unit)? = null,
    modifier: Modifier = Modifier,
    editing: Boolean = false,
    editBusy: Boolean = false,
) {
    val colors = rememberPlaylistDetailColors()
    TrackContextMenu(
        track = track,
        onPlay = onClick,
        onUpdateTrackMetadata = onUpdateTrackMetadata,
        onImportLyrics = onImportLyrics,
        onPickArtwork = onPickArtwork,
        onMatchNeteaseMetadata = onMatchNeteaseMetadata,
        onAddToPlaylist = onAddToPlaylist,
        onPlayNext = onPlayNext,
        onAddNextUp = onAddNextUp,
        onEnqueue = onEnqueue,
        onRemoveFromPlaylist = onRemoveFromPlaylist,
        onMoveUp = onMoveUp,
        onMoveDown = onMoveDown,
        onOpenArtist = onOpenArtist?.let { open -> { open(track) } },
        onOpenAlbum = onOpenAlbum?.let { open -> { open(track) } },
        showMoreAction = !editing,
        modifier = modifier.fillMaxWidth(),
    ) { pressModifier ->
        Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .then(pressModifier)
                .padding(end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ArtworkTile(
                artworkUri = track.artworkUri,
                modifier = Modifier.size(44.dp),
                accent = accent,
                cornerRadius = 7.dp,
                elevation = 0.dp,
                placeholderIconSize = 24.dp,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    displayMetadataOrUnknown(track.title, unknownTrackLabel()),
                    color = colors.content,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    displayMetadataOrUnknown(track.artist, unknownArtistLabel()),
                    color = colors.muted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                formatDuration(track.durationMs),
                color = colors.muted.copy(alpha = 0.82f),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Normal,
            )
        }
        if (editing) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            onMoveUp?.let { move -> TextButton(onClick = move, enabled = !editBusy) {
                Text(stringResource(R.string.feature_library_move_up_e6d961), color = colors.content)
            } }
            onMoveDown?.let { move -> TextButton(onClick = move, enabled = !editBusy) {
                Text(stringResource(R.string.feature_library_move_down_cf81ae), color = colors.content)
            } }
            onRemoveFromPlaylist?.let { remove -> TextButton(onClick = { remove(track) }, enabled = !editBusy) {
                Text(stringResource(R.string.feature_library_remove_from_playlist_f07718), color = colors.content)
            } }
        }
        }
    }
}
