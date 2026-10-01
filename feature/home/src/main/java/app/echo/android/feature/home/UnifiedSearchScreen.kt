package app.echo.android.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.library.*
import app.echo.android.model.connect.EchoRemoteTrack

@Composable
fun UnifiedSearchScreen(query: String, loading: Boolean, results: EchoUnifiedSearch,
    onQuery: (String) -> Unit, onBack: () -> Unit, onMoments: () -> Unit,
    onTrack: (EchoTrack) -> Unit, onAlbum: (AlbumSummary) -> Unit, onArtist: (ArtistSummary) -> Unit,
    onPlaylist: (EchoPlaylist) -> Unit, onMoment: (EchoSavedMoment) -> Unit, onPcTrack: (EchoRemoteTrack) -> Unit) {
    var category by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.search_back)) }
            OutlinedTextField(query, onQuery, label = { Text(stringResource(R.string.search_everywhere)) }, singleLine = true, modifier = Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(R.string.search_all, R.string.search_library, R.string.search_pc).forEachIndexed { index, label ->
                FilterChip(category == index, { category = index }, label = { Text(stringResource(label)) })
            }
            TextButton(onClick = onMoments) { Text(stringResource(R.string.moments_title)) }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (results.localUnavailable || results.pcUnavailable) item { Text(stringResource(R.string.search_partial), color = MaterialTheme.colorScheme.error) }
            if (category != 2) {
                if (results.moments.isNotEmpty()) item { SearchGroupLabel(R.string.moments_title) }
                items(results.moments, key = { "moment:${it.bookmark.id}" }) { moment -> SearchEntry(moment.bookmark.label, "${moment.track.title} · ${moment.track.artist}") { onMoment(moment) } }
                if (results.playlists.isNotEmpty()) item { SearchGroupLabel(R.string.search_playlists) }
                items(results.playlists, key = { "playlist:${it.id}" }) { SearchEntry(it.name, stringResource(R.string.search_library)) { onPlaylist(it) } }
                if (results.tracks.isNotEmpty()) item { SearchGroupLabel(R.string.search_tracks) }
                items(results.tracks, key = { "track:${it.id}" }) { track ->
                    val origin = stringResource(if (track.source.isLocalAudioFile) R.string.search_phone else R.string.search_remote)
                    SearchEntry(track.title, "${track.artist} · $origin") { onTrack(track) }
                }
                if (results.albums.isNotEmpty()) item { SearchGroupLabel(R.string.search_albums) }
                items(results.albums, key = { "album:${it.albumKey}" }) { SearchEntry(it.title, it.artist.orEmpty()) { onAlbum(it) } }
                if (results.artists.isNotEmpty()) item { SearchGroupLabel(R.string.search_artists) }
                items(results.artists, key = { "artist:${it.artistKey}" }) { SearchEntry(it.name, stringResource(R.string.search_library)) { onArtist(it) } }
            }
            if (category != 1) {
                if (results.pcTracks.isNotEmpty()) item { SearchGroupLabel(R.string.search_pc) }
                items(results.pcTracks, key = { "pc:${it.id}" }) { SearchEntry(it.title, it.artist) { onPcTrack(it) } }
            }
            val libraryEmpty = results.tracks.isEmpty() && results.albums.isEmpty() && results.artists.isEmpty() && results.playlists.isEmpty() && results.moments.isEmpty()
            if (!loading && query.isNotBlank() && when(category) { 1 -> libraryEmpty; 2 -> results.pcTracks.isEmpty(); else -> libraryEmpty && results.pcTracks.isEmpty() }) item {
                Text(stringResource(R.string.search_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (query.isBlank()) item { Text(stringResource(R.string.search_hint), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable private fun SearchGroupLabel(label: Int) { Text(stringResource(label), style = MaterialTheme.typography.titleLarge) }
@Composable private fun SearchEntry(title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
