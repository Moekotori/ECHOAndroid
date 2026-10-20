package app.echo.android.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.EchoTrackVersions

val LocalTrackVersionsAction = staticCompositionLocalOf<((EchoTrack) -> Unit)?> { null }

@Composable
fun TrackVersionsDialog(
    versions: EchoTrackVersions,
    saving: Boolean = false,
    error: Boolean = false,
    onChoose: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.track_versions_title)) },
        text = {
            Column {
                if (error) Text(stringResource(R.string.track_versions_error), color = MaterialTheme.colorScheme.error)
                Text(stringResource(R.string.track_versions_explanation), style = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    item {
                        TextButton(onClick = { onChoose(null) }, enabled = !saving) {
                            RadioButton(selected = versions.preferredTrackId == null, onClick = null)
                            Text(stringResource(R.string.track_versions_auto))
                        }
                    }
                    items(versions.tracks, key = { it.id }) { track ->
                        TextButton(onClick = { onChoose(track.id) }, enabled = !saving) {
                            RadioButton(selected = versions.preferredTrackId == track.id, onClick = null)
                            Column(Modifier.weight(1f)) {
                                Text(track.title)
                                Text("${trackVersionSourceLabel(track.source.id)} · ${track.mimeType.orEmpty()} · ${track.sampleRateHz?.let { "$it Hz" }.orEmpty()}",
                                    style = MaterialTheme.typography.bodySmall)
                                Text(track.album.orEmpty(), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (versions.tracks.size < 2) item {
                        Text(stringResource(R.string.track_versions_empty), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.track_versions_close)) } },
    )
}

@Composable
private fun trackVersionSourceLabel(source: String): String = when (source.substringBefore(':')) {
    "mediastore", "saf" -> stringResource(R.string.feature_library_local_library_ddf4b1)
    "subsonic" -> "Subsonic / Navidrome"
    "jellyfin" -> "Jellyfin / Emby"
    "webdav" -> "WebDAV"
    "smb" -> "SMB"
    "echo-link" -> "Echo Link"
    else -> stringResource(R.string.feature_library_unknown_source_d8c291)
}
