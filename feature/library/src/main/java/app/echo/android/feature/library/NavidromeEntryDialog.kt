package app.echo.android.feature.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoArtworkImage
import app.echo.android.design.EchoArtworkSize
import app.echo.android.model.library.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun NavidromeEntryDialog(actions: NavidromeActions, entry: NavidromeEntry,
    playlistActions: RemoteLibraryWritebackActions, onChanged: () -> Unit, onDismiss: () -> Unit) {
    var info by remember(entry) { mutableStateOf<NavidromeInformation?>(null) }
    var loading by remember(entry) { mutableStateOf(true) }
    var busy by remember(entry) { mutableStateOf(false) }
    var failed by remember(entry) { mutableStateOf(false) }
    var bookmark by remember(entry) { mutableStateOf(false) }
    var adding by remember(entry) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val current = info?.entry ?: entry
    fun execute(command: NavidromeCommand, update: (NavidromeEntry) -> NavidromeEntry) {
        if (busy) return
        busy = true; failed = false
        scope.launch {
            try { actions.execute(command); info = (info ?: NavidromeInformation(entry)).copy(entry = update(current)); onChanged() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }
    LaunchedEffect(actions, entry) {
        try { info = actions.information(entry) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
        finally { loading = false }
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(current.name) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 500.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (loading || busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (failed) item { Text(stringResource(R.string.nav_error), color = MaterialTheme.colorScheme.error) }
                current.artworkUri?.let { artwork -> item { EchoArtworkImage(artwork, null, Modifier.size(120.dp), sizeClass = EchoArtworkSize.Hero) } }
                item {
                    TextButton(enabled = !busy && !loading, onClick = {
                        execute(NavidromeCommand.Favorite(current.kind, current.id, !current.favorite)) { it.copy(favorite = !it.favorite) }
                    }) { Text(stringResource(if (current.favorite) R.string.nav_unfavorite else R.string.nav_favorite)) }
                    Text(stringResource(R.string.nav_rating), style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        (0..5).forEach { rating ->
                            TextButton(enabled = !busy && !loading, onClick = {
                                execute(NavidromeCommand.Rating(current.id, rating)) { it.copy(rating = rating) }
                            }, modifier = Modifier.defaultMinSize(minWidth = 44.dp), contentPadding = PaddingValues(4.dp)) {
                                Text(if (rating == current.rating) "[$rating]" else rating.toString())
                            }
                        }
                    }
                    if (current.kind == NavidromeKind.Track) TextButton(enabled = !busy, onClick = { bookmark = true }) {
                        Text(stringResource(R.string.nav_bookmark_add))
                    }
                    if (current.kind == NavidromeKind.Track) TextButton(enabled = !busy, onClick = { adding = true }) {
                        Text(stringResource(R.string.nav_add_to_playlist))
                    }
                }
                info?.description?.let { description -> item { Text(description, style = MaterialTheme.typography.bodyMedium) } }
                info?.sourceUrl?.let { url -> item {
                    TextButton(onClick = { try { uriHandler.openUri(url) } catch (_: Exception) { failed = true } }) { Text("Last.fm") }
                } }
                items(info?.facts.orEmpty()) { (key, value) ->
                    Column { Text(navidromeFactLabel(key), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }, confirmButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.remote_playlist_close)) } })
    if (bookmark) NavidromeBookmarkDialog(current, onDismiss = { bookmark = false }, onSave = { position, comment ->
        actions.execute(NavidromeCommand.Bookmark(current.id, position, comment)); onChanged(); bookmark = false
    })
    if (adding) NavidromePlaylistPicker(playlistActions, requireNotNull(current.track), onDismiss = { adding = false })
}

@Composable
private fun navidromeFactLabel(key: String): String {
    val id = when (key) {
        "album" -> R.string.nav_album
        "artists" -> R.string.nav_artists
        "albumArtists", "displayAlbumArtist" -> R.string.nav_album_artist
        "genre", "genres" -> R.string.nav_genres
        "displayComposer", "contributors" -> R.string.nav_credits
        "year", "releaseDate" -> R.string.nav_release_date
        "originalReleaseDate" -> R.string.nav_original_date
        "duration" -> R.string.nav_duration
        "track" -> R.string.nav_track_number
        "discNumber", "discTitles" -> R.string.nav_discs
        "samplingRate" -> R.string.nav_sample_rate
        "bitDepth" -> R.string.nav_bit_depth
        "bitRate" -> R.string.nav_bitrate
        "channelCount" -> R.string.nav_channels
        "playCount" -> R.string.nav_play_count
        "played" -> R.string.nav_last_played
        "explicitStatus" -> R.string.nav_explicit
        "comment" -> R.string.nav_comment
        "recordLabels" -> R.string.nav_labels
        "releaseTypes", "version" -> R.string.nav_version
        "created" -> R.string.nav_added
        "works", "movements", "groupings" -> R.string.nav_works
        "moods" -> R.string.nav_moods
        "sortName" -> R.string.nav_sort_name
        "replayGain" -> R.string.nav_replay_gain
        else -> null
    }
    return id?.let { stringResource(it) } ?: when (key) { "musicBrainzId" -> "MusicBrainz"; "isrc" -> "ISRC"; "bpm" -> "BPM"; else -> key }
}

@Composable
internal fun NavidromeTextDialog(title: String, initial: String, label: String, onDismiss: () -> Unit, onSave: suspend (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(title) },
        text = { Column {
            OutlinedTextField(value, { value = it.take(1000) }, enabled = !busy, label = { Text(label) })
            if (failed) Text(stringResource(R.string.nav_error), color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true; failed = false
            scope.launch { try { onSave(value) } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { failed = true } finally { busy = false } }
        }) { Text(stringResource(R.string.remote_playlist_save)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.remote_playlist_close)) } })
}

@Composable
private fun NavidromeBookmarkDialog(entry: NavidromeEntry, onDismiss: () -> Unit, onSave: suspend (Long, String) -> Unit) {
    var seconds by remember { mutableStateOf((entry.positionMs / 1000).toString()) }
    var comment by remember { mutableStateOf(entry.comment) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val position = seconds.toLongOrNull()?.takeIf { it in 0..Long.MAX_VALUE / 1000 }?.times(1000)
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(stringResource(R.string.nav_bookmark_add)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(seconds, { seconds = it.take(12) }, enabled = !busy, singleLine = true,
                label = { Text(stringResource(R.string.nav_position_seconds)) })
            OutlinedTextField(comment, { comment = it.take(1000) }, enabled = !busy, label = { Text(stringResource(R.string.nav_comment)) })
            if (failed) Text(stringResource(R.string.nav_error), color = MaterialTheme.colorScheme.error)
        } }, confirmButton = { TextButton(enabled = !busy && position != null &&
            (entry.track?.durationMs.let { it == null || it <= 0 || position < it }), onClick = {
                busy = true; scope.launch { try { onSave(requireNotNull(position), comment) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { failed = true } finally { busy = false } }
            }) { Text(stringResource(R.string.remote_playlist_save)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.remote_playlist_close)) } })
}
