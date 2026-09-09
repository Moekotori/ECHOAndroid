package app.echo.android.feature.library

import app.echo.android.model.library.LibraryScanOptions
import app.echo.android.feature.library.R as L10nR
import androidx.compose.ui.res.stringResource

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import app.echo.android.design.echoClickable
import app.echo.android.design.echoCombinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Add
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import app.echo.android.design.ArtworkTile
import app.echo.android.design.EchoColors
import app.echo.android.design.rememberEchoContentMotion
import app.echo.android.design.EchoContentMaxWidth
import app.echo.android.design.EchoDarkGlassBorder
import app.echo.android.design.EchoGlassBorder
import app.echo.android.design.EchoGlassInk
import app.echo.android.design.EchoGlassNight
import app.echo.android.design.EchoGlassPanel
import app.echo.android.design.EchoHomeMist
import app.echo.android.design.EchoPanel
import app.echo.android.design.EmptyState
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.LocalEchoWidthSizeClass
import app.echo.android.design.PageChrome
import app.echo.android.model.connect.EchoRemoteAlbum
import app.echo.android.model.connect.EchoRemoteFolder
import app.echo.android.model.connect.EchoRemoteLibraryState
import app.echo.android.model.connect.EchoLinkLibraryQueryPolicy
import app.echo.android.model.connect.EchoRemotePlaylist
import app.echo.android.model.connect.EchoRemoteTrack
import app.echo.android.model.library.AlbumSortMode
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.ArtistSortMode
import app.echo.android.model.library.ArtistSummary
import app.echo.android.model.library.FolderSortMode
import app.echo.android.model.library.EchoPlaylist
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.EchoTrackMetadataUpdate
import app.echo.android.model.library.FolderSummary
import app.echo.android.model.library.LibraryPlaybackOrigin
import app.echo.android.model.library.LibraryScanPhase
import app.echo.android.model.library.LibraryScanProgress
import app.echo.android.model.library.LibrarySource
import app.echo.android.model.library.LibraryTrackSortMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

private val LinkedLibraryHeaderTopPadding = 10.dp
private val LinkedLibraryHeaderRowHeight = 56.dp
private val LinkedLibraryHeaderBottomSpacing = 8.dp

internal enum class LibraryViewMode(
    val icon: ImageVector,
) {
    Songs(Icons.AutoMirrored.Rounded.QueueMusic),
    Folders(Icons.Rounded.LibraryMusic),
    Albums(Icons.Rounded.LibraryMusic),
    Artists(Icons.Rounded.Person),
    Genres(Icons.Rounded.LibraryMusic),
    Cloud(Icons.Rounded.CloudQueue),
    Playlists(Icons.Rounded.LibraryMusic),
}

@Composable
internal fun LibraryViewMode.label(): String = when (this) {
    LibraryViewMode.Songs -> stringResource(L10nR.string.feature_library_songs_107b60)
    LibraryViewMode.Folders -> stringResource(L10nR.string.feature_library_folders_cc514a)
    LibraryViewMode.Albums -> stringResource(L10nR.string.feature_library_albums_e68c2b)
    LibraryViewMode.Artists -> stringResource(L10nR.string.feature_library_artists_1e19fb)
    LibraryViewMode.Genres -> stringResource(L10nR.string.feature_library_genres_8c2a11)
    LibraryViewMode.Cloud -> stringResource(L10nR.string.feature_library_cloud_466e60)
    LibraryViewMode.Playlists -> stringResource(L10nR.string.feature_library_playlists_56bf76)
}

private enum class LinkedLibraryMode(
    val icon: ImageVector,
) {
    Songs(Icons.AutoMirrored.Rounded.QueueMusic),
    Albums(Icons.Rounded.LibraryMusic),
    Artists(Icons.Rounded.Person),
    Playlists(Icons.Rounded.LibraryMusic),
    Folders(Icons.Rounded.FolderOpen),
}

@Composable
private fun LinkedLibraryMode.label(): String = when (this) {
    LinkedLibraryMode.Songs -> stringResource(L10nR.string.feature_library_songs_107b60)
    LinkedLibraryMode.Albums -> stringResource(L10nR.string.feature_library_albums_e68c2b)
    LinkedLibraryMode.Artists -> stringResource(L10nR.string.feature_library_artists_1e19fb)
    LinkedLibraryMode.Playlists -> stringResource(L10nR.string.feature_library_playlists_56bf76)
    LinkedLibraryMode.Folders -> stringResource(L10nR.string.feature_library_folders_cc514a)
}

private enum class LibrarySourceMode(
    val id: String,
    val icon: ImageVector,
) {
    Local(LibrarySourceIds.Local, Icons.Rounded.LibraryMusic),
    PcEcho(LibrarySourceIds.PcEcho, Icons.Rounded.Devices),
    Cloud(LibrarySourceIds.Cloud, Icons.Rounded.CloudQueue),
}

@Composable
private fun LibrarySourceMode.label(): String = when (this) {
    LibrarySourceMode.Local -> stringResource(L10nR.string.feature_library_local_9b5178)
    LibrarySourceMode.PcEcho -> stringResource(L10nR.string.feature_library_pc_echo_e0a2d4)
    LibrarySourceMode.Cloud -> stringResource(L10nR.string.feature_library_cloud_466e60)
}

@Composable
internal fun LibraryTrackSortMode.label(): String = when (this) {
    LibraryTrackSortMode.Title -> stringResource(L10nR.string.feature_library_song_title_fedd5e)
    LibraryTrackSortMode.Duration -> stringResource(L10nR.string.feature_library_duration_e1c56c)
    LibraryTrackSortMode.FrequentlyPlayed -> stringResource(L10nR.string.feature_library_frequently_played_9d6932)
    LibraryTrackSortMode.RecentlyPlayed -> stringResource(L10nR.string.feature_library_recently_played_ce4d5f)
    LibraryTrackSortMode.Random -> stringResource(L10nR.string.feature_library_shuffle_f8e15b)
    LibraryTrackSortMode.Artist -> stringResource(L10nR.string.feature_library_artist_c15fae)
    LibraryTrackSortMode.Album -> stringResource(L10nR.string.feature_library_album_724bef)
    LibraryTrackSortMode.RecentlyUpdated -> stringResource(L10nR.string.feature_library_recently_updated_781ab0)
}

@Composable
internal fun AlbumSortMode.label(): String = when (this) {
    AlbumSortMode.Title -> stringResource(L10nR.string.feature_library_title_af1111)
    AlbumSortMode.Artist -> stringResource(L10nR.string.feature_library_artist_b6e7ad)
    AlbumSortMode.Year -> stringResource(L10nR.string.feature_library_year_fe373f)
    AlbumSortMode.TrackCount -> stringResource(L10nR.string.feature_library_tracks_2d80e8)
    AlbumSortMode.Duration -> stringResource(L10nR.string.feature_library_duration_573e08)
    AlbumSortMode.RecentlyAdded -> stringResource(L10nR.string.feature_library_recently_added_b4e8c1)
}

@Composable
internal fun ArtistSortMode.label(): String = when (this) {
    ArtistSortMode.Name -> stringResource(L10nR.string.feature_library_name_57335e)
    ArtistSortMode.AlbumCount -> stringResource(L10nR.string.feature_library_albums_e68c2b)
    ArtistSortMode.TrackCount -> stringResource(L10nR.string.feature_library_tracks_2d80e8)
    ArtistSortMode.Duration -> stringResource(L10nR.string.feature_library_duration_573e08)
}

@Composable
internal fun FolderSortMode.label(): String = when (this) {
    FolderSortMode.Path -> stringResource(L10nR.string.feature_library_name_57335e)
    FolderSortMode.TrackCount -> stringResource(L10nR.string.feature_library_tracks_2d80e8)
    FolderSortMode.AlbumCount -> stringResource(L10nR.string.feature_library_albums_e68c2b)
    FolderSortMode.Duration -> stringResource(L10nR.string.feature_library_duration_573e08)
    FolderSortMode.Size -> stringResource(L10nR.string.feature_library_size_f63a75)
    FolderSortMode.RecentlyModified -> stringResource(L10nR.string.feature_library_recently_updated_781ab0)
}

@Composable
internal fun unknownArtistLabel(): String =
    stringResource(L10nR.string.feature_library_unknown_artist_9acb98)

@Composable
internal fun unknownAlbumLabel(): String =
    stringResource(L10nR.string.feature_library_unknown_album_8831c3)

@Composable
internal fun unknownTrackLabel(): String =
    stringResource(L10nR.string.feature_library_unknown_track_6cae0d)

@Composable
internal fun libraryTrackCountLabel(count: Int): String =
    stringResource(L10nR.string.feature_library_count_tracks_73b21a, (count).toString())

@Composable
internal fun libraryAlbumCountLabel(count: Int): String =
    stringResource(L10nR.string.feature_library_count_albums_554048, (count).toString())

@Composable
internal fun libraryMinutesLabel(minutes: Int): String =
    stringResource(L10nR.string.feature_library_minutes_min_777a53, (minutes).toString())

private object LibrarySourceIds {
    const val Local = "local"
    const val PcEcho = "pc_echo"
    const val Cloud = "cloud"
}

private sealed interface LibraryDetailTransitionTarget {
    object Browser : LibraryDetailTransitionTarget

