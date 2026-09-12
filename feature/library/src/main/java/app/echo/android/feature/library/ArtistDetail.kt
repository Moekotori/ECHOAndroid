package app.echo.android.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    onEnqueue: ((EchoTrack) -> Unit)? = null,
    albums: LazyPagingItems<AlbumSummary>? = null,
    query: String = "",
    sort: LibraryTrackSortMode = LibraryTrackSortMode.Album,
    onQueryChange: (String) -> Unit = {},
    onSortChange: (LibraryTrackSortMode) -> Unit = {},
    onOpenAlbum: (AlbumSummary) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    albumListState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
) {
    val palette = rememberArtworkPalette(artist.artworkUri, seedKey = artist.artistKey)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(440.dp)
                .background(
                    Brush.verticalGradient(
                        0f to palette.vibrant.copy(alpha = 0.12f),
                        0.45f to palette.deep.copy(alpha = 0.06f),
                        1f to Color.Transparent,
                    ),
                ),
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = AlbumDetailBottomPadding),
        ) {
            item(key = "hero") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = EchoContentMaxWidth)
                        .padding(horizontal = 20.dp),
                ) {
                    AlbumDetailTopBar(onBack = onBack)
                    Spacer(Modifier.height(8.dp))
                    ArtistProfileHeader(artist, palette)
                    Spacer(Modifier.height(20.dp))
                    ArtistPlaybackActions(artist.trackCount > 0, onPlayAll, onShuffle)
                    Spacer(Modifier.height(24.dp))
                    if (albums != null) ArtistAlbums(albums, palette, albumListState, onOpenAlbum)
                    Spacer(Modifier.height(22.dp))
                    ArtistTracksHeading(count = artist.trackCount)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = query, onValueChange = onQueryChange,
                        label = { Text(stringResource(L10nR.string.artist_search_tracks)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(L10nR.string.artist_clear_search))
                            }
                        },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(LibraryTrackSortMode.Album, LibraryTrackSortMode.Title, LibraryTrackSortMode.Duration).forEach { mode ->
                            FilterChip(selected = sort == mode, onClick = { onSortChange(mode) },
                                label = { Text(stringResource(when (mode) {
                                    LibraryTrackSortMode.Title -> L10nR.string.artist_sort_title
                                    LibraryTrackSortMode.Duration -> L10nR.string.artist_sort_duration
                                    else -> L10nR.string.artist_sort_album
                                })) })
                        }
                    }
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
                                .widthIn(max = EchoContentMaxWidth)
                                .padding(horizontal = 20.dp),
                        ) {
                            AlbumTrackRow(
                                index = index,
                                track = track,
                                accent = palette.vibrant,
                                onClick = { onPlayTrack(track) },
                                onUpdateTrackMetadata = onUpdateTrackMetadata,
                                onImportLyrics = onImportLyrics,
                                onPickArtwork = onPickArtwork,
                                onMatchNeteaseMetadata = onMatchNeteaseMetadata,
                                onAddToPlaylist = onAddToPlaylist,
                                onPlayNext = onPlayNext,
                                onEnqueue = onEnqueue,
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
    modifier: Modifier = Modifier,
) {
    val palette = rememberArtworkPalette(artist.artworkUri, seedKey = artist.artistKey)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(440.dp)
                .background(
                    Brush.verticalGradient(
                        0f to palette.vibrant.copy(alpha = 0.12f),
                        0.45f to palette.deep.copy(alpha = 0.06f),
                        1f to Color.Transparent,
                    ),
                ),
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = AlbumDetailBottomPadding),
        ) {
            item(key = "hero") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = EchoContentMaxWidth)
                        .padding(horizontal = 20.dp),
                ) {
                    AlbumDetailTopBar(onBack = onBack)
                    Spacer(Modifier.height(8.dp))
                    ArtistProfileHeader(artist, palette)
                    Spacer(Modifier.height(18.dp))
                    AlbumActionBar(onPlayAll = onPlayAll, onShuffle = onShuffle, onPlayOnPc = onPlayOnPc)
                    Spacer(Modifier.height(18.dp))
                    Spacer(Modifier.height(22.dp))
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
                ) { index, track ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = EchoContentMaxWidth)
                            .padding(horizontal = 20.dp),
                    ) {
                        AlbumTrackRow(
                            index = index,
                            track = track,
                            accent = palette.vibrant,
                            onClick = { onPlayTrack(track) },
                            onUpdateTrackMetadata = onUpdateTrackMetadata,
                            onImportLyrics = onImportLyrics,
                            onPickArtwork = onPickArtwork,
                            onMatchNeteaseMetadata = onMatchNeteaseMetadata,
                        )
                    }
                }
            }
        }
    }
}
