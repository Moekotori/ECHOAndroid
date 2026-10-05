package app.echo.android.feature.library

import app.echo.android.design.EchoIcon
import app.echo.android.design.EchoDropdownMenu
import app.echo.android.design.EchoDropdownMenuItem

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.Alignment
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import app.echo.android.design.rememberArtworkPalette
import app.echo.android.design.EchoContentMaxWidth
import app.echo.android.model.library.*
import app.echo.android.feature.library.R as L10nR

@Composable
internal fun ArtistDetailPage(
    artist: ArtistSummary,
    tracks: LazyPagingItems<EchoTrack>,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onPlayTrack: (EchoTrack) -> Unit,
    onUpdateTrackMetadata: (suspend (EchoTrackMetadataUpdate) -> Unit)? = null,
    onImportLyrics: ((EchoTrack) -> Unit)? = null,
    onPickArtwork: ((EchoTrack) -> Unit)? = null,
    onMatchNeteaseMetadata: ((EchoTrack) -> Unit)? = null,
    onAddToPlaylist: ((EchoTrack) -> Unit)? = null,
    onPlayNext: ((EchoTrack) -> Unit)? = null,
    onAddNextUp: ((EchoTrack) -> Unit)? = null,
    onEnqueue: ((EchoTrack) -> Unit)? = null,
    albums: LazyPagingItems<AlbumSummary>? = null,
    query: String = "",
    sort: LibraryTrackSortMode = LibraryTrackSortMode.Album,
    onQueryChange: (String) -> Unit = {},
    onSortChange: (LibraryTrackSortMode) -> Unit = {},
    onOpenAlbum: (AlbumSummary) -> Unit = {},
    onOpenTrackAlbum: ((EchoTrack) -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    albumListState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
) {
    val palette = rememberArtworkPalette(artist.artworkUri, seedKey = artist.artistKey)
    val onlineQuery = ArtistOnlineQuery(artist.name, albums?.itemSnapshotList?.items?.take(3)?.map { it.title }.orEmpty())
    ArtistDetailPager(artist, onlineQuery, onBack, modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = AlbumDetailBottomPadding),
        ) {
            item(key = "hero") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentWidth(Alignment.CenterHorizontally)
                        .widthIn(max = EchoContentMaxWidth)
                        .padding(horizontal = 24.dp),
                ) {
                    ArtistProfileHeader(artist)
                    Spacer(Modifier.height(20.dp))
                    ArtistPlaybackActions(artist.trackCount > 0, onPlayAll, onShuffle)
                    Spacer(Modifier.height(24.dp))
                    if (albums != null) ArtistAlbums(albums, palette, albumListState, onOpenAlbum)
                    Spacer(Modifier.height(22.dp))
                    ArtistTracksHeading(count = artist.trackCount)
                    Spacer(Modifier.height(10.dp))
                    ArtistTrackTools(query, sort, onQueryChange, onSortChange)
                    Spacer(Modifier.height(10.dp))
                }
            }

            when {
                tracks.isInitialPagingLoad() -> item(key = "loading") {
                    AlbumDetailNotice(stringResource(L10nR.string.feature_library_loading_tracks_8e2147))
                }
                tracks.isInitialPagingError() -> item(key = "error") {
                    ArtistRetry(onRetry = tracks::retry)
                }
                tracks.itemCount == 0 -> item(key = "empty") {
                    AlbumDetailNotice(stringResource(if (query.isBlank()) L10nR.string.feature_library_no_tracks_yet_c4614a else L10nR.string.artist_no_matches))
                }
                else -> items(
                    count = tracks.itemCount,
                    key = { index -> tracks.peek(index)?.id ?: "track-$index" },
                ) { index ->
                    tracks[index]?.let { track ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentWidth(Alignment.CenterHorizontally)
                                .widthIn(max = EchoContentMaxWidth)
                                .padding(horizontal = 24.dp),
                        ) {
                            TrackRow(
                                track = track,
                                showAudioInfoTags = false,
                                onClick = { onPlayTrack(track) },
                                onUpdateTrackMetadata = onUpdateTrackMetadata,
                                onImportLyrics = onImportLyrics,
                                onPickArtwork = onPickArtwork,
                                onMatchNeteaseMetadata = onMatchNeteaseMetadata,
                                onAddToPlaylist = onAddToPlaylist,
                                onPlayNext = onPlayNext,
                                onAddNextUp = onAddNextUp,
                                onEnqueue = onEnqueue,
                                onOpenAlbum = onOpenTrackAlbum,
                            )
                        }
                    }
                }
            }
            item(key = "paging-status") {
                when {
                    tracks.itemCount > 0 && (tracks.loadState.append is LoadState.Error || tracks.loadState.refresh is LoadState.Error) -> ArtistRetry(onRetry = tracks::retry)
                    tracks.loadState.append is LoadState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(20.dp))
                }
            }
        }
    }
}


