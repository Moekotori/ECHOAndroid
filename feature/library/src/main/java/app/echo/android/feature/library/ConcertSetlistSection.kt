package app.echo.android.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.library.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun ConcertSetlistSection(
    query: ArtistOnlineQuery,
    event: ArtistConcert,
    loader: app.echo.android.model.library.ArtistOnlineInfoLoader?,
    matcher: app.echo.android.model.library.ArtistSetlistMatcher?,
    onPlayMatched: ((List<String>) -> Unit)?,
) {
    var setlist by remember(query, event, loader, matcher) { mutableStateOf<ArtistSetlist?>(null) }
    var loading by remember(query, event, loader, matcher) { mutableStateOf(true) }
    var failed by remember(query, event, loader, matcher) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(query, event, loader, matcher) {
        if (loader == null) {
            loading = false
            failed = true
            return@LaunchedEffect
        }
        loading = true
        failed = false
        try {
            val remote = loader.loadSetlist(query, event, false)
            setlist = matcher?.match(remote) ?: remote
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        } finally {
            loading = false
        }
    }
    val current = setlist
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            loading -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            failed -> Text(stringResource(R.string.artist_setlist_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            current?.missingApiKey == true -> Text(stringResource(R.string.artist_setlist_need_key), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            current == null || current.songs.isEmpty() -> Text(stringResource(R.string.artist_setlist_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> {
                if (!current.exactDate) {
                    Text(stringResource(R.string.artist_setlist_recent), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (current.stale) {
                    Text(stringResource(R.string.artist_online_stale, "setlist.fm"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val playable = remember(current) { playableSetlistTrackIds(current.songs) }
                if (playable.isNotEmpty() && onPlayMatched != null) {
                    Button(onClick = { onPlayMatched(playable) }) {
                        Text(stringResource(R.string.artist_setlist_play_matched, playable.size, current.songs.size))
                    }
                }
                current.songs.forEach { song ->
                    val matched = song.trackId != null
                    Text(
                        text = if (song.tape) stringResource(R.string.artist_setlist_tape, song.title) else song.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (matched) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = if (matched && onPlayMatched != null) {
                            Modifier.clickable { onPlayMatched(listOf(song.trackId!!)) }
                        } else {
                            Modifier
                        },
                    )
                }
            }
        }
        TextButton(enabled = !loading && loader != null, onClick = {
            if (loader == null) return@TextButton
            scope.launch {
                loading = true
                failed = false
                try {
                    val remote = loader.loadSetlist(query, event, true)
                    setlist = matcher?.match(remote) ?: remote
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    failed = true
                } finally {
                    loading = false
                }
            }
        }) { Text(stringResource(R.string.artist_setlist_refresh)) }
    }
}
