package app.echo.android.feature.library

import app.echo.android.design.EchoModalSheet
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.ArtworkTile
import app.echo.android.design.EchoIcon
import app.echo.android.design.echoClickable
import app.echo.android.model.library.EchoPlaylist
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryBatchPlaylistSheet(
    playlists: List<EchoPlaylist>,
    count: Int,
    onDismiss: () -> Unit,
    onSelect: (EchoPlaylist) -> Unit,
) {
    val editable = remember(playlists) { playlists.filter(EchoPlaylist::canEdit).distinctBy(EchoPlaylist::id) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    EchoModalSheet(
        onDismissRequest = { if (!closing) onDismiss() },
        sheetState = sheet,
    ) {
        LazyColumn(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "heading") {
                Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.batch_playlist), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        pluralStringResource(R.plurals.batch_selected_count, count, count),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (editable.isEmpty()) item(key = "empty") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.batch_playlists_empty), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.batch_cancel)) }
                }
            }
            items(editable, key = EchoPlaylist::id) { playlist ->
                Surface(color = androidx.compose.ui.graphics.Color.Transparent, shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)) {
                    Row(
                        Modifier.fillMaxWidth().echoClickable(enabled = !closing) {
                            closing = true
                            scope.launch {
                                try { sheet.hide(); onSelect(playlist) }
                                finally { closing = false }
                            }
                        }.padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        ArtworkTile(playlist.artworkUri, Modifier.size(48.dp), accent = rememberLibraryArtworkAccent(), cornerRadius = 6.dp, elevation = 0.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(playlist.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(stringResource(R.string.feature_library_count_tracks_73b21a, playlist.trackCount.toString()),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        EchoIcon(Icons.AutoMirrored.Rounded.PlaylistAdd, null, Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}
