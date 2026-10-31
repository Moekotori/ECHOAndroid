package app.echo.android.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import app.echo.android.design.formatDuration
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.libraryDisplayTitle
import app.echo.android.feature.library.R as L10nR

@Composable
fun TrackInfoDialog(
    track: EchoTrack,
    onDismiss: () -> Unit,
    onOpenArtist: (() -> Unit)? = null,
    mediaTools: LibraryMediaTools? = LocalLibraryMediaTools.current,
) {
    var tool by remember(track.id) { mutableStateOf<String?>(null) }
    val artistLabel = stringResource(L10nR.string.feature_library_go_to_artist)
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
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mediaTools != null && track.source.isLocalAudioFile) {
                    TextButton(onClick = { tool = "encoding" }) { Text(stringResource(L10nR.string.encoding_title)) }
                    TextButton(onClick = { tool = "images" }) { Text(stringResource(L10nR.string.album_images_title)) }
                }
                val notProvided = stringResource(L10nR.string.feature_library_not_available_7d3220)
                TrackInfoLine(
                    stringResource(L10nR.string.feature_library_artist_b6e7ad),
                    displayMetadataOrUnknown(track.artist, unknownArtistLabel()),
                    onClick = onOpenArtist?.takeIf { track.artist.isNotBlank() },
                    onClickLabel = artistLabel,
                )
                TrackInfoLine(
                    stringResource(L10nR.string.feature_library_album_eb13be),
                    displayMetadataOrUnknown(track.album, unknownAlbumLabel()),
                )
                TrackInfoLine(
                    stringResource(L10nR.string.feature_library_album_artist_8a1c9e),
                    track.albumArtist?.takeIf { it.isNotBlank() } ?: notProvided,
                )
                TrackInfoLine(
                    stringResource(L10nR.string.metadata_composer),
                    track.composer?.takeIf { it.isNotBlank() } ?: notProvided,
                )
                TrackInfoLine(
                    stringResource(L10nR.string.feature_library_track_000e4d),
                    track.trackNumber?.toString() ?: notProvided,
                )
                TrackInfoLine(
                    stringResource(L10nR.string.feature_library_disc_3128c0),
                    track.discNumber?.toString() ?: notProvided,
                )
                TrackInfoLine(
                    stringResource(L10nR.string.feature_library_year_fe373f),
                    track.year?.toString() ?: notProvided,
                )
                TrackInfoLine(
                    stringResource(L10nR.string.feature_library_format_a7775b),
                    formatTrackMimeType(track.mimeType)
                        ?: track.mimeType?.takeIf { it.isNotBlank() }
                        ?: notProvided,
                )
                TrackInfoLine(
                    stringResource(L10nR.string.feature_library_sample_rate_f6382c),
                    track.sampleRateHz?.let(::formatTrackSampleRate) ?: notProvided,
                )
                TrackInfoLine(
                    stringResource(L10nR.string.feature_library_duration_573e08),
                    formatDuration(track.durationMs),
                )
                TrackInfoLine(
                    stringResource(L10nR.string.feature_library_size_f63a75),
                    formatTrackFileSize(track.sizeBytes),
                )
                TrackInfoLine(stringResource(L10nR.string.feature_library_source_4aff16), track.source.id)
            }
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
            maxLines = 1,
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            color = if (onClick != null) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