    data class AlbumDetail(
        val album: AlbumSummary,
        val tracks: LazyPagingItems<EchoTrack>,
    ) : LibraryDetailTransitionTarget

    data class ArtistDetail(
        val artist: ArtistSummary,
        val tracks: LazyPagingItems<EchoTrack>,
    ) : LibraryDetailTransitionTarget

    data class FolderDetail(
        val folder: FolderSummary,
        val tracks: LazyPagingItems<EchoTrack>,
    ) : LibraryDetailTransitionTarget

    data class PlaylistDetail(
        val playlist: EchoPlaylist,
        val tracks: LazyPagingItems<EchoTrack>,
    ) : LibraryDetailTransitionTarget
}

/**
 * 互联曲库的详情过渡目标。数据随目标一起捕获,
 * 保证 AnimatedContent 退场的旧页面渲染的仍是自己的内容。
 */
private sealed interface LinkedLibraryDetailTarget {
    object Browser : LinkedLibraryDetailTarget

    data class Album(
        val album: AlbumSummary,
        val tracks: List<EchoRemoteTrack>,
        val isLoading: Boolean = false,
    ) : LinkedLibraryDetailTarget

    data class Artist(
        val artist: ArtistSummary,
        val tracks: List<EchoRemoteTrack>,
    ) : LinkedLibraryDetailTarget

    data class Playlist(
        val playlist: EchoRemotePlaylist,
        val tracks: List<EchoRemoteTrack>,
        val isLoading: Boolean,
        val error: String?,
    ) : LinkedLibraryDetailTarget
}

