package app.echo.android.feature.library

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
    val copyLabel = stringResource(R.string.track_info_copy)
    val copiedLabel = stringResource(R.string.track_info_copied)
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
                fields.forEach { field ->
                    TrackInfoLine(field.label, field.value,
                        onClick = onOpenArtist?.takeIf { field.opensArtist && track.artist.isNotBlank() },
                        onClickLabel = artistLabel,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                context.getSystemService(ClipboardManager::class.java)?.let { clipboard ->
                    clipboard.setPrimaryClip(ClipData.newPlainText(copyLabel,
                        fields.joinToString("\n") { "${it.label}: ${it.value}" }))
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
            maxLines = 1,
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
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

