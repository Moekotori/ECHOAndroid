package app.echo.android.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.library.EchoTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun NavidromePlaylistPicker(actions: RemoteLibraryWritebackActions, track: EchoTrack, onDismiss: () -> Unit) {
    val playlists by actions.playlists.collectAsState(emptyList())
    var busy by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(actions) {
        try { actions.navidrome?.refreshPlaylists?.invoke() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
        finally { busy = false }
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(stringResource(R.string.nav_add_to_playlist)) },
        text = { LazyColumn(Modifier.heightIn(max = 400.dp)) {
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (failed) item { Text(stringResource(R.string.nav_error), color = MaterialTheme.colorScheme.error) }
            if (!busy && playlists.isEmpty()) item { Text(stringResource(R.string.remote_playlist_empty)) }
            items(playlists, key = { it.id }) { playlist ->
                TextButton(enabled = !busy, onClick = {
                    busy = true; failed = false
                    scope.launch {
                        try { actions.add(playlist.id, track.id); onDismiss() }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { failed = true }
                        finally { busy = false }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text(playlist.name) }
            }
        } }, confirmButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.remote_playlist_close)) } })
}
