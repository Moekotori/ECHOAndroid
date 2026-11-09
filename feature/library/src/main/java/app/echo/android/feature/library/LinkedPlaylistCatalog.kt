package app.echo.android.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.ArtworkTile
import app.echo.android.design.EchoIcon
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.echoClickable
import app.echo.android.design.echoTheme
import app.echo.android.model.connect.EchoRemotePlaylist
import app.echo.android.feature.library.R as L10nR

@Composable
internal fun LinkedPlaylistList(
    playlists: List<EchoRemotePlaylist>,
    onOpenPlaylist: (EchoRemotePlaylist) -> Unit,
    onOpenLikedSongs: () -> Unit,
    onOpenLikedAlbums: () -> Unit,
    onLoadArtwork: suspend (EchoRemotePlaylist) -> String?,
    query: String,
    isLoading: Boolean,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    loadedCount: Int,
    totalCount: Int,
    isLoadingMore: Boolean,
    hasError: Boolean,
    onLoadMore: () -> Unit,
) {
    val listState = rememberLazyListState()
    val requestTotal = if (playlists.size < loadedCount) playlists.size else totalCount
    val favoritesCount = if (query.isBlank()) 2 else 0
    LoadMoreWhenNearEnd(listState, playlists.size + favoritesCount, requestTotal + favoritesCount,
        isLoading || isLoadingMore || !isActive || hasError, onLoadMore)
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = LibraryBottomControlsPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (query.isBlank()) {
            item(key = "linked-favorite-songs") {
                LinkedFavoritesEntry(
                    Icons.Rounded.Favorite,
                    stringResource(L10nR.string.feature_library_liked_songs_8d6245),
                    stringResource(L10nR.string.feature_library_pc_echo_e0a2d4),
                    onOpenLikedSongs,
                )
            }
            item(key = "linked-favorite-albums") {
                LinkedFavoritesEntry(
                    Icons.Rounded.Album,
                    stringResource(L10nR.string.album_liked_title),
                    stringResource(L10nR.string.feature_library_local_9b5178),
                    onOpenLikedAlbums,
                )
            }
        }
        if (playlists.isEmpty()) item(key = "linked-playlists-status") {
            LibraryCollectionEmpty(stringResource(when {
                isLoading -> L10nR.string.feature_library_reading_pc_echo_library_fccbe4
                query.isBlank() -> L10nR.string.feature_library_pc_echo_has_no_playlists_to_show_8e8438
                else -> L10nR.string.feature_library_pc_echo_has_no_matching_playlists_846ad7
            }))
        }
        items(
            items = playlists,
            key = { playlist -> playlist.id },
        ) { playlist ->
            val artwork = rememberLinkedPlaylistArtwork(playlist, isActive, onLoadArtwork)
            val displayedPlaylist = remember(playlist, artwork) {
                if (artwork == playlist.artworkUrl) playlist else playlist.copy(artworkUrl = artwork)
            }
            LinkedPlaylistRow(
                playlist = displayedPlaylist,
                onOpen = { onOpenPlaylist(displayedPlaylist) },
            )
        }
        if (totalCount > loadedCount) {
            item {
                TextButton(onClick = onLoadMore, enabled = isActive && !isLoadingMore) {
                    Text(stringResource(if (hasError) L10nR.string.library_retry else L10nR.string.linked_artists_load_more))
                }
            }
        }
    }
}

@Composable
private fun LinkedFavoritesEntry(icon: ImageVector, title: String, source: String, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().echoClickable(role = Role.Button, onClick = onOpen).heightIn(min = 52.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { EchoIcon(icon, contentDescription = null) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(source, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        EchoIcon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(24.dp))
    }
}

@Composable
internal fun LinkedPlaylistRow(
    playlist: EchoRemotePlaylist,
    onOpen: () -> Unit,
) {
    val accent = rememberLibraryControlColor()
    val dark = LocalEchoDarkTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (dark) echoTheme().panel.copy(alpha = 0.50f) else echoTheme().mist.copy(alpha = 0.46f))
            .echoClickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkTile(
            artworkUri = playlist.artworkUrl,
            modifier = Modifier.size(58.dp),
            accent = rememberLibraryArtworkAccent(),
            cornerRadius = 12.dp,
            elevation = 3.dp,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                playlist.name,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                linkedPlaylistSubtitle(playlist),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Surface(
            modifier = Modifier.size(38.dp),
            color = accent.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, echoTheme().glassBorder),
            shape = RoundedCornerShape(12.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                EchoIcon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun linkedPlaylistSubtitle(playlist: EchoRemotePlaylist): String {
    val source = playlist.sourceLabel?.takeIf { it.isNotBlank() } ?: "PC ECHO"
    return stringResource(L10nR.string.feature_library_playlist_trackcount_tracks_source_3431c6, playlist.trackCount.toString(), source)
}