@Composable
internal fun ArtistDetailListPage(
    artist: ArtistSummary,
    tracks: List<EchoTrack>,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onPlayOnPc: (() -> Unit)? = null,
    onPlayTrack: (EchoTrack) -> Unit,
    onUpdateTrackMetadata: (suspend (EchoTrackMetadataUpdate) -> Unit)? = null,
    onImportLyrics: ((EchoTrack) -> Unit)? = null,
    onPickArtwork: ((EchoTrack) -> Unit)? = null,
    onMatchNeteaseMetadata: ((EchoTrack) -> Unit)? = null,
    onOpenTrackAlbum: ((EchoTrack) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val onlineQuery = remember(artist.artistKey, artist.name, tracks) {
        ArtistOnlineQuery(artist.name, tracks.take(24).mapNotNull { it.album }.distinct().take(3))
    }
    ArtistDetailPager(artist, onlineQuery, onBack, modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = AlbumDetailBottomPadding),
        ) {
            item(key = "hero") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentWidth(Alignment.CenterHorizontally)
                        .widthIn(max = EchoContentMaxWidth)
                        .padding(horizontal = 24.dp),
                ) {
                    ArtistProfileHeader(artist)
                    Spacer(Modifier.height(18.dp))
                    ArtistPlaybackActions(tracks.isNotEmpty(), onPlayAll, onShuffle, onPlayOnPc)
                    Spacer(Modifier.height(24.dp))
                    ArtistTracksHeading(count = artist.trackCount)
                    Spacer(Modifier.height(10.dp))
                }
            }

            if (tracks.isEmpty()) {
                item(key = "empty") {
                    AlbumDetailNotice(stringResource(L10nR.string.feature_library_no_tracks_yet_c4614a))
                }
            } else {
                itemsIndexed(
                    items = tracks,
                    key = { _, track -> track.id },
                ) { _, track ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentWidth(Alignment.CenterHorizontally)
                            .widthIn(max = EchoContentMaxWidth)
                            .padding(horizontal = 24.dp),
                    ) {
                        TrackRow(
                            track = track,
                            showAudioInfoTags = false,
                            onClick = { onPlayTrack(track) },
                            onUpdateTrackMetadata = onUpdateTrackMetadata,
                            onImportLyrics = onImportLyrics,
                            onPickArtwork = onPickArtwork,
                            onMatchNeteaseMetadata = onMatchNeteaseMetadata,
                            onOpenAlbum = onOpenTrackAlbum,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistTrackTools(query: String, sort: LibraryTrackSortMode, onQueryChange: (String) -> Unit, onSortChange: (LibraryTrackSortMode) -> Unit) {
    var showSort by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurface),
            cursorBrush = SolidColor(scheme.primary),
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .background(scheme.surfaceContainerLow, RoundedCornerShape(10.dp))
                        .padding(start = 12.dp, end = if (query.isEmpty()) 12.dp else 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    EchoIcon(Icons.Rounded.Search, null, modifier = Modifier.size(20.dp), tint = scheme.onSurfaceVariant)
                    Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
                        if (query.isEmpty()) Text(stringResource(L10nR.string.artist_search_tracks),
                            style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                        innerTextField()
                    }
                    if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) {
                        EchoIcon(Icons.Rounded.Close, stringResource(L10nR.string.artist_clear_search), modifier = Modifier.size(18.dp))
                    }
                }
            },
        )
        Box {
            IconButton(onClick = { showSort = true }) { EchoIcon(Icons.AutoMirrored.Rounded.Sort, stringResource(L10nR.string.artist_sort_tracks)) }
            EchoDropdownMenu(expanded = showSort, onDismissRequest = { showSort = false }) {
                listOf(LibraryTrackSortMode.Album, LibraryTrackSortMode.Title, LibraryTrackSortMode.Duration).forEach { mode ->
                    EchoDropdownMenuItem(text = { Text(stringResource(when (mode) {
                        LibraryTrackSortMode.Title -> L10nR.string.artist_sort_title
                        LibraryTrackSortMode.Duration -> L10nR.string.artist_sort_duration
                        else -> L10nR.string.artist_sort_album
                    })) },
                        selected = mode == sort,
                        onClick = { onSortChange(mode); showSort = false })
                }
            }
        }
    }
}