@Composable
fun LibraryScreen(
    hasPermission: Boolean,
    scanState: LibraryScanProgress,
    libraryQuery: String,
    trackSortMode: LibraryTrackSortMode,
    albumSortMode: AlbumSortMode,
    artistSortMode: ArtistSortMode,
    folderSortMode: FolderSortMode,
    tracks: Flow<PagingData<EchoTrack>>,
    albums: Flow<PagingData<AlbumSummary>>,
    remoteAlbums: Flow<PagingData<AlbumSummary>>,
    linkedLibraryActive: Boolean,
    linkedLibraryAvailable: Boolean,
    linkedLibraryState: StateFlow<EchoRemoteLibraryState>,
    selectedLibrarySourceId: String,
    artists: Flow<PagingData<ArtistSummary>>,
    genres: Flow<PagingData<app.echo.android.model.library.GenreSummary>> = kotlinx.coroutines.flow.emptyFlow(),
    folders: Flow<PagingData<FolderSummary>>,
    playlists: List<EchoPlaylist>,
    showTrackAudioInfoTags: Boolean,
    selectedAlbum: AlbumSummary?,
    selectedArtist: ArtistSummary?,
    selectedGenre: app.echo.android.model.library.GenreSummary? = null,
    selectedFolder: FolderSummary?,
    selectedPlaylist: EchoPlaylist?,
    albumDetailTracks: LazyPagingItems<EchoTrack>?,
    artistDetailTracks: LazyPagingItems<EchoTrack>?,
    genreDetailTracks: LazyPagingItems<EchoTrack>? = null,
    folderDetailTracks: LazyPagingItems<EchoTrack>?,
    playlistDetailTracks: LazyPagingItems<EchoTrack>?,
    onRequestPermission: () -> Unit,
    onLibraryQueryChange: (String) -> Unit,
    onLibrarySourceChange: (String) -> Unit,
    onTrackSortModeChange: (LibraryTrackSortMode) -> Unit,
    onAlbumSortModeChange: (AlbumSortMode) -> Unit,
    onArtistSortModeChange: (ArtistSortMode) -> Unit,
    onFolderSortModeChange: (FolderSortMode) -> Unit,
    onScanFolder: (LibraryScanOptions) -> Unit,
    onScanAll: (LibraryScanOptions) -> Unit,
    onCancelScan: () -> Unit,
    onRefreshLinkedLibrary: (String) -> Unit,
    onOpenLinkedPlaylist: (EchoRemotePlaylist) -> Unit,
    onOpenLinkedAlbum: (EchoRemoteAlbum) -> Unit = {},
    onRefreshLinkedFolders: (String) -> Unit = {},
    onPlayLinkedTrack: (EchoRemoteTrack) -> Unit,
    onPlayLinkedQueue: (List<EchoRemoteTrack>, Int) -> Unit,
    onPlayLinkedQueueOnPc: (List<EchoRemoteTrack>, Int) -> Unit = { _, _ -> },
    onPlayTrack: (EchoTrack, LibraryPlaybackOrigin) -> Unit,
    onPlayNext: (EchoTrack) -> Unit = {},
    onEnqueueTrack: (EchoTrack) -> Unit = {},
    onUpdateTrackMetadata: (EchoTrackMetadataUpdate) -> Unit,
    onImportLyricsForTrack: (EchoTrack) -> Unit,
    onPickTrackArtwork: (EchoTrack) -> Unit,
    onPlayAlbum: (AlbumSummary) -> Unit,
    onShuffleAlbum: (AlbumSummary) -> Unit,
    onPlayArtist: (ArtistSummary) -> Unit,
    onShuffleArtist: (ArtistSummary) -> Unit,
    onOpenGenre: (app.echo.android.model.library.GenreSummary) -> Unit = {},
    onPlayGenre: (app.echo.android.model.library.GenreSummary) -> Unit = {},
    onPlayFolder: (FolderSummary) -> Unit,
    onShuffleFolder: (FolderSummary) -> Unit,
    onPlayPlaylist: (EchoPlaylist) -> Unit,
    onShufflePlaylist: (EchoPlaylist) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onRenamePlaylist: (EchoPlaylist, String) -> Unit,
    onDeletePlaylist: (EchoPlaylist) -> Unit,
    onAddTrackToPlaylist: (EchoPlaylist, EchoTrack) -> Unit,
    onCreatePlaylistAndAddTrack: (String, EchoTrack) -> Unit,
    onRemoveTrackFromPlaylist: (EchoPlaylist, EchoTrack) -> Unit,
    onReorderPlaylistTracks: (EchoPlaylist, Int, Int) -> Unit,
    onOpenAlbum: (AlbumSummary) -> Unit,
    onOpenArtist: (ArtistSummary) -> Unit,
    onOpenFolder: (FolderSummary) -> Unit,
    onOpenPlaylist: (EchoPlaylist) -> Unit,
    onCloseDetail: () -> Unit,
    onImportM3uPlaylist: () -> Unit = {},
    onExportM3uPlaylist: (EchoPlaylist) -> Unit = {},
) {
    val playNext = onPlayNext
    val enqueueTrack = onEnqueueTrack
    var selectedModeIndex by rememberSaveable { mutableIntStateOf(LibraryViewMode.Songs.ordinal) }
    val selectedMode = LibraryViewMode.entries[selectedModeIndex]
    var selectedSource by remember(selectedLibrarySourceId, linkedLibraryActive) {
        mutableStateOf(librarySourceModeFromId(selectedLibrarySourceId, linkedLibraryActive))
    }
    var linkedMode by rememberSaveable { mutableStateOf(LinkedLibraryMode.Songs) }
    var selectedLinkedAlbumKey by remember { mutableStateOf<String?>(null) }
    var selectedLinkedArtistKey by remember { mutableStateOf<String?>(null) }
    var selectedLinkedPlaylistId by remember { mutableStateOf<String?>(null) }
    val songListState = rememberLazyListState()
    var addToPlaylistTrack by remember { mutableStateOf<EchoTrack?>(null) }
    var scanWasActiveForBanner by remember { mutableStateOf(false) }
    var showScanResultBanner by remember { mutableStateOf(false) }

    LaunchedEffect(
        scanState.phase,
        scanState.scannedCount,
        scanState.insertedCount,
        scanState.updatedCount,
        scanState.deletedCount,
        scanState.error,
    ) {
        if (scanState.isScanning) {
            scanWasActiveForBanner = true
            showScanResultBanner = false
        } else if (scanWasActiveForBanner && scanState.hasResultBannerMessage()) {
            showScanResultBanner = true
            scanWasActiveForBanner = false
        }
    }

    LaunchedEffect(showScanResultBanner, scanState.phase) {
        if (showScanResultBanner && scanState.phase != LibraryScanPhase.Error) {
            delay(3_200L)
            showScanResultBanner = false
        }
    }

    LaunchedEffect(selectedLibrarySourceId, linkedLibraryActive) {
        val persistedSource = librarySourceModeFromId(selectedLibrarySourceId, linkedLibraryActive)
        if (persistedSource != selectedSource) {
            selectedSource = persistedSource
            selectedLinkedAlbumKey = null
            selectedLinkedArtistKey = null
            selectedLinkedPlaylistId = null
        }
    }

    fun selectSource(source: LibrarySourceMode) {
        if (selectedSource == source) return
        selectedSource = source
        onLibrarySourceChange(source.id)
        selectedLinkedAlbumKey = null
        selectedLinkedArtistKey = null
        selectedLinkedPlaylistId = null
        when (source) {
            LibrarySourceMode.Local -> {
                if (selectedMode == LibraryViewMode.Cloud) {
                    selectedModeIndex = LibraryViewMode.Songs.ordinal
                }
            }
            LibrarySourceMode.Cloud -> selectedModeIndex = LibraryViewMode.Albums.ordinal
            LibrarySourceMode.PcEcho -> if (linkedLibraryAvailable) onRefreshLinkedLibrary(libraryQuery)
        }
    }

    if (selectedSource == LibrarySourceMode.PcEcho && linkedLibraryAvailable) {
        val linkedState by linkedLibraryState.collectAsState()
        LinkedEchoLibraryPage(
            state = linkedState,
            query = libraryQuery,
            selectedMode = linkedMode,
            selectedAlbumKey = selectedLinkedAlbumKey,
            selectedSource = selectedSource,
            selectedSortMode = trackSortMode,
            albumSortMode = albumSortMode,
            artistSortMode = artistSortMode,
            folderSortMode = folderSortMode,
            showTrackAudioInfoTags = showTrackAudioInfoTags,
            selectedPlaylistId = selectedLinkedPlaylistId,
            onQueryChange = onLibraryQueryChange,
            onSelectSource = ::selectSource,
            onSortModeChange = onTrackSortModeChange,
            onAlbumSortModeChange = onAlbumSortModeChange,
            onArtistSortModeChange = onArtistSortModeChange,
            onFolderSortModeChange = onFolderSortModeChange,
            onSelectMode = { mode ->
                linkedMode = mode
                selectedLinkedAlbumKey = null
                selectedLinkedArtistKey = null
                selectedLinkedPlaylistId = null
                if (mode == LinkedLibraryMode.Folders) onRefreshLinkedFolders(linkedState.folderPath)
            },
            onOpenAlbum = { album ->
                selectedLinkedAlbumKey = album.albumKey
                linkedState.albums.firstOrNull {
                    EchoLinkLibraryQueryPolicy.remoteAlbumKey(it.id) == album.albumKey
                }?.let(onOpenLinkedAlbum)
            },
            selectedArtistKey = selectedLinkedArtistKey,
            onOpenArtist = { artist -> selectedLinkedArtistKey = artist.artistKey },
            onOpenPlaylist = { playlist ->
                selectedLinkedPlaylistId = playlist.id
                onOpenLinkedPlaylist(playlist)
            },
            onCloseAlbum = { selectedLinkedAlbumKey = null },
            onCloseArtist = { selectedLinkedArtistKey = null },
            onClosePlaylist = { selectedLinkedPlaylistId = null },
            onRefresh = onRefreshLinkedLibrary,
            onRefreshFolders = onRefreshLinkedFolders,
            onPlayLinkedTrack = onPlayLinkedTrack,
            onPlayLinkedQueue = onPlayLinkedQueue,
            onPlayLinkedQueueOnPc = onPlayLinkedQueueOnPc,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    if (selectedSource == LibrarySourceMode.PcEcho) {
        LibraryBrowserFrame(
            query = libraryQuery, onQueryChange = onLibraryQueryChange,
            sources = { LibrarySourceStrip(selectedSource, linkedLibraryAvailable, ::selectSource) },
        ) {
            LibraryCollectionEmpty(
                title = stringResource(L10nR.string.feature_library_not_connected_c4d337),
                detail = stringResource(L10nR.string.feature_library_connect_pc_echo_in_link_first_then_you_31fc3e),
            )
        }
        return
    }

    val prefersSplit = LocalEchoWidthSizeClass.current.prefersLibrarySplit

    @Composable
    fun LocalBrowserPane() {
        var showEmptyScanOptions by remember { mutableStateOf(false) }
        if (showEmptyScanOptions) LibraryScanOptionsDialog(
            onDismiss = { showEmptyScanOptions = false },
            onScanFolder = { options -> showEmptyScanOptions = false; onScanFolder(options) },
            onScanAll = { options -> showEmptyScanOptions = false; onScanAll(options) },
        )
        LibraryBrowserFrame(
            query = libraryQuery,
            onQueryChange = onLibraryQueryChange,
            sources = { LibrarySourceStrip(selectedSource, linkedLibraryAvailable, ::selectSource) },
            actions = {
                if (selectedSource == LibrarySourceMode.Local) LibrarySourceScanButton(
                    selectedSource, linkedLibraryAvailable, ::selectSource,
                    hasPermission, scanState, onRequestPermission, onScanFolder, onScanAll, onCancelScan,
                )
            },
        ) {
            when {
                else -> {
                    if (scanState.isScanning) LibraryScanStatus(scanState, onCancelScan)
                    LibraryBrowserHeader(
                        scanState = scanState,
                        showScanResultBanner = showScanResultBanner,
                        selectedSource = selectedSource,
                        linkedLibraryAvailable = linkedLibraryAvailable,
                        onSelectSource = ::selectSource,
                        selectedMode = selectedMode,
                        selectedSortMode = trackSortMode,
                        albumSortMode = albumSortMode,
                        artistSortMode = artistSortMode,
                        folderSortMode = folderSortMode,
                        onSelectMode = { mode ->
                            selectedModeIndex = mode.ordinal
                            if (selectedSource == LibrarySourceMode.Cloud && mode != LibraryViewMode.Albums) {
                                selectedSource = LibrarySourceMode.Local
                                onLibrarySourceChange(LibrarySourceMode.Local.id)
                            }
                        },
                        onSortModeChange = onTrackSortModeChange,
                        onAlbumSortModeChange = onAlbumSortModeChange,
                        onArtistSortModeChange = onArtistSortModeChange,
                        onFolderSortModeChange = onFolderSortModeChange,
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        val libraryTabMotion = rememberEchoContentMotion()
                        AnimatedContent(
                            targetState = selectedMode,
                            transitionSpec = {
                                libraryTabMotion.tabSwitch(targetState.ordinal > initialState.ordinal)
                            },
                            label = "library-mode-transition",
                            modifier = Modifier.fillMaxSize(),
                        ) { mode ->
                        when (mode) {
                            LibraryViewMode.Songs -> {
                                val trackItems = tracks.collectAsLazyPagingItems()
                                val showInitialTrackLoading =
                                    trackItems.itemCount == 0 &&
                                        trackItems.loadState.refresh is LoadState.Loading
                                val showInitialTrackError =
                                    trackItems.itemCount == 0 &&
                                        trackItems.loadState.refresh is LoadState.Error
                                when {
                                    showInitialTrackLoading -> LibraryCollectionEmpty(
                                        stringResource(L10nR.string.feature_library_loading_library_a77123),
                                    )
                                    showInitialTrackError -> LibraryCollectionEmpty(
                                        title = stringResource(L10nR.string.feature_library_library_query_failed_f9c538),
                                        actionLabel = stringResource(L10nR.string.library_retry), onAction = { trackItems.retry() },
                                    )
                                    trackItems.itemCount == 0 -> LibraryCollectionEmpty(
                                        title = stringResource(if (libraryQuery.isNotBlank()) L10nR.string.library_no_matches else L10nR.string.library_start_collection),
                                        detail = stringResource(if (libraryQuery.isNotBlank()) L10nR.string.library_search_hint else L10nR.string.library_import_hint),
                                        actionLabel = stringResource(if (libraryQuery.isNotBlank()) L10nR.string.library_clear_search else L10nR.string.library_add_music),
                                        onAction = if (libraryQuery.isNotBlank()) ({ onLibraryQueryChange("") }) else ({ showEmptyScanOptions = true }),
                                    )
                                    else -> TrackList(
                                        tracks = trackItems,
                                        onPlayTrack = { track ->
                                            onPlayTrack(track, LibraryPlaybackOrigin.Songs)
                                        },
                                        onUpdateTrackMetadata = onUpdateTrackMetadata,
                                        onImportLyrics = onImportLyricsForTrack,
                                        onPickArtwork = onPickTrackArtwork,
                                        onAddToPlaylist = { track -> addToPlaylistTrack = track },
                                        onPlayNext = playNext,
                                        onEnqueue = enqueueTrack,
                                        showAudioInfoTags = showTrackAudioInfoTags,
                                        listState = songListState,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }

                            LibraryViewMode.Folders -> FolderList(
                                folders = folders.collectAsLazyPagingItems(),
                                onOpenFolder = onOpenFolder,
                                modifier = Modifier.fillMaxSize(),
                            )

                            LibraryViewMode.Albums -> {
                                val albumItems = if (selectedSource == LibrarySourceMode.Cloud) {
                                    remoteAlbums.collectAsLazyPagingItems()
                                } else {
                                    albums.collectAsLazyPagingItems()
                                }
                                AlbumWall(
                                    albums = albumItems,
                                    onOpenAlbum = onOpenAlbum,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            LibraryViewMode.Artists -> ArtistWall(
                                artists = artists.collectAsLazyPagingItems(),
                                onOpenArtist = onOpenArtist,
                                modifier = Modifier.fillMaxSize(),
                            )

                            LibraryViewMode.Genres -> GenreWall(
                                genres = genres.collectAsLazyPagingItems(),
                                onOpenGenre = onOpenGenre,
                                modifier = Modifier.fillMaxSize(),
                            )

                            LibraryViewMode.Cloud -> AlbumWall(
                                albums = remoteAlbums.collectAsLazyPagingItems(),
                                onOpenAlbum = onOpenAlbum,
                                modifier = Modifier.fillMaxSize(),
                            )

                            LibraryViewMode.Playlists -> LocalPlaylistPanel(
                                playlists = playlists,
                                onOpenPlaylist = onOpenPlaylist,
                                onPlayPlaylist = onPlayPlaylist,
                                onCreatePlaylist = onCreatePlaylist,
                                onRenamePlaylist = onRenamePlaylist,
                                onDeletePlaylist = onDeletePlaylist,
                                onImportM3uPlaylist = onImportM3uPlaylist,
                                onExportM3uPlaylist = onExportM3uPlaylist,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        }
                    }
                }
            }
        }
    }

    if (prefersSplit) {
        val livePlaylist = selectedPlaylist?.let { selected ->
            playlists.firstOrNull { it.id == selected.id } ?: selected
        }
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(0.42f).fillMaxHeight()) {
                LocalBrowserPane()
            }
            Box(
                Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)),
            )
            Box(Modifier.weight(0.58f).fillMaxHeight()) {
                when {
                    selectedAlbum != null && albumDetailTracks != null -> AlbumDetailPage(
                        album = selectedAlbum,
                        tracks = albumDetailTracks,
                        onBack = onCloseDetail,
                        onPlayAll = { onPlayAlbum(selectedAlbum) },
                        onShuffle = { onShuffleAlbum(selectedAlbum) },
                        onPlayTrack = { track ->
                            onPlayTrack(track, LibraryPlaybackOrigin.Album(selectedAlbum.albumKey))
                        },
                        onUpdateTrackMetadata = onUpdateTrackMetadata,
                        onImportLyrics = onImportLyricsForTrack,
                        onPickArtwork = onPickTrackArtwork,
                        onAddToPlaylist = { track -> addToPlaylistTrack = track },
                        onPlayNext = playNext,
                        onEnqueue = enqueueTrack,
                        modifier = Modifier.fillMaxSize(),
                    )
                    selectedGenre != null && genreDetailTracks != null -> ArtistDetailPage(
                        artist = ArtistSummary(
                            artistKey = selectedGenre.genreKey,
                            name = selectedGenre.name,
                            artworkUri = selectedGenre.artworkUri,
                            albumCount = selectedGenre.albumCount,
                            trackCount = selectedGenre.trackCount,
                            durationMs = selectedGenre.durationMs,
                        ),
                        tracks = genreDetailTracks,
                        onBack = onCloseDetail,
                        onPlayAll = { onPlayGenre(selectedGenre) },
                        onShuffle = { onPlayGenre(selectedGenre) },
                        onPlayTrack = { track ->
                            onPlayTrack(track, LibraryPlaybackOrigin.Songs)
                        },
                        onUpdateTrackMetadata = onUpdateTrackMetadata,
                        onImportLyrics = onImportLyricsForTrack,
                        onPickArtwork = onPickTrackArtwork,
                        onAddToPlaylist = { track -> addToPlaylistTrack = track },
                        onPlayNext = playNext,
                        onEnqueue = enqueueTrack,
                    )
                    selectedArtist != null && artistDetailTracks != null -> ArtistDetailPage(
                        artist = selectedArtist,
                        tracks = artistDetailTracks,
                        onBack = onCloseDetail,
                        onPlayAll = { onPlayArtist(selectedArtist) },
                        onShuffle = { onShuffleArtist(selectedArtist) },
                        onPlayTrack = { track ->
                            onPlayTrack(track, LibraryPlaybackOrigin.Artist(selectedArtist.artistKey))
                        },
                        onUpdateTrackMetadata = onUpdateTrackMetadata,
                        onImportLyrics = onImportLyricsForTrack,
                        onPickArtwork = onPickTrackArtwork,
                        onAddToPlaylist = { track -> addToPlaylistTrack = track },
                        onPlayNext = playNext,
                        onEnqueue = enqueueTrack,
                        modifier = Modifier.fillMaxSize(),
                    )
                    selectedFolder != null && folderDetailTracks != null -> FolderDetailPage(
                        folder = selectedFolder,
                        tracks = folderDetailTracks,
                        onBack = onCloseDetail,
                        onPlayAll = { onPlayFolder(selectedFolder) },
                        onShuffle = { onShuffleFolder(selectedFolder) },
                        onPlayTrack = { track ->
                            onPlayTrack(track, LibraryPlaybackOrigin.Folder(selectedFolder.folderKey))
                        },
                        onUpdateTrackMetadata = onUpdateTrackMetadata,
                        onImportLyrics = onImportLyricsForTrack,
                        onPickArtwork = onPickTrackArtwork,
                        onAddToPlaylist = { track -> addToPlaylistTrack = track },
                        onPlayNext = playNext,
                        onEnqueue = enqueueTrack,
                        modifier = Modifier.fillMaxSize(),
                    )
                    livePlaylist != null && playlistDetailTracks != null -> PlaylistDetailPage(
                        playlist = livePlaylist,
                        tracks = playlistDetailTracks,
                        onBack = onCloseDetail,
                        onPlayAll = { onPlayPlaylist(livePlaylist) },
                        onShuffle = { onShufflePlaylist(livePlaylist) },
                        onPlayTrack = { track ->
                            onPlayTrack(track, LibraryPlaybackOrigin.Playlist(livePlaylist.id))
                        },
                        onRenamePlaylist = { name -> onRenamePlaylist(livePlaylist, name) },
                        onDeletePlaylist = {
                            onDeletePlaylist(livePlaylist)
                            onCloseDetail()
                        },
                        onRemoveTrack = { track -> onRemoveTrackFromPlaylist(livePlaylist, track) },
                        onMoveTrack = { from, to -> onReorderPlaylistTracks(livePlaylist, from, to) },
                        onUpdateTrackMetadata = onUpdateTrackMetadata,
                        onImportLyrics = onImportLyricsForTrack,
                        onPickArtwork = onPickTrackArtwork,
                        onAddToPlaylist = { track -> addToPlaylistTrack = track },
                        onPlayNext = playNext,
                        onEnqueue = enqueueTrack,
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> LibrarySplitPlaceholder()
                }
            }
        }
    } else {

    // 详情页走全屏沉浸式页面，不套用曲库的 PageChrome
    val activeAlbumDetail = selectedAlbum
    val activeArtistDetail = selectedArtist
    val activeFolderDetail = selectedFolder
    val activePlaylistDetail = selectedPlaylist
    val detailTransitionTarget = when {
        activeAlbumDetail != null && albumDetailTracks != null ->
            LibraryDetailTransitionTarget.AlbumDetail(activeAlbumDetail, albumDetailTracks)
        activeArtistDetail != null && artistDetailTracks != null ->
            LibraryDetailTransitionTarget.ArtistDetail(activeArtistDetail, artistDetailTracks)
        activeFolderDetail != null && folderDetailTracks != null ->
            LibraryDetailTransitionTarget.FolderDetail(activeFolderDetail, folderDetailTracks)
        activePlaylistDetail != null && playlistDetailTracks != null -> {
            val livePlaylist = playlists.firstOrNull { it.id == activePlaylistDetail.id }
                ?: activePlaylistDetail
            LibraryDetailTransitionTarget.PlaylistDetail(livePlaylist, playlistDetailTracks)
        }
        else -> LibraryDetailTransitionTarget.Browser
    }

    val libraryDetailMotion = rememberEchoContentMotion()

    AnimatedContent(
        targetState = detailTransitionTarget,
        contentKey = { target ->
            when (target) {
                LibraryDetailTransitionTarget.Browser -> "library-browser"
                is LibraryDetailTransitionTarget.AlbumDetail -> "album:${target.album.albumKey}"
                is LibraryDetailTransitionTarget.ArtistDetail -> "artist:${target.artist.artistKey}"
                is LibraryDetailTransitionTarget.FolderDetail -> "folder:${target.folder.folderKey}"
                is LibraryDetailTransitionTarget.PlaylistDetail -> "playlist:${target.playlist.id}"
            }
        },
        transitionSpec = {
            if (targetState != LibraryDetailTransitionTarget.Browser) {
                libraryDetailMotion.pagePush()
            } else {
                libraryDetailMotion.pagePop()
            }
        },
        label = "library-detail-transition",
        modifier = Modifier.fillMaxSize(),
    ) { target ->
        when (target) {
            is LibraryDetailTransitionTarget.AlbumDetail -> AlbumDetailPage(
                album = target.album,
                tracks = target.tracks,
                onBack = onCloseDetail,
                onPlayAll = { onPlayAlbum(target.album) },
                onShuffle = { onShuffleAlbum(target.album) },
                onPlayTrack = { track ->
                    onPlayTrack(track, LibraryPlaybackOrigin.Album(target.album.albumKey))
                },
                onUpdateTrackMetadata = onUpdateTrackMetadata,
                onImportLyrics = onImportLyricsForTrack,
                onPickArtwork = onPickTrackArtwork,
                onAddToPlaylist = { track -> addToPlaylistTrack = track },
                onPlayNext = playNext,
                onEnqueue = enqueueTrack,
                modifier = Modifier.fillMaxSize(),
            )
            is LibraryDetailTransitionTarget.ArtistDetail -> ArtistDetailPage(
                artist = target.artist,
                tracks = target.tracks,
                onBack = onCloseDetail,
                onPlayAll = { onPlayArtist(target.artist) },
                onShuffle = { onShuffleArtist(target.artist) },
                onPlayTrack = { track ->
                    onPlayTrack(track, LibraryPlaybackOrigin.Artist(target.artist.artistKey))
                },
                onUpdateTrackMetadata = onUpdateTrackMetadata,
                onImportLyrics = onImportLyricsForTrack,
                onPickArtwork = onPickTrackArtwork,
                onAddToPlaylist = { track -> addToPlaylistTrack = track },
                onPlayNext = playNext,
                onEnqueue = enqueueTrack,
                modifier = Modifier.fillMaxSize(),
            )
            is LibraryDetailTransitionTarget.FolderDetail -> FolderDetailPage(
                folder = target.folder,
                tracks = target.tracks,
                onBack = onCloseDetail,
                onPlayAll = { onPlayFolder(target.folder) },
                onShuffle = { onShuffleFolder(target.folder) },
                onPlayTrack = { track ->
                    onPlayTrack(track, LibraryPlaybackOrigin.Folder(target.folder.folderKey))
                },
                onUpdateTrackMetadata = onUpdateTrackMetadata,
                onImportLyrics = onImportLyricsForTrack,
                onPickArtwork = onPickTrackArtwork,
                onAddToPlaylist = { track -> addToPlaylistTrack = track },
                onPlayNext = playNext,
                onEnqueue = enqueueTrack,
                modifier = Modifier.fillMaxSize(),
            )
            is LibraryDetailTransitionTarget.PlaylistDetail -> PlaylistDetailPage(
                playlist = target.playlist,
                tracks = target.tracks,
                onBack = onCloseDetail,
                onPlayAll = { onPlayPlaylist(target.playlist) },
                onShuffle = { onShufflePlaylist(target.playlist) },
                onPlayTrack = { track ->
                    onPlayTrack(track, LibraryPlaybackOrigin.Playlist(target.playlist.id))
                },
                onRenamePlaylist = { name -> onRenamePlaylist(target.playlist, name) },
                onDeletePlaylist = {
                    onDeletePlaylist(target.playlist)
                    onCloseDetail()
                },
                onRemoveTrack = { track -> onRemoveTrackFromPlaylist(target.playlist, track) },
                onMoveTrack = { from, to -> onReorderPlaylistTracks(target.playlist, from, to) },
                onUpdateTrackMetadata = onUpdateTrackMetadata,
                onImportLyrics = onImportLyricsForTrack,
                onPickArtwork = onPickTrackArtwork,
                onAddToPlaylist = { track -> addToPlaylistTrack = track },
                onPlayNext = playNext,
                onEnqueue = enqueueTrack,
                modifier = Modifier.fillMaxSize(),
            )
            LibraryDetailTransitionTarget.Browser -> LocalBrowserPane()
        }
    }
    }

    addToPlaylistTrack?.let { track ->
        AddToPlaylistDialog(
            playlists = playlists,
            onDismiss = { addToPlaylistTrack = null },
            onSelectPlaylist = { playlist ->
                onAddTrackToPlaylist(playlist, track)
                addToPlaylistTrack = null
            },
            onCreatePlaylist = { name ->
                onCreatePlaylistAndAddTrack(name, track)
                addToPlaylistTrack = null
            },
        )
    }
}

@Composable
private fun LibrarySplitPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        LibraryCollectionEmpty(
            stringResource(L10nR.string.feature_library_pick_an_album_artist_folder_or_playlist_e517b7),
        )
    }
}


@Composable
private fun LibrarySourceScanButton(
    selectedSource: LibrarySourceMode,
    linkedLibraryAvailable: Boolean,
    onSelectSource: (LibrarySourceMode) -> Unit,
    hasPermission: Boolean,
    scanState: LibraryScanProgress,
    onRequestPermission: () -> Unit,
    onScanFolder: (LibraryScanOptions) -> Unit,
    onScanAll: (LibraryScanOptions) -> Unit,
    onCancelScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showScanOptions by remember { mutableStateOf(false) }
    androidx.compose.material3.TextButton(
        onClick = { showScanOptions = true },
        enabled = !scanState.isScanning, modifier = modifier,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(L10nR.string.library_add_music))
    }
    if (showScanOptions) LibraryScanOptionsDialog(
        onDismiss = { showScanOptions = false },
        onScanFolder = { options -> showScanOptions = false; onScanFolder(options) },
        onScanAll = { options -> showScanOptions = false; onScanAll(options) },
    )
}

private fun librarySourceModeFromId(
    sourceId: String,
    linkedLibraryActive: Boolean,
): LibrarySourceMode =
    when (sourceId) {
        LibrarySourceIds.PcEcho -> LibrarySourceMode.PcEcho
        LibrarySourceIds.Cloud -> LibrarySourceMode.Cloud
        LibrarySourceIds.Local -> LibrarySourceMode.Local
        else -> if (linkedLibraryActive) LibrarySourceMode.PcEcho else LibrarySourceMode.Local
    }

@Composable
private fun LibrarySourceStrip(
    selectedSource: LibrarySourceMode,
    linkedLibraryAvailable: Boolean,
    onSelectSource: (LibrarySourceMode) -> Unit,
) {
    LibraryTextTabs(
        labels = LibrarySourceMode.entries.map { it.label() },
        selectedIndex = selectedSource.ordinal,
        onSelect = { onSelectSource(LibrarySourceMode.entries[it]) },
        compact = true,
    )
}

@Composable
private fun LinkedEchoLibraryPage(
    state: EchoRemoteLibraryState,
    query: String,
    selectedMode: LinkedLibraryMode,
    selectedAlbumKey: String?,
    selectedArtistKey: String?,
    selectedSource: LibrarySourceMode,
    selectedSortMode: LibraryTrackSortMode,
    albumSortMode: AlbumSortMode,
    artistSortMode: ArtistSortMode,
    folderSortMode: FolderSortMode,
    showTrackAudioInfoTags: Boolean,
    selectedPlaylistId: String?,
    onQueryChange: (String) -> Unit,
    onSelectSource: (LibrarySourceMode) -> Unit,
    onSortModeChange: (LibraryTrackSortMode) -> Unit,
    onAlbumSortModeChange: (AlbumSortMode) -> Unit,
    onArtistSortModeChange: (ArtistSortMode) -> Unit,
    onFolderSortModeChange: (FolderSortMode) -> Unit,
    onSelectMode: (LinkedLibraryMode) -> Unit,
    onOpenAlbum: (AlbumSummary) -> Unit,
    onOpenArtist: (ArtistSummary) -> Unit,
    onOpenPlaylist: (EchoRemotePlaylist) -> Unit,
    onCloseAlbum: () -> Unit,
    onCloseArtist: () -> Unit,
    onClosePlaylist: () -> Unit,
    onRefresh: (String) -> Unit,
    onRefreshFolders: (String) -> Unit,
    onPlayLinkedTrack: (EchoRemoteTrack) -> Unit,
    onPlayLinkedQueue: (List<EchoRemoteTrack>, Int) -> Unit,
    onPlayLinkedQueueOnPc: (List<EchoRemoteTrack>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tracks = state.tracks
    val playlists = state.playlists
    val normalizedQuery = remember(query) { query.trim() }
    val remoteQuery = remember(state.query) { state.query.trim() }
    val includeSortedTracks = selectedMode == LinkedLibraryMode.Songs
    val includeAlbums = selectedMode == LinkedLibraryMode.Albums || selectedAlbumKey != null
    val includeArtists = selectedMode == LinkedLibraryMode.Artists || selectedArtistKey != null
    val includePlaylists = selectedMode == LinkedLibraryMode.Playlists || selectedPlaylistId != null
    val catalog by produceState(
        initialValue = LinkedLibraryCatalog.Empty,
        tracks,
        playlists,
        normalizedQuery,
        remoteQuery,
        selectedSortMode,
        albumSortMode,
        artistSortMode,
        includeSortedTracks,
        includeAlbums,
        includeArtists,
        includePlaylists,
        state.albums,
    ) {
        // 输入防抖:按键会重启本协程,连续输入只保留最后一次全量 filter/sort/拼音构建
        if (normalizedQuery.isNotEmpty()) delay(200)
        value = withContext(Dispatchers.Default) {
            LinkedLibraryCatalog.build(
                tracks = tracks,
                playlists = playlists,
                query = normalizedQuery,
                remoteQuery = remoteQuery,
                sortMode = selectedSortMode,
                albumSortMode = albumSortMode,
                artistSortMode = artistSortMode,
                includeSortedTracks = includeSortedTracks,
                includeAlbums = includeAlbums,
                includeArtists = includeArtists,
                includePlaylists = includePlaylists,
                remoteAlbums = state.albums,
            )
        }
    }
    val sortedTracks = catalog.tracks
    val albums = catalog.albums
    val artists = catalog.artists
    val filteredPlaylists = catalog.playlists
    val selectedAlbum = remember(albums, selectedAlbumKey) {
        albums.firstOrNull { it.albumKey == selectedAlbumKey }
    }
    val selectedArtist = remember(artists, selectedArtistKey) {
        artists.firstOrNull { it.artistKey == selectedArtistKey }
    }
    val selectedPlaylist = remember(playlists, selectedPlaylistId) {
        playlists.firstOrNull { it.id == selectedPlaylistId }
    }
    val remoteAlbumId = selectedAlbumKey?.let(EchoLinkLibraryQueryPolicy::remoteAlbumId)
    val selectedAlbumTracks = remember(sortedTracks, selectedAlbumKey, state.albumTracks, remoteAlbumId) {
        if (selectedAlbumKey == null) {
            emptyList()
        } else if (remoteAlbumId != null) {
            state.albumTracks[remoteAlbumId].orEmpty()
        } else {
            sortedTracks.filter { it.linkedAlbumKey() == selectedAlbumKey }
        }
    }
    val selectedArtistTracks = remember(sortedTracks, selectedArtistKey) {
        if (selectedArtistKey == null) {
            emptyList()
        } else {
            sortedTracks.filter { it.linkedArtistKey() == selectedArtistKey }
        }
    }

    LaunchedEffect(normalizedQuery, remoteQuery) {
        val queryToSend = EchoLinkLibraryQueryPolicy.remoteSearchQuery(normalizedQuery)
        if (queryToSend != remoteQuery) {
            delay(300L)
            onRefresh(queryToSend)
        }
    }
    LaunchedEffect(state.foldersUnavailable, selectedMode) {
        if (state.foldersUnavailable && selectedMode == LinkedLibraryMode.Folders) {
            onSelectMode(LinkedLibraryMode.Songs)
        }
    }
    var lastEmptyAutoRefreshQuery by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(
        normalizedQuery,
        remoteQuery,
        state.isLoading,
        state.error,
        state.tracks.size,
        state.totalCount,
    ) {
        val shouldRefreshEmptyLibrary =
            normalizedQuery == remoteQuery &&
                !state.isLoading &&
                state.error.isNullOrBlank() &&
                state.tracks.isEmpty() &&
                state.totalCount == 0 &&
                lastEmptyAutoRefreshQuery != normalizedQuery
        if (shouldRefreshEmptyLibrary) {
            lastEmptyAutoRefreshQuery = normalizedQuery
            onRefresh(normalizedQuery)
        }
    }

    val linkedDetailTarget = when {
        selectedAlbum != null -> LinkedLibraryDetailTarget.Album(
            album = selectedAlbum,
            tracks = selectedAlbumTracks,
            isLoading = remoteAlbumId != null && state.loadingAlbumId == remoteAlbumId,
        )
        selectedArtist != null -> LinkedLibraryDetailTarget.Artist(
            artist = selectedArtist,
            tracks = selectedArtistTracks,
        )
        selectedPlaylist != null -> LinkedLibraryDetailTarget.Playlist(
            playlist = selectedPlaylist,
            tracks = state.playlistTracks[selectedPlaylist.id].orEmpty(),
            isLoading = state.loadingPlaylistId == selectedPlaylist.id,
            error = state.error,
        )
        else -> LinkedLibraryDetailTarget.Browser
    }
    val linkedDetailMotion = rememberEchoContentMotion()
    AnimatedContent(
        targetState = linkedDetailTarget,
        contentKey = { target ->
            when (target) {
                LinkedLibraryDetailTarget.Browser -> "browser"
                is LinkedLibraryDetailTarget.Album -> "album:${target.album.albumKey}"
                is LinkedLibraryDetailTarget.Artist -> "artist:${target.artist.artistKey}"
                is LinkedLibraryDetailTarget.Playlist -> "playlist:${target.playlist.id}"
            }
        },
        transitionSpec = {
            if (targetState == LinkedLibraryDetailTarget.Browser) linkedDetailMotion.pagePop() else linkedDetailMotion.pagePush()
        },
        label = "linked-library-detail",
        modifier = modifier,
    ) { target ->
        if (target is LinkedLibraryDetailTarget.Album) {
            LinkedAlbumTracksPage(
                album = target.album,
                tracks = target.tracks,
                isLoading = target.isLoading,
                onBack = onCloseAlbum,
                onPlayLinkedTrack = onPlayLinkedTrack,
                onPlayLinkedQueue = onPlayLinkedQueue,
                onPlayLinkedQueueOnPc = onPlayLinkedQueueOnPc,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (target is LinkedLibraryDetailTarget.Artist) {
            LinkedArtistTracksPage(
                artist = target.artist,
                tracks = target.tracks,
                onBack = onCloseArtist,
                onPlayLinkedTrack = onPlayLinkedTrack,
                onPlayLinkedQueue = onPlayLinkedQueue,
                onPlayLinkedQueueOnPc = onPlayLinkedQueueOnPc,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (target is LinkedLibraryDetailTarget.Playlist) {
            LinkedPlaylistTracksPage(
                playlist = target.playlist,
                tracks = target.tracks,
                isLoading = target.isLoading,
                error = target.error,
                onBack = onClosePlaylist,
                onPlayLinkedTrack = onPlayLinkedTrack,
                onPlayLinkedQueue = onPlayLinkedQueue,
                onPlayLinkedQueueOnPc = onPlayLinkedQueueOnPc,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
    LibraryBrowserFrame(
        query = query, onQueryChange = onQueryChange,
        sources = { LibrarySourceStrip(selectedSource, true, onSelectSource) },
        actions = {
            IconButton(onClick = { onRefresh(normalizedQuery) }, enabled = !state.isLoading) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(L10nR.string.feature_library_refresh_pc_echo_library_5b6275))
            }
        },
    ) {
        val errorMessage = state.error
        LinkedLibraryHeader(
            selectedSource = selectedSource,
            linkedLibraryAvailable = true,
            selectedMode = selectedMode,
            selectedSortMode = selectedSortMode,
            albumSortMode = albumSortMode,
            artistSortMode = artistSortMode,
            folderSortMode = folderSortMode,
            foldersAvailable = !state.foldersUnavailable,
            onSelectSource = onSelectSource,
            onSelectMode = onSelectMode,
            onSortModeChange = onSortModeChange,
            onAlbumSortModeChange = onAlbumSortModeChange,
            onArtistSortModeChange = onArtistSortModeChange,
            onFolderSortModeChange = onFolderSortModeChange,
        )
        errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        if (state.isLoadingMore) {
            Text(
                text = stringResource(L10nR.string.feature_library_loading_more_from_pc_echo_tracks_size_state_e85d63, (tracks.size).toString(), (state.totalCount).toString()),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        val linkedTabMotion = rememberEchoContentMotion()
        AnimatedContent(
            targetState = selectedMode,
            transitionSpec = {
                linkedTabMotion.tabSwitch(targetState.ordinal > initialState.ordinal)
            },
            label = "linked-library-mode",
            modifier = Modifier.weight(1f),
        ) { mode ->
        when {
            state.isLoading && tracks.isEmpty() && albums.isEmpty() && playlists.isEmpty() -> LibraryCollectionEmpty(
                stringResource(L10nR.string.feature_library_reading_pc_echo_library_fccbe4),
            )
            mode == LinkedLibraryMode.Songs && sortedTracks.isEmpty() -> {
                LibraryCollectionEmpty(
                    if (query.isBlank()) {
                        stringResource(L10nR.string.feature_library_pc_echo_has_no_songs_to_show_a46ba6)
                    } else {
                        stringResource(L10nR.string.feature_library_pc_echo_has_no_matching_songs_40afe3)
                    },
                )
            }
            mode == LinkedLibraryMode.Albums && albums.isEmpty() -> {
                LibraryCollectionEmpty(
                    if (query.isBlank()) {
                        stringResource(L10nR.string.feature_library_pc_echo_has_no_albums_to_show_2784be)
                    } else {
                        stringResource(L10nR.string.feature_library_pc_echo_has_no_matching_albums_c433fb)
                    },
                )
            }
            mode == LinkedLibraryMode.Artists && artists.isEmpty() -> {
                LibraryCollectionEmpty(
                    if (query.isBlank()) {
                        stringResource(L10nR.string.feature_library_pc_echo_has_no_artists_to_show_b6975c)
                    } else {
                        stringResource(L10nR.string.feature_library_pc_echo_has_no_matching_artists_2dbc79)
                    },
                )
            }
            mode == LinkedLibraryMode.Playlists && filteredPlaylists.isEmpty() -> {
                LibraryCollectionEmpty(
                    if (query.isBlank()) {
                        stringResource(L10nR.string.feature_library_pc_echo_has_no_playlists_to_show_8e8438)
                    } else {
                        stringResource(L10nR.string.feature_library_pc_echo_has_no_matching_playlists_846ad7)
                    },
                )
            }
            mode == LinkedLibraryMode.Folders && state.loadingFolderPath != null &&
                state.folders.isEmpty() && state.folderTracks.isEmpty() -> {
                LibraryCollectionEmpty(stringResource(L10nR.string.feature_library_reading_pc_echo_library_fccbe4))
            }
            mode == LinkedLibraryMode.Folders && state.folders.isEmpty() && state.folderTracks.isEmpty() -> {
                LibraryCollectionEmpty(
                    if (query.isBlank()) {
                        stringResource(L10nR.string.feature_library_pc_echo_has_no_folders_to_show_9e12b8)
                    } else {
                        stringResource(L10nR.string.feature_library_pc_echo_has_no_matching_folders_4c90d1)
                    },
                )
            }
            mode == LinkedLibraryMode.Songs -> LinkedTrackList(
                tracks = sortedTracks,
                onPlayLinkedTrack = onPlayLinkedTrack,
                showAudioInfoTags = showTrackAudioInfoTags,
                modifier = Modifier.fillMaxSize(),
            )
            mode == LinkedLibraryMode.Albums -> LinkedAlbumWall(
                albums = albums,
                onOpenAlbum = onOpenAlbum,
                modifier = Modifier.fillMaxSize(),
            )
            mode == LinkedLibraryMode.Artists -> LinkedArtistWall(
                artists = artists,
                onOpenArtist = onOpenArtist,
                modifier = Modifier.fillMaxSize(),
            )
            mode == LinkedLibraryMode.Playlists -> LinkedPlaylistList(
                playlists = filteredPlaylists,
                onOpenPlaylist = onOpenPlaylist,
                modifier = Modifier.fillMaxSize(),
            )
            mode == LinkedLibraryMode.Folders -> LinkedFolderBrowser(
                path = state.folderPath,
                folders = state.folders,
                tracks = state.folderTracks,
                sortMode = folderSortMode,
                onOpenFolder = onRefreshFolders,
                onPlayLinkedTrack = onPlayLinkedTrack,
                onPlayLinkedQueue = onPlayLinkedQueue,
                onPlayLinkedQueueOnPc = onPlayLinkedQueueOnPc,
                modifier = Modifier.fillMaxSize(),
            )
        }
            }
        }
    }
    }
}

@Composable
private fun LinkedLibraryHeader(
    selectedSource: LibrarySourceMode,
    linkedLibraryAvailable: Boolean,
    selectedMode: LinkedLibraryMode,
    selectedSortMode: LibraryTrackSortMode,
    albumSortMode: AlbumSortMode,
    artistSortMode: ArtistSortMode,
    folderSortMode: FolderSortMode,
    foldersAvailable: Boolean,
    onSelectSource: (LibrarySourceMode) -> Unit,
    onSelectMode: (LinkedLibraryMode) -> Unit,
    onSortModeChange: (LibraryTrackSortMode) -> Unit,
    onAlbumSortModeChange: (AlbumSortMode) -> Unit,
    onArtistSortModeChange: (ArtistSortMode) -> Unit,
    onFolderSortModeChange: (FolderSortMode) -> Unit,
) {
    val modes = if (foldersAvailable) {
        LinkedLibraryMode.entries
    } else {
        LinkedLibraryMode.entries.filter { it != LinkedLibraryMode.Folders }
    }
    val selectedIndex = modes.indexOf(selectedMode).coerceAtLeast(0)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            LibraryTextTabs(modes.map { it.label() }, selectedIndex, { onSelectMode(modes[it]) })
        }
        when (selectedMode) {
            LinkedLibraryMode.Songs -> LibraryTrackSortMenu(selectedSortMode, onSortModeChange)
            LinkedLibraryMode.Albums -> LibraryAlbumSortMenu(albumSortMode, onAlbumSortModeChange)
            LinkedLibraryMode.Artists -> LibraryArtistSortMenu(artistSortMode, onArtistSortModeChange)
            LinkedLibraryMode.Folders -> LibraryFolderSortMenu(folderSortMode, onFolderSortModeChange)
            LinkedLibraryMode.Playlists -> Unit
        }
    }
}


@Composable
private fun LinkedTrackList(
    tracks: List<EchoRemoteTrack>,
    onPlayLinkedTrack: (EchoRemoteTrack) -> Unit,
    showAudioInfoTags: Boolean,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = LibraryBottomControlsPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = tracks,
            key = { track -> track.id ?: "${track.title}-${track.artist}-${track.album.orEmpty()}" },
        ) { track ->
            TrackRow(
                track = track.toEchoTrack(),
                onClick = { onPlayLinkedTrack(track) },
                showAudioInfoTags = showAudioInfoTags,
            )
        }
    }
}

@Composable
private fun LinkedPlaylistList(
    playlists: List<EchoRemotePlaylist>,
    onOpenPlaylist: (EchoRemotePlaylist) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = LibraryBottomControlsPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(
            items = playlists,
            key = { playlist -> playlist.id },
        ) { playlist ->
            LinkedPlaylistRow(
                playlist = playlist,
                onOpen = { onOpenPlaylist(playlist) },
            )
        }
    }
}

@Composable
private fun LinkedPlaylistRow(
    playlist: EchoRemotePlaylist,
    onOpen: () -> Unit,
) {
    val accent = rememberLibraryControlColor()
    val dark = LocalEchoDarkTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (dark) EchoGlassPanel.copy(alpha = 0.50f) else EchoHomeMist.copy(alpha = 0.46f))
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
                fontWeight = FontWeight.Bold,
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
            border = BorderStroke(1.dp, if (dark) EchoDarkGlassBorder else EchoGlassBorder),
            shape = RoundedCornerShape(12.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
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
private fun LinkedPlaylistTracksPage(
    playlist: EchoRemotePlaylist,
    tracks: List<EchoRemoteTrack>,
    isLoading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onPlayLinkedTrack: (EchoRemoteTrack) -> Unit,
    onPlayLinkedQueue: (List<EchoRemoteTrack>, Int) -> Unit,
    onPlayLinkedQueueOnPc: (List<EchoRemoteTrack>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LibraryDetailFrame(
        actions = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(L10nR.string.feature_library_back_to_pc_echo_playlists_499312),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtworkTile(
                    artworkUri = playlist.artworkUrl,
                    modifier = Modifier.size(62.dp),
                    accent = rememberLibraryArtworkAccent(),
                    cornerRadius = 14.dp,
                    elevation = 4.dp,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        playlist.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        linkedPlaylistSubtitle(playlist.copy(trackCount = playlist.trackCount.coerceAtLeast(tracks.size))),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        when {
            isLoading -> LibraryCollectionEmpty(
                stringResource(L10nR.string.feature_library_reading_pc_echo_playlist_1ba678),
            )
            tracks.isEmpty() && !error.isNullOrBlank() -> LibraryCollectionEmpty(error)
            tracks.isEmpty() -> LibraryCollectionEmpty(
                stringResource(L10nR.string.feature_library_this_pc_echo_playlist_has_no_playable_tracks_b480ec),
            )
            else -> {
                if (tracks.isNotEmpty()) {
                    TextButton(
                        onClick = { onPlayLinkedQueueOnPc(tracks, 0) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(L10nR.string.feature_library_play_on_pc_7c21a4))
                    }
                }
                LinkedTrackList(
                    tracks = tracks,
                    onPlayLinkedTrack = { track ->
                        val index = tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                        onPlayLinkedQueue(tracks, index)
                    },
                    showAudioInfoTags = false,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun LinkedAlbumWall(
    albums: List<AlbumSummary>,
    onOpenAlbum: (AlbumSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = LibraryWallGridCells,
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = LibraryBottomControlsPadding),
    ) {
        gridItems(
            items = albums,
            key = { album -> album.albumKey },
        ) { album ->
            AlbumWallCard(album = album, onClick = { onOpenAlbum(album) })
        }
    }
}

@Composable
private fun LinkedArtistWall(
    artists: List<ArtistSummary>,
    onOpenArtist: (ArtistSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = LibraryWallGridCells,
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 6.dp, bottom = LibraryBottomControlsPadding),
    ) {
        gridItems(
            items = artists,
            key = { artist -> artist.artistKey },
        ) { artist ->
            ArtistWallCard(artist = artist, onClick = { onOpenArtist(artist) })
        }
    }
}

@Composable
private fun LinkedAlbumTracksPage(
    album: AlbumSummary,
    tracks: List<EchoRemoteTrack>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onPlayLinkedTrack: (EchoRemoteTrack) -> Unit,
    onPlayLinkedQueue: (List<EchoRemoteTrack>, Int) -> Unit,
    onPlayLinkedQueueOnPc: (List<EchoRemoteTrack>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val albumTracks = remember(tracks) { tracks.map { it.toEchoTrack() } }
    val remoteTracksByUiId = remember(tracks, albumTracks) {
        tracks.zip(albumTracks).associate { (remote, uiTrack) -> uiTrack.id to remote }
    }
    if (isLoading && tracks.isEmpty()) {
        LibraryDetailFrame(
            actions = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(L10nR.string.feature_library_back_to_pc_echo_playlists_499312),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            modifier = modifier,
        ) {
            LibraryCollectionEmpty(stringResource(L10nR.string.feature_library_reading_pc_echo_library_fccbe4))
        }
        return
    }
    AlbumDetailListPage(
        album = album,
        tracks = albumTracks,
        onBack = onBack,
        onPlayAll = { if (tracks.isNotEmpty()) onPlayLinkedQueue(tracks, 0) },
        onShuffle = {
            if (tracks.isNotEmpty()) onPlayLinkedQueue(tracks.shuffled(), 0)
        },
        onPlayOnPc = { if (tracks.isNotEmpty()) onPlayLinkedQueueOnPc(tracks, 0) },
        onPlayTrack = { track ->
            val remote = remoteTracksByUiId[track.id] ?: return@AlbumDetailListPage
            val index = tracks.indexOfFirst { it.id == remote.id }.coerceAtLeast(0)
            onPlayLinkedQueue(tracks, index)
        },
        modifier = modifier,
    )
}

@Composable
private fun LinkedArtistTracksPage(
    artist: ArtistSummary,
    tracks: List<EchoRemoteTrack>,
    onBack: () -> Unit,
    onPlayLinkedTrack: (EchoRemoteTrack) -> Unit,
    onPlayLinkedQueue: (List<EchoRemoteTrack>, Int) -> Unit,
    onPlayLinkedQueueOnPc: (List<EchoRemoteTrack>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val artistTracks = remember(tracks) { tracks.map { it.toEchoTrack() } }
    val remoteTracksByUiId = remember(tracks, artistTracks) {
        tracks.zip(artistTracks).associate { (remote, uiTrack) -> uiTrack.id to remote }
    }
    ArtistDetailListPage(
        artist = artist,
        tracks = artistTracks,
        onBack = onBack,
        onPlayAll = { if (tracks.isNotEmpty()) onPlayLinkedQueue(tracks, 0) },
        onShuffle = {
            if (tracks.isNotEmpty()) onPlayLinkedQueue(tracks.shuffled(), 0)
        },
        onPlayOnPc = { if (tracks.isNotEmpty()) onPlayLinkedQueueOnPc(tracks, 0) },
        onPlayTrack = { track ->
            val remote = remoteTracksByUiId[track.id] ?: return@ArtistDetailListPage
            val index = tracks.indexOfFirst { it.id == remote.id }.coerceAtLeast(0)
            onPlayLinkedQueue(tracks, index)
        },
        modifier = modifier,
    )
}

@Composable
private fun LinkedFolderBrowser(
    path: String,
    folders: List<EchoRemoteFolder>,
    tracks: List<EchoRemoteTrack>,
    sortMode: FolderSortMode,
    onOpenFolder: (String) -> Unit,
    onPlayLinkedTrack: (EchoRemoteTrack) -> Unit,
    onPlayLinkedQueue: (List<EchoRemoteTrack>, Int) -> Unit,
    onPlayLinkedQueueOnPc: (List<EchoRemoteTrack>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val parentPath = EchoLinkLibraryQueryPolicy.parentFolderPath(path)
    val sortedFolders = remember(folders, sortMode) { folders.sortedForLinkedLibrary(sortMode) }
    Column(modifier.fillMaxSize()) {
        if (path.isNotBlank()) {
            TextButton(onClick = { onOpenFolder(parentPath) }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(L10nR.string.feature_library_open_parent_folder_2a11c0))
            }
        }
        if (tracks.isNotEmpty()) {
            TextButton(
                onClick = { onPlayLinkedQueueOnPc(tracks, 0) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(L10nR.string.feature_library_play_on_pc_7c21a4))
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(bottom = LibraryBottomControlsPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(sortedFolders, key = { folder -> folder.path }) { folder ->
                LinkedPlaylistRow(
                    playlist = EchoRemotePlaylist(
                        id = folder.path,
                        name = folder.name,
                        artworkUrl = folder.artworkUrl,
                        trackCount = folder.trackCount,
                        sourceLabel = folder.path,
                    ),
                    onOpen = { onOpenFolder(folder.path) },
                )
            }
            items(
                tracks,
                key = { track -> track.id ?: "${track.title}-${track.artist}" },
            ) { track ->
                TrackRow(
                    track = track.toEchoTrack(),
                    onClick = {
                        val index = tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                        onPlayLinkedQueue(tracks, index)
                    },
                    showAudioInfoTags = false,
                )
            }
        }
    }
}

private fun EchoRemoteTrack.toEchoTrack(): EchoTrack =
    EchoTrack(
        id = "echo-link:${id ?: "${title.hashCode()}-${artist.hashCode()}-${album.hashCode()}"}",
        uri = "",
        title = title,
        artist = artist.ifBlank { "PC ECHO" },
        album = album,
        albumArtist = artist.takeIf { it.isNotBlank() },
        artworkUri = artworkUrl,
        durationMs = durationMs,
        source = LibrarySource("echo-link"),
    )

@Composable
private fun linkedPlaylistSubtitle(playlist: EchoRemotePlaylist): String {
    val source = playlist.sourceLabel?.takeIf { it.isNotBlank() } ?: "PC ECHO"
    return stringResource(L10nR.string.feature_library_playlist_trackcount_tracks_source_3431c6, (playlist.trackCount).toString(), (source).toString())
}

@Composable
private fun LibraryTrackSortMenu(
    selectedSortMode: LibraryTrackSortMode,
    onSortModeChange: (LibraryTrackSortMode) -> Unit,
) {
    LibrarySortMenu(
        selected = selectedSortMode,
        options = LibraryTrackSortMode.entries,
        label = { it.label() },
        contentDescription = stringResource(L10nR.string.feature_library_set_track_sort_order_4053c0),
        onSelect = onSortModeChange,
    )
}

@Composable
private fun LibraryAlbumSortMenu(
    selectedSortMode: AlbumSortMode,
    onSortModeChange: (AlbumSortMode) -> Unit,
) {
    LibrarySortMenu(
        selected = selectedSortMode,
        options = AlbumSortMode.entries,
        label = { it.label() },
        contentDescription = stringResource(L10nR.string.feature_library_set_album_sort_order_7c2d91),
        onSelect = onSortModeChange,
    )
}

@Composable
private fun LibraryArtistSortMenu(
    selectedSortMode: ArtistSortMode,
    onSortModeChange: (ArtistSortMode) -> Unit,
) {
    LibrarySortMenu(
        selected = selectedSortMode,
        options = ArtistSortMode.entries,
        label = { it.label() },
        contentDescription = stringResource(L10nR.string.feature_library_set_artist_sort_order_e15a06),
        onSelect = onSortModeChange,
    )
}

@Composable
private fun LibraryFolderSortMenu(
    selectedSortMode: FolderSortMode,
    onSortModeChange: (FolderSortMode) -> Unit,
) {
    LibrarySortMenu(
        selected = selectedSortMode,
        options = FolderSortMode.entries,
        label = { it.label() },
        contentDescription = stringResource(L10nR.string.feature_library_set_folder_sort_order_9a4f33),
        onSelect = onSortModeChange,
    )
}

@Composable
private fun <T> LibrarySortMenu(
    selected: T,
    options: List<T>,
    label: @Composable (T) -> String,
    contentDescription: String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.AutoMirrored.Rounded.Sort,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(label(mode)) },
                    onClick = {
                        expanded = false
                        onSelect(mode)
                    },
                    trailingIcon = {
                        if (mode == selected) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                )
            }
        }
    }
}

private fun List<EchoRemoteFolder>.sortedForLinkedLibrary(mode: FolderSortMode): List<EchoRemoteFolder> {
    val byName = compareBy(String.CASE_INSENSITIVE_ORDER, EchoRemoteFolder::name)
        .thenBy(String.CASE_INSENSITIVE_ORDER, EchoRemoteFolder::path)
    return when (mode) {
        FolderSortMode.Path -> sortedWith(byName)
        FolderSortMode.TrackCount -> sortedWith(compareByDescending(EchoRemoteFolder::trackCount).then(byName))
        FolderSortMode.AlbumCount -> sortedWith(compareByDescending(EchoRemoteFolder::childFolderCount).then(byName))
        FolderSortMode.Duration,
        FolderSortMode.Size,
        FolderSortMode.RecentlyModified,
        -> sortedWith(byName)
    }
}

@Composable
private fun LibraryBrowserHeader(
    scanState: LibraryScanProgress,
    showScanResultBanner: Boolean,
    selectedSource: LibrarySourceMode,
    linkedLibraryAvailable: Boolean,
    onSelectSource: (LibrarySourceMode) -> Unit,
    selectedMode: LibraryViewMode,
    selectedSortMode: LibraryTrackSortMode,
    albumSortMode: AlbumSortMode,
    artistSortMode: ArtistSortMode,
    folderSortMode: FolderSortMode,
    onSelectMode: (LibraryViewMode) -> Unit,
    onSortModeChange: (LibraryTrackSortMode) -> Unit,
    onAlbumSortModeChange: (AlbumSortMode) -> Unit,
    onArtistSortModeChange: (ArtistSortMode) -> Unit,
    onFolderSortModeChange: (FolderSortMode) -> Unit,
) {
    if (showScanResultBanner) {
        LibraryScanResultBanner(scanState)
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            LibraryPagerTabs(selectedMode, onSelectMode, cloudOnly = selectedSource == LibrarySourceMode.Cloud)
        }
        when (selectedMode) {
            LibraryViewMode.Songs -> if (selectedSource == LibrarySourceMode.Local) {
                LibraryTrackSortMenu(selectedSortMode, onSortModeChange)
            }
            LibraryViewMode.Albums, LibraryViewMode.Cloud ->
                LibraryAlbumSortMenu(albumSortMode, onAlbumSortModeChange)
            LibraryViewMode.Artists -> LibraryArtistSortMenu(artistSortMode, onArtistSortModeChange)
            LibraryViewMode.Folders -> LibraryFolderSortMenu(folderSortMode, onFolderSortModeChange)
            LibraryViewMode.Genres, LibraryViewMode.Playlists -> Unit
        }
    }
}

private fun LibraryScanProgress.hasResultBannerMessage(): Boolean =
    when (phase) {
        LibraryScanPhase.Completed,
        LibraryScanPhase.Cancelled,
        LibraryScanPhase.Error -> true
        LibraryScanPhase.Idle,
        LibraryScanPhase.Preparing,
        LibraryScanPhase.QueryingMediaStore,
        LibraryScanPhase.Diffing,
        LibraryScanPhase.WritingDatabase,
        LibraryScanPhase.CleaningRemoved -> false
    }
