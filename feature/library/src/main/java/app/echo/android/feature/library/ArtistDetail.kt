package app.echo.android.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
    onUpdateTrackMetadata: (suspend (EchoTrackMetadataUpdate) -> Boolean)? = null,
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
    onOpenTrackAlbum: ((EchoTrack) -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    albumListState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
) {
    val palette = rememberArtworkPalette(app.echo.android.design.artistArtwork(artist.name, artist.artworkUri), seedKey = artist.artistKey)
    val onlineQuery = ArtistOnlineQuery(artist.name, albums?.itemSnapshotList?.items?.take(3)?.map { it.title }.orEmpty())
    val serverArtwork = rememberServerArtistArtwork(onlineQuery)
    var albumsExpanded by rememberSaveable(artist.artistKey) { mutableStateOf(false) }
    ArtistDetailPager(
        artist, onlineQuery, onBack, modifier,
        onPlayAll = onPlayAll, onShuffle = onShuffle, playbackEnabled = artist.trackCount > 0,
        sort = sort, onSortChange = onSortChange,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = AlbumDetailBottomPadding),
        ) {
            item(key = "hero") {
                ArtistHero(if (serverArtwork != null) artist.copy(artworkUri = serverArtwork) else artist)
            }
            if (albums != null) {
                artistAlbumsSection(albums, artist.albumCount, palette, albumsExpanded, { albumsExpanded = !albumsExpanded }, onOpenAlbum)
            }
            item(key = "tracks-header") {
                ArtistSectionItem {
                    Spacer(Modifier.height(if (albums != null) 24.dp else 20.dp))
                    ArtistTracksSearchHeading(count = artist.trackCount, query = query, onQueryChange = onQueryChange)
                    Spacer(Modifier.height(4.dp))
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
    onUpdateTrackMetadata: (suspend (EchoTrackMetadataUpdate) -> Boolean)? = null,
    onImportLyrics: ((EchoTrack) -> Unit)? = null,
    onPickArtwork: ((EchoTrack) -> Unit)? = null,
    onMatchNeteaseMetadata: ((EchoTrack) -> Unit)? = null,
    onOpenTrackAlbum: ((EchoTrack) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val onlineQuery = remember(artist.artistKey, artist.name, tracks) {
        ArtistOnlineQuery(artist.name, tracks.take(24).mapNotNull { it.album }.distinct().take(3))
    }
    val serverArtwork = rememberServerArtistArtwork(onlineQuery)
    ArtistDetailPager(
        artist, onlineQuery, onBack, modifier,
        onPlayAll = onPlayAll, onShuffle = onShuffle,
        playbackEnabled = tracks.isNotEmpty(), onPlayOnPc = onPlayOnPc,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = AlbumDetailBottomPadding),
        ) {
            item(key = "hero") {
                ArtistHero(if (serverArtwork != null) artist.copy(artworkUri = serverArtwork) else artist)
            }
            item(key = "sections") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentWidth(Alignment.CenterHorizontally)
                        .widthIn(max = EchoContentMaxWidth)
                        .padding(horizontal = 24.dp),
                ) {
                    Spacer(Modifier.height(20.dp))
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

