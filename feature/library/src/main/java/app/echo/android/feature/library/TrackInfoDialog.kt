package app.echo.android.feature.library

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.displayMetadataOrUnknown
import app.echo.android.design.echoClickable
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.libraryDisplayTitle
import app.echo.android.feature.library.R as L10nR

@Composable
fun TrackInfoDialog(
    track: EchoTrack,
    onDismiss: () -> Unit,
    onOpenArtist: (() -> Unit)? = null,
    mediaTools: LibraryMediaTools? = LocalLibraryMediaTools.current,
    audioFormat: TrackInfoAudioFormat? = null,
) {
    var tool by remember(track.id) { mutableStateOf<String?>(null) }
    val artistLabel = stringResource(L10nR.string.feature_library_go_to_artist)
    val fields = trackInfoFields(track, audioFormat)
    var playlistRetry by remember(track.id) { mutableIntStateOf(0) }
    val membership by rememberTrackInfoPlaylists(track, mediaTools, playlistRetry)
    val playlistLabel = stringResource(L10nR.string.track_info_playlists)
    val likedName = stringResource(L10nR.string.feature_library_liked_songs_8d6245)
    val missing = stringResource(L10nR.string.feature_library_not_available_7d3220)
    val membershipMessage = when {
        membership.loading -> stringResource(L10nR.string.feature_library_loading_playlist_eb6248)
        membership.failed -> stringResource(L10nR.string.track_info_playlists_failed)
        membership.playlists == null -> missing
        membership.playlists.orEmpty().isEmpty() -> stringResource(L10nR.string.track_info_playlists_empty)
        else -> null
    }
    fun playlistName(playlist: app.echo.android.model.library.EchoPlaylist): String =
        if (playlist.isLikedSongs) likedName else playlist.name.ifBlank { missing }
    val context = LocalContext.current
    val copyLabel = stringResource(L10nR.string.track_info_copy)
    val copiedLabel = stringResource(L10nR.string.track_info_copied)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                displayMetadataOrUnknown(track.libraryDisplayTitle(LocalLibraryFileNames.current), unknownTrackLabel()),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                mediaTools?.setRating?.let { setter -> item { TrackRatingControl(track, setter) } }
                fields.forEachIndexed { index, field ->
                    item(key = "field:$index") {
                        TrackInfoLine(field.label, field.value,
                            onClick = onOpenArtist?.takeIf { field.opensArtist && track.artist.isNotBlank() },
                            onClickLabel = artistLabel,
                        )
                    }
                    // Put collection memberships next to the identity/tags, ahead of technical details.
                    if (index == 5) {
                        if (membershipMessage != null) item(key = "playlist-status") {
                            Column {
                                TrackInfoLine(playlistLabel, membershipMessage)
                                if (membership.failed) TextButton(onClick = { playlistRetry++ }) {
                                    Text(stringResource(L10nR.string.hidden_track_retry))
                                }
                            }
                        }
                        items(membership.playlists.orEmpty(), key = { "playlist:${it.id}" }) { playlist ->
                            TrackInfoLine(playlistLabel, playlistName(playlist))
                        }
                        item(key = "playlist-scope") {
                            Text(stringResource(L10nR.string.track_info_playlists_scope),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (mediaTools != null && track.source.isLocalAudioFile) item(key = "media-tools") {
                    Column {
                        TextButton(onClick = { tool = "encoding" }) { Text(stringResource(L10nR.string.encoding_title)) }
                        TextButton(onClick = { tool = "images" }) { Text(stringResource(L10nR.string.album_images_title)) }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !membership.loading && !membership.failed, onClick = {
                context.getSystemService(ClipboardManager::class.java)?.let { clipboard ->
                    clipboard.setPrimaryClip(ClipData.newPlainText(copyLabel,
                        buildString {
                            fields.forEach { append(it.label).append(": ").append(it.value).append('\n') }
                            append(playlistLabel).append(": ")
                            if (membershipMessage != null) append(membershipMessage)
                            else membership.playlists.orEmpty().forEach { append('\n').append(playlistName(it)) }
                        }))
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        Toast.makeText(context, copiedLabel, Toast.LENGTH_SHORT).show()
                    }
                }
            }) { Text(copyLabel) }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(L10nR.string.feature_library_done_a5e659))
            }
        },
    )
    if (mediaTools != null) when (tool) {
        "encoding" -> TrackEncodingDialog(track, mediaTools) { tool = null }
        "images" -> AlbumImagesDialog(track, mediaTools) { tool = null }
    }
}

@Composable
private fun TrackInfoLine(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.echoClickable(
                        onClickLabel = onClickLabel,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            modifier = Modifier.widthIn(min = 76.dp, max = 96.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
        SelectionContainer(Modifier.weight(1f)) {
            Text(
                value,
                color = if (onClick != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

