package app.echo.android.feature.library

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
import androidx.compose.foundation.pager.PagerState
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import app.echo.android.design.EchoIcon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.movableContentOf
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
import app.echo.android.design.EchoPanel
import app.echo.android.design.EmptyState
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.LocalEchoPageInteractive
import app.echo.android.design.PageChrome
import app.echo.android.design.echoTheme
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
import app.echo.android.model.settings.EchoLibraryLayout
import app.echo.android.model.settings.EchoLibraryCategory
import app.echo.android.model.library.LibraryTrackSortMode
import app.echo.android.model.library.LibraryTrackFilter
import app.echo.android.model.library.LibraryLyricsIndexState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

internal enum class LibraryViewMode(
    val icon: ImageVector,
) {
    Songs(Icons.AutoMirrored.Rounded.QueueMusic),
    Folders(Icons.Rounded.LibraryMusic),
    Albums(Icons.Rounded.LibraryMusic),
    Artists(Icons.Rounded.Person),
    Genres(Icons.Rounded.LibraryMusic),
    Composers(Icons.Rounded.Audiotrack),
    Cloud(Icons.Rounded.CloudQueue),
    Playlists(Icons.Rounded.LibraryMusic),
    Radio(Icons.Rounded.Radio),
}

@Composable
internal fun LibraryViewMode.label(): String = when (this) {
    LibraryViewMode.Radio -> stringResource(L10nR.string.radio_title)
    LibraryViewMode.Songs -> stringResource(L10nR.string.feature_library_songs_107b60)
    LibraryViewMode.Folders -> stringResource(L10nR.string.feature_library_folders_cc514a)
    LibraryViewMode.Albums -> stringResource(L10nR.string.feature_library_albums_e68c2b)
    LibraryViewMode.Artists -> stringResource(L10nR.string.feature_library_artists_1e19fb)
    LibraryViewMode.Genres -> stringResource(L10nR.string.feature_library_genres_8c2a11)
    LibraryViewMode.Composers -> stringResource(L10nR.string.feature_library_composers_4a91c2)
    LibraryViewMode.Cloud -> stringResource(L10nR.string.feature_library_cloud_466e60)
    LibraryViewMode.Playlists -> stringResource(L10nR.string.feature_library_playlists_56bf76)
}

internal enum class LinkedLibraryMode(
    val icon: ImageVector,
) {
    Songs(Icons.AutoMirrored.Rounded.QueueMusic),
    Albums(Icons.Rounded.LibraryMusic),
    Artists(Icons.Rounded.Person),
    Playlists(Icons.Rounded.LibraryMusic),
    Folders(Icons.Rounded.FolderOpen);

    val category: EchoLibraryCategory get() = EchoLibraryCategory.valueOf(name)
}

@Composable
private fun LinkedLibraryMode.label(): String = when (this) {
    LinkedLibraryMode.Songs -> stringResource(L10nR.string.feature_library_songs_107b60)
    LinkedLibraryMode.Albums -> stringResource(L10nR.string.feature_library_albums_e68c2b)
    LinkedLibraryMode.Artists -> stringResource(L10nR.string.feature_library_artists_1e19fb)
    LinkedLibraryMode.Playlists -> stringResource(L10nR.string.feature_library_playlists_56bf76)
    LinkedLibraryMode.Folders -> stringResource(L10nR.string.feature_library_folders_cc514a)
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
    linkedLibraryOnly: Boolean = false,
    selectedLibrarySourceId: String,
    artists: Flow<PagingData<ArtistSummary>>,
    composers: Flow<PagingData<app.echo.android.model.library.GenreSummary>>,
    genres: Flow<PagingData<app.echo.android.model.library.GenreSummary>> = kotlinx.coroutines.flow.emptyFlow(),
    folders: Flow<PagingData<FolderSummary>>,
    playlists: List<EchoPlaylist>,
    likedAlbums: Flow<PagingData<AlbumSummary>>,
    albumLiked: Boolean?,
    onSetAlbumLiked: (String, Boolean) -> Unit,
    onRestoreBuiltinPlaylists: () -> Unit,
    showTrackAudioInfoTags: Boolean,
    selectedAlbum: AlbumSummary?,
    selectedArtist: ArtistSummary?,
    selectedGenre: app.echo.android.model.library.GenreSummary? = null,
    selectedFolder: FolderSummary?,
    selectedPlaylist: EchoPlaylist?,
    albumDetailTracks: LazyPagingItems<EchoTrack>?,
    artistDetailTracks: LazyPagingItems<EchoTrack>?,
    artistDetailAlbums: LazyPagingItems<AlbumSummary>? = null,
    artistQuery: String = "",
    artistSort: LibraryTrackSortMode = LibraryTrackSortMode.Album,
    onArtistQueryChange: (String) -> Unit = {},
    onArtistSortChange: (LibraryTrackSortMode) -> Unit = {},
    genreDetailTracks: LazyPagingItems<EchoTrack>? = null,
    folderDetailTracks: LazyPagingItems<EchoTrack>?,
    playlistDetailTracks: LazyPagingItems<EchoTrack>?,
    onRequestPermission: () -> Unit,
    onLibraryQueryChange: (String) -> Unit,
    onLibrarySourceChange: (String) -> Unit,
    onTrackSortModeChange: (LibraryTrackSortMode) -> Unit,
    trackFilter: LibraryTrackFilter = LibraryTrackFilter(),
    lyricsIndexState: StateFlow<LibraryLyricsIndexState>? = null,
    onInspectLyrics: () -> Unit = {},
    onCancelLyricsInspection: () -> Unit = {},
    onTrackFilterChange: (LibraryTrackFilter) -> Unit = {},
    onFindSongLetterIndex: suspend (Char) -> Int,
    onFindAlbumLetterIndex: (suspend (Char, Boolean) -> Int)? = null,
    playingTrackId: String? = null,
    isPlaying: Boolean = false,
    onFindPlayingSongIndex: (suspend (String) -> Int?)? = null,
    onAlbumSortModeChange: (AlbumSortMode) -> Unit,
    onArtistSortModeChange: (ArtistSortMode) -> Unit,
    onFolderSortModeChange: (FolderSortMode) -> Unit,
    onAddMusic: () -> Unit,
    onCancelScan: () -> Unit,
    onRefreshLinkedLibrary: (String, EchoLibraryCategory) -> Unit,
    onPrioritizeLinkedLibrary: (EchoLibraryCategory) -> Unit,
    onOpenLinkedPlaylist: (EchoRemotePlaylist) -> Unit,
    onLoadLinkedPlaylistArtwork: suspend (EchoRemotePlaylist) -> String?,
    onOpenLinkedAlbum: (EchoRemoteAlbum) -> Unit = {},
    onRefreshLinkedFolders: (String) -> Unit = {},
    onLoadMoreLinkedTracks: () -> Unit = {},
    onLoadMoreLinkedAlbums: () -> Unit = {},
    onLoadMoreLinkedPlaylists: () -> Unit = {},
    onChangeLinkedFolderPage: (Int) -> Unit = {},
    onPlayLinkedQueue: (List<EchoRemoteTrack>, Int) -> Unit,
    onPlayLinkedQueueOnPc: ((List<EchoRemoteTrack>, Int) -> Unit)? = null,
    onPlayTrack: (EchoTrack, LibraryPlaybackOrigin) -> Unit,
    onPlayNext: (EchoTrack) -> Unit = {},
    onEnqueueTrack: (EchoTrack) -> Unit = {},
    onUpdateTrackMetadata: suspend (EchoTrackMetadataUpdate) -> Boolean,
    onImportLyricsForTrack: (EchoTrack) -> Unit,
    onPickTrackArtwork: (EchoTrack) -> Unit,
    onPlayAlbum: (AlbumSummary) -> Unit,
    onShuffleAlbum: (AlbumSummary) -> Unit,
    onPlayArtist: (ArtistSummary) -> Unit,
    onShuffleArtist: (ArtistSummary) -> Unit,
    onOpenGenre: (app.echo.android.model.library.GenreSummary) -> Unit = {},
    onPlayGenre: (app.echo.android.model.library.GenreSummary) -> Unit = {},
    onOpenComposer: (app.echo.android.model.library.GenreSummary) -> Unit = {},
    onPlayComposer: (app.echo.android.model.library.GenreSummary) -> Unit = {},
    onPlayPlaylist: (EchoPlaylist) -> Unit,
    onShufflePlaylist: (EchoPlaylist) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    experienceActions: LibraryExperienceActions? = null,
    playlistManagement: PlaylistManagementActions? = null,
    remotePlaylistManagement: RemoteLibraryWritebackActions? = null,
    onRenamePlaylist: (EchoPlaylist, String) -> Unit,
    onDeletePlaylist: (EchoPlaylist) -> Unit,
    onAddTrackToPlaylist: (EchoPlaylist, EchoTrack) -> Unit,
    onCreatePlaylistAndAddTrack: (String, EchoTrack) -> Unit,
    onRemoveTrackFromPlaylist: (EchoPlaylist, EchoTrack) -> Unit,
    onReorderPlaylistTracks: (EchoPlaylist, Int, Int) -> Unit,
    onReorderAlbumTracks: (AlbumSummary, Int, Int) -> Unit = { _, _, _ -> },
    onOpenAlbum: (AlbumSummary) -> Unit,
    onOpenArtist: (ArtistSummary) -> Unit,
    onOpenAlbumArtist: (() -> Unit)? = null,
    onOpenTrackArtist: ((EchoTrack) -> Unit)? = null,
    onOpenTrackAlbum: ((EchoTrack) -> Unit)? = null,
    onOpenFolder: (FolderSummary) -> Unit,
    onOpenPlaylist: (EchoPlaylist) -> Unit,
    onCloseDetail: () -> Unit,
    outerNavigationOwnsTransition: Boolean = false,
    detailBackProgress: () -> Float = { 0f },
    onLinkedLibraryPageOpenChanged: (Boolean) -> Unit = {},
    linkedDetailBackEnabled: Boolean = true,
    onOpenConnect: () -> Unit = {},
    cloudLibraryConfigured: Boolean = false,
    onImportM3uPlaylist: () -> Unit = {},
    onExportM3uPlaylist: (EchoPlaylist) -> Unit = {},
    onEnqueuePlaylist: ((EchoPlaylist) -> Unit)? = null,
    onSharePlaylist: ((EchoPlaylist) -> Unit)? = null,
    radioContent: @Composable (
        interactive: Boolean,
        placeToolbarActions: @Composable (@Composable () -> Unit) -> Unit,
    ) -> Unit,
    onOpenPodcasts: () -> Unit = {},
    albumArtistAlbums: ((AlbumSummary) -> Flow<PagingData<AlbumSummary>>?)? = null,
    onPinPlaylistShortcut: ((EchoPlaylist, Boolean) -> Unit)? = null,
    onWriteAlbumNfc: ((AlbumSummary) -> Unit)? = null,
    onWritePlaylistNfc: ((EchoPlaylist) -> Unit)? = null,
    libraryLayout: EchoLibraryLayout = EchoLibraryLayout(),
    onLibraryLayoutChange: ((EchoLibraryLayout) -> Unit)? = null,
) {
    val artistListState = rememberSaveable(selectedArtist?.artistKey, saver = androidx.compose.foundation.lazy.LazyListState.Saver) {
        androidx.compose.foundation.lazy.LazyListState()
    }
    val artistAlbumListState = rememberSaveable(selectedArtist?.artistKey, saver = androidx.compose.foundation.lazy.LazyListState.Saver) {
        androidx.compose.foundation.lazy.LazyListState()
    }
    val playNext = onPlayNext
    val enqueueTrack = onEnqueueTrack
    var selectedModeIndex by rememberSaveable { mutableIntStateOf(libraryPagerModes(false, libraryLayout).first().ordinal) }
    var selectedSource by remember {
        mutableStateOf(librarySourceModeFromId(selectedLibrarySourceId, linkedLibraryActive))
    }
    val layout = remember(libraryLayout) { libraryLayout.normalized() }
    var editingLayout by rememberSaveable { mutableStateOf(false) }
    var likedAlbumsOpen by rememberSaveable { mutableStateOf(false) }
    var playlistSortMode by rememberSaveable { mutableStateOf(PlaylistCatalogSort.Updated) }
    var navidromeVisible by rememberSaveable { mutableStateOf(false) }
    if (navidromeVisible && remotePlaylistManagement != null) NavidromeLibraryManager(remotePlaylistManagement) {
        navidromeVisible = false
    }
    val customizeLayout: (() -> Unit)? = if (onLibraryLayoutChange != null) ({ editingLayout = true }) else null
    val selectedMode = restoredLibraryViewMode(
        selectedModeIndex,
        cloudOnly = selectedSource == LibrarySourceMode.Cloud,
        layout = layout,
    )
    LaunchedEffect(selectedMode) { selectedModeIndex = selectedMode.ordinal }
    val viewingLikedAlbums = likedAlbumsOpen && selectedSource == LibrarySourceMode.Local && selectedMode == LibraryViewMode.Playlists
    fun closeLikedAlbums() {
        likedAlbumsOpen = false
        onLibraryQueryChange("")
    }
    LaunchedEffect(selectedMode, selectedSource) {
        if (!viewingLikedAlbums) likedAlbumsOpen = false
    }
    app.echo.android.design.EchoOverlayBackHandler(
        enabled = viewingLikedAlbums && linkedDetailBackEnabled && selectedAlbum == null && selectedArtist == null &&
            selectedFolder == null && selectedGenre == null && selectedPlaylist == null,
        onDismiss = ::closeLikedAlbums,
    )
    if (editingLayout && onLibraryLayoutChange != null) LibraryLayoutEditor(
        layout = layout,
        onDismiss = { editingLayout = false },
        onApply = onLibraryLayoutChange,
    )
    var linkedMode by rememberSaveable { mutableStateOf(LinkedLibraryMode.Songs) }
    var selectedLinkedAlbum by remember { mutableStateOf<AlbumSummary?>(null) }
    var selectedLinkedArtist by remember { mutableStateOf<ArtistSummary?>(null) }
    var selectedLinkedPlaylist by remember { mutableStateOf<EchoRemotePlaylist?>(null) }
    val songListState = rememberLazyListState()
    // Share the active song page with the title-row navigation controls.
    val songTrackItems = if (selectedSource == LibrarySourceMode.Local && selectedMode == LibraryViewMode.Songs) {
        tracks.collectAsLazyPagingItems()
    } else null
    val firstSongId by remember(songTrackItems) {
        derivedStateOf { songTrackItems?.itemSnapshotList?.getOrNull(0)?.id }
    }
    DisposableEffect(songTrackItems, firstSongId) {
        // When the new first row arrives, anchor the top by index. Following the old row's key
        // would scroll the viewport along with that row instead of showing it move in place.
        if (firstSongId != null && songListState.firstVisibleItemIndex == 0 &&
            songListState.firstVisibleItemScrollOffset == 0
        ) {
            songListState.requestScrollToItem(0)
        }
        onDispose { }
    }
    val librarySnackbar = remember { SnackbarHostState() }
    // A different browse order starts at the top; filter-only changes retain the current list.
    val songNavigation = remember(libraryQuery, trackSortMode, selectedLibrarySourceId) {
        listOf(libraryQuery, trackSortMode.name, selectedLibrarySourceId)
    }
    var previousSongNavigation by rememberSaveable { mutableStateOf(songNavigation) }
    LaunchedEffect(songNavigation) {
        // Re-entering from radio, podcasts or relations restores the saved list, not a new search.
        if (previousSongNavigation != songNavigation) {
            if (songListState.firstVisibleItemIndex != 0 || songListState.firstVisibleItemScrollOffset != 0) {
                songListState.requestScrollToItem(0)
            }
            previousSongNavigation = songNavigation
        }
    }
    var addToPlaylistTrack by remember { mutableStateOf<EchoTrack?>(null) }
    var scanWasActive by remember { mutableStateOf(false) }
    var showScanResultBanner by remember { mutableStateOf(false) }
    val scanCompletionMessage = libraryScanCompletionMessage(scanState)

    LaunchedEffect(scanState.phase) {
        if (scanState.isScanning) {
            scanWasActive = true
            showScanResultBanner = false
        } else if (scanWasActive) {
            scanWasActive = false
            showScanResultBanner = scanState.phase == LibraryScanPhase.Cancelled || scanState.phase == LibraryScanPhase.Error
            scanCompletionMessage?.let { librarySnackbar.showSnackbar(it) }
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
        selectedModeIndex = restoredLibraryViewMode(
            selectedModeIndex,
            cloudOnly = persistedSource == LibrarySourceMode.Cloud,
            layout = layout,
        ).ordinal
        if (persistedSource != selectedSource) {
            selectedSource = persistedSource
            selectedLinkedAlbum = null
            selectedLinkedArtist = null
            selectedLinkedPlaylist = null
        }
    }

    fun selectSource(source: LibrarySourceMode) {
        if (selectedMode == LibraryViewMode.Radio) {
            selectedModeIndex = LibraryViewMode.Songs.ordinal
        }
        if (selectedSource == source) return
        selectedSource = source
        onLibrarySourceChange(source.id)
        selectedLinkedAlbum = null
        selectedLinkedArtist = null
        selectedLinkedPlaylist = null
        onCloseDetail()
        when (source) {
            LibrarySourceMode.Local -> {
                if (selectedMode == LibraryViewMode.Cloud) {
                    selectedModeIndex = LibraryViewMode.Songs.ordinal
                }
            }
            LibrarySourceMode.Cloud -> selectedModeIndex = LibraryViewMode.Albums.ordinal
            LibrarySourceMode.PcEcho -> if (linkedLibraryAvailable) onRefreshLinkedLibrary(libraryQuery, linkedMode.category)
        }
    }

    fun selectRadio() {
        if (selectedSource != LibrarySourceMode.Local) {
            selectSource(LibrarySourceMode.Local)
        }
        selectedModeIndex = LibraryViewMode.Radio.ordinal
    }

    val prefersSplit = app.echo.android.design.LocalEchoLayout.current.supportsTwoPanes()
    val localModes = remember(layout) { libraryPagerModes(cloudOnly = false, layout = layout) }
    val cloudModes = remember { libraryPagerModes(cloudOnly = true) }
    // Keep the category position when the browser moves between the overlay and split layouts.
    val localPager = remember(localModes) {
        PagerState(currentPage = localModes.indexOf(selectedMode).coerceAtLeast(0)) { localModes.size }
    }
    val cloudPager = remember { PagerState { cloudModes.size } }

    @Composable
    fun LocalLibraryBody(
        displayedSource: LibrarySourceMode,
        placeRadioToolbarActions: @Composable (@Composable () -> Unit) -> Unit,
    ) {
        val cloud = displayedSource == LibrarySourceMode.Cloud
        val modes = if (cloud) cloudModes else localModes
        val pager = if (cloud) cloudPager else localPager
        val selectMode: (LibraryViewMode) -> Unit = { mode -> selectedModeIndex = mode.ordinal }
        fun openAlbumFrom(mode: LibraryViewMode, album: AlbumSummary) {
            // A tap can arrive before the pager publishes its newly settled category.
            selectMode(mode)
            onOpenAlbum(album)
        }
        Column(Modifier.fillMaxSize()) {
                Column(Modifier.padding(horizontal = 24.dp)) {
                    if (scanState.isScanning) LibraryScanStatus(scanState, onCancelScan)
                    if (showScanResultBanner) LibraryScanResultBanner(scanState)
                    LibraryPagerTabs(
                        selectedMode, selectMode, modes = modes, pager = pager,
                        showAlbumArtists = layout.showAlbumArtists,
                    )
                }
                if (selectedMode == LibraryViewMode.Songs && displayedSource == LibrarySourceMode.Local) {
                    Box(Modifier.padding(start = 24.dp)) {
                        LibraryTrackFilterSummary(trackFilter, onTrackFilterChange)
                    }
                }
                    Box(modifier = Modifier.weight(1f)) {
                        LibraryCategoryPager(
                            pager = pager,
                            modes = modes,
                            selectedMode = selectedMode,
                            onSelectMode = selectMode,
                            modifier = Modifier.fillMaxSize(),
                        ) { mode ->
                        val showAlphabetIndex = libraryQuery.isBlank() && when (mode) {
                            LibraryViewMode.Songs -> trackSortMode == LibraryTrackSortMode.Title && !trackFilter.isActive
                            LibraryViewMode.Albums -> albumSortMode == AlbumSortMode.Title && onFindAlbumLetterIndex != null
                            else -> false
                        }
                        val songsPage = mode == LibraryViewMode.Songs
                        // Song rows use their own insets, and alphabet rails sit at the page edge.
                        Box(
                            Modifier.fillMaxSize().padding(
                                start = if (songsPage) 0.dp else 24.dp,
                                end = if (songsPage || showAlphabetIndex) 0.dp else 24.dp,
                            ),
                        ) {
                        when (mode) {
                            LibraryViewMode.Radio -> radioContent(
                                displayedSource == selectedSource && selectedMode == LibraryViewMode.Radio &&
                                    LocalEchoPageInteractive.current,
                                placeRadioToolbarActions,
                            )
                            LibraryViewMode.Songs -> {
                                val trackItems = songTrackItems ?: tracks.collectAsLazyPagingItems()
                                val showInitialTrackLoading =
                                    trackItems.itemCount == 0 &&
                                        trackItems.loadState.refresh is LoadState.Loading
                                val showInitialTrackError =
                                    trackItems.itemCount == 0 &&
                                        trackItems.loadState.refresh is LoadState.Error
                                val lightweightDeletion = app.echo.android.design.LocalEchoEffectivePerformanceMode.current.isLightweight
                                // Retain the outgoing lazy layout so its last item can finish departing.
                                AnimatedContent(
                                    targetState = trackItems.itemCount == 0,
                                    transitionSpec = { libraryEmptyTransition(lightweightDeletion) },
                                    modifier = Modifier.fillMaxSize(),
                                    label = "library-song-empty-transition",
                                ) { empty ->
                                when {
                                    empty && showInitialTrackLoading -> LibraryCollectionEmpty(
                                        stringResource(L10nR.string.feature_library_loading_library_a77123),
                                    )
                                    empty && showInitialTrackError -> LibraryCollectionEmpty(
                                        title = stringResource(L10nR.string.feature_library_library_query_failed_f9c538),
                                        actionLabel = stringResource(L10nR.string.library_retry), onAction = { trackItems.retry() },
                                    )
                                    empty && trackFilter.isActive -> LibraryFilteredEmptyState(
                                        trackFilter, lyricsIndexState, onInspectLyrics, onCancelLyricsInspection,
                                        onClear = { onTrackFilterChange(LibraryTrackFilter()) },
                                    )
                                    empty -> LibraryCollectionEmpty(
                                        title = stringResource(if (libraryQuery.isNotBlank()) L10nR.string.library_no_matches else L10nR.string.library_start_collection),
                                        detail = stringResource(if (libraryQuery.isNotBlank()) L10nR.string.library_search_hint else L10nR.string.library_import_hint),
                                        actionLabel = stringResource(if (libraryQuery.isNotBlank()) L10nR.string.library_clear_search else L10nR.string.library_add_music),
                                        onAction = if (libraryQuery.isNotBlank()) ({ onLibraryQueryChange("") }) else onAddMusic,
                                    )
                                    else -> Box(Modifier.fillMaxSize()) {
                                        TrackList(
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
                                            onOpenArtist = onOpenTrackArtist,
                                            onOpenAlbum = onOpenTrackAlbum,
                                            showAudioInfoTags = showTrackAudioInfoTags,
                                            listState = songListState,
                                            playingTrackId = playingTrackId,
                                            isPlaying = isPlaying,
                                            modifier = Modifier.fillMaxSize().padding(
                                                start = 6.dp,
                                                end = if (showAlphabetIndex) LibraryAlphabetIndexWidth else 0.dp,
                                            ),
                                        )
                                        if (showAlphabetIndex) {
                                            SongAlphabetIndex(
                                                itemCount = trackItems.itemCount,
                                                listState = songListState,
                                                findIndex = onFindSongLetterIndex,
                                                modifier = Modifier.align(Alignment.CenterEnd),
                                            )
                                        }
                                    }
                                }
                                }
                            }

                            LibraryViewMode.Folders -> FolderList(
                                folders = folders.collectAsLazyPagingItems(),
                                onOpenFolder = onOpenFolder,
                                modifier = Modifier.fillMaxSize(),
                            )

                            LibraryViewMode.Albums -> {
                                val albumItems = if (displayedSource == LibrarySourceMode.Cloud) {
                                    remoteAlbums.collectAsLazyPagingItems()
                                } else {
                                    albums.collectAsLazyPagingItems()
                                }
                                GuidedAlbumWall(
                                    findLetterIndex = if (showAlphabetIndex) ({ letter ->
                                        onFindAlbumLetterIndex!!.invoke(letter, displayedSource == LibrarySourceMode.Cloud)
                                    }) else null,
                                    albums = albumItems,
                                    onOpenAlbum = { openAlbumFrom(mode, it) },
                                    isCloud = displayedSource == LibrarySourceMode.Cloud,
                                    cloudConfigured = cloudLibraryConfigured,
                                    query = libraryQuery,
                                    onClearSearch = { onLibraryQueryChange("") },
                                    onAddMusic = onAddMusic,
                                    onOpenConnect = onOpenConnect,
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

                            LibraryViewMode.Composers -> GenreWall(
                                genres = composers.collectAsLazyPagingItems(),
                                onOpenGenre = onOpenComposer,
                                modifier = Modifier.fillMaxSize(),
                                loadingLabel = stringResource(L10nR.string.feature_library_loading_composers_c81e2a),
                                errorLabel = stringResource(L10nR.string.feature_library_failed_to_load_composers_9b4d01),
                                emptyLabel = stringResource(L10nR.string.feature_library_this_library_has_no_composers_to_show_yet_2e8c17),
                            )

                            LibraryViewMode.Cloud -> GuidedAlbumWall(
                                albums = remoteAlbums.collectAsLazyPagingItems(),
                                onOpenAlbum = { openAlbumFrom(mode, it) },
                                isCloud = true,
                                cloudConfigured = cloudLibraryConfigured,
                                query = libraryQuery,
                                onClearSearch = { onLibraryQueryChange("") },
                                onAddMusic = onAddMusic,
                                onOpenConnect = onOpenConnect,
                                modifier = Modifier.fillMaxSize(),
                            )

                            LibraryViewMode.Playlists -> if (viewingLikedAlbums) {
                                LikedAlbumsPanel(
                                    albums = likedAlbums.collectAsLazyPagingItems(),
                                    query = libraryQuery,
                                    onBack = ::closeLikedAlbums,
                                    onOpenAlbum = { openAlbumFrom(mode, it) },
                                )
                            } else LocalPlaylistPanel(
                                playlists = playlists,
                                query = libraryQuery,
                                sort = playlistSortMode,
                                onOpenLikedAlbums = { likedAlbumsOpen = true },
                                onRestoreBuiltinPlaylists = onRestoreBuiltinPlaylists,
                                onOpenPlaylist = onOpenPlaylist,
                                onPlayPlaylist = onPlayPlaylist,
                                onShufflePlaylist = onShufflePlaylist,
                                onCreatePlaylist = onCreatePlaylist,
                                management = playlistManagement,
                                experienceActions = experienceActions,
                                remoteManagement = remotePlaylistManagement,
                                onRenamePlaylist = onRenamePlaylist,
                                onDeletePlaylist = onDeletePlaylist,
                                onImportM3uPlaylist = onImportM3uPlaylist,
                                onExportM3uPlaylist = onExportM3uPlaylist,
                                onEnqueuePlaylist = onEnqueuePlaylist,
                                onSharePlaylist = onSharePlaylist,
                                onPinPlaylistShortcut = onPinPlaylistShortcut,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        }
                        }
                    }
        }
    }

    @Composable
    fun SharedLibraryBrowser() {
        val radioSelected = selectedSource == LibrarySourceMode.Local && selectedMode == LibraryViewMode.Radio
        var radioToolbarActions by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
        LibraryBrowserFrame(
            query = libraryQuery,
            onQueryChange = onLibraryQueryChange,
            searchEnabled = !radioSelected,
            searchPlaceholder = stringResource(
                when {
                    viewingLikedAlbums -> L10nR.string.album_liked_search
                    selectedSource == LibrarySourceMode.Local && selectedMode == LibraryViewMode.Playlists -> L10nR.string.playlist_search
                    else -> L10nR.string.feature_library_search_songs_artists_albums_14dc2c
                },
            ),
            sourcePicker = {
                LibrarySourcePicker(
                    selectedSource = selectedSource,
                    onSelectSource = ::selectSource,
                    radioSelected = radioSelected,
                    onSelectRadio = ::selectRadio,
                    onSelectPodcasts = onOpenPodcasts,
                    onAddMusic = onAddMusic.takeIf {
                        selectedSource == LibrarySourceMode.Local && !radioSelected &&
                            selectedMode != LibraryViewMode.Playlists
                    },
                )
            },
            navigationActions = {
                if (songTrackItems != null) {
                    SongListNavigation(
                        tracks = songTrackItems,
                        listState = songListState,
                        playingTrackId = playingTrackId,
                        queryKey = Triple(libraryQuery, trackSortMode, trackFilter),
                        findIndex = onFindPlayingSongIndex,
                        snackbar = librarySnackbar,
                    )
                }
            },
            actions = {
                when (selectedSource) {
                    LibrarySourceMode.Local -> if (radioSelected) {
                        radioToolbarActions?.invoke()
                    } else {
                        LibraryHiddenTracksAccess()
                    }
                    LibrarySourceMode.PcEcho -> if (linkedLibraryAvailable) {
                        LinkedLibraryRefreshAction(
                            state = linkedLibraryState,
                            onRefresh = { onRefreshLinkedLibrary(libraryQuery.trim(), linkedMode.category) },
                        )
                    }
                    LibrarySourceMode.Cloud -> if (remotePlaylistManagement != null) {
                        TextButton(onClick = { navidromeVisible = true }) { Text("Navidrome") }
                    }
                }
            },
            sortAction = {
                if (selectedSource != LibrarySourceMode.PcEcho) {
                    LibraryBrowserSortAction(
                        layout = layout,
                        onLayoutChange = onLibraryLayoutChange,
                        selectedSource = selectedSource,
                        selectedMode = selectedMode,
                        onCustomize = customizeLayout,
                        selectedSortMode = trackSortMode,
                        trackFilter = trackFilter,
                        lyricsIndexState = lyricsIndexState,
                        onInspectLyrics = onInspectLyrics,
                        onCancelLyricsInspection = onCancelLyricsInspection,
                        onTrackFilterChange = onTrackFilterChange,
                        albumSortMode = albumSortMode,
                        artistSortMode = artistSortMode,
                        folderSortMode = folderSortMode,
                        playlistSortMode = playlistSortMode.takeUnless { viewingLikedAlbums },
                        onSortModeChange = onTrackSortModeChange,
                        onAlbumSortModeChange = onAlbumSortModeChange,
                        onArtistSortModeChange = onArtistSortModeChange,
                        onFolderSortModeChange = onFolderSortModeChange,
                        onPlaylistSortModeChange = { playlistSortMode = it },
                    )
                }
            },
        ) {
            val sourceMotion = rememberEchoContentMotion()
            AnimatedContent(
                targetState = selectedSource,
                transitionSpec = { sourceMotion.sourceSwitch() },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                label = "library-source-body",
            ) { displayedSource ->
                val linkedSource = displayedSource == LibrarySourceMode.PcEcho
                Box(Modifier.fillMaxSize().padding(horizontal = if (linkedSource) 24.dp else 0.dp)) {
                when {
                    displayedSource == LibrarySourceMode.PcEcho && linkedLibraryAvailable -> {
                        val linkedState by linkedLibraryState.collectAsState()
                        val linkedModes = remember(layout, linkedState.foldersUnavailable) {
                            val available = EchoLibraryCategory.entries.filterNot {
                                it == EchoLibraryCategory.Folders && linkedState.foldersUnavailable
                            }
                            layout.visibleCategories(available).map { LinkedLibraryMode.valueOf(it.name) }
                        }
                        val effectiveLinkedMode = linkedMode.takeIf { it in linkedModes } ?: linkedModes.first()
                        LaunchedEffect(effectiveLinkedMode) {
                            linkedMode = effectiveLinkedMode
                            if (effectiveLinkedMode == LinkedLibraryMode.Folders) {
                                onRefreshLinkedFolders(linkedLibraryState.value.folderPath)
                            }
                        }
                        LinkedEchoLibraryPage(
                            state = linkedState,
                            libraryOnly = linkedLibraryOnly,
                            query = libraryQuery,
                            selectedMode = effectiveLinkedMode,
                            modes = linkedModes,
                            onCustomize = customizeLayout,
                            selectedSource = displayedSource,
                            selectedSortMode = trackSortMode,
                            albumSortMode = albumSortMode,
                            artistSortMode = artistSortMode,
                            folderSortMode = folderSortMode,
                            showTrackAudioInfoTags = showTrackAudioInfoTags,
                            onSortModeChange = onTrackSortModeChange,
                            onAlbumSortModeChange = onAlbumSortModeChange,
                            onArtistSortModeChange = onArtistSortModeChange,
                            onFolderSortModeChange = onFolderSortModeChange,
                            onSelectMode = { mode ->
                                linkedMode = mode
                                selectedLinkedAlbum = null
                                selectedLinkedArtist = null
                                selectedLinkedPlaylist = null
                            },
                            onOpenAlbum = { album ->
                                linkedMode = LinkedLibraryMode.Albums
                                selectedLinkedAlbum = album
                                linkedLibraryState.value.albums.firstOrNull {
                                    EchoLinkLibraryQueryPolicy.remoteAlbumKey(it.id) == album.albumKey
                                }?.let(onOpenLinkedAlbum)
                            },
                            onOpenArtist = { artist -> selectedLinkedArtist = artist },
                            onOpenPlaylist = { playlist ->
                                selectedLinkedPlaylist = playlist
                                onOpenLinkedPlaylist(playlist)
                            },
                            onLoadPlaylistArtwork = onLoadLinkedPlaylistArtwork,
                            onRefresh = { onRefreshLinkedLibrary(it, effectiveLinkedMode.category) },
                            onPrioritize = onPrioritizeLinkedLibrary,
                            onRefreshFolders = onRefreshLinkedFolders,
                            onLoadMoreTracks = onLoadMoreLinkedTracks,
                            onLoadMoreAlbums = onLoadMoreLinkedAlbums,
                            onLoadMorePlaylists = onLoadMoreLinkedPlaylists,
                            onChangeFolderPage = onChangeLinkedFolderPage,
                            onPlayLinkedQueue = onPlayLinkedQueue,
                            onPlayLinkedQueueOnPc = onPlayLinkedQueueOnPc,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    displayedSource == LibrarySourceMode.PcEcho -> LibraryCollectionEmpty(
                        title = stringResource(L10nR.string.feature_library_not_connected_c4d337),
                        detail = stringResource(L10nR.string.feature_library_connect_pc_echo_in_link_first_then_you_31fc3e),
                        actionLabel = stringResource(L10nR.string.library_open_connect),
                        onAction = onOpenConnect,
                    )
                    else -> LocalLibraryBody(displayedSource) { actions ->
                        val latestActions = rememberUpdatedState(actions)
                        val toolbar: @Composable () -> Unit = remember { { latestActions.value() } }
                        // The pager owns the radio screen and its actions; the header only renders them.
                        DisposableEffect(toolbar) {
                            radioToolbarActions = toolbar
                            onDispose {
                                if (radioToolbarActions === toolbar) radioToolbarActions = null
                            }
                        }
                    }
                }
                SnackbarHost(
                    librarySnackbar,
                    Modifier.align(Alignment.BottomCenter).padding(
                        start = if (linkedSource) 0.dp else 24.dp,
                        end = 24.dp,
                        bottom = LibraryBottomControlsPadding,
                    ),
                )
                }
            }
        }
    }

    val activeAlbumDetail = selectedAlbum
    val activeArtistDetail = selectedArtist
    val activeFolderDetail = selectedFolder
    val activePlaylistDetail = selectedPlaylist
    val linkedAlbumDetail = selectedLinkedAlbum
    val linkedArtistDetail = selectedLinkedArtist
    val linkedPlaylistDetail = selectedLinkedPlaylist
    val linkedPageOpen = selectedSource == LibrarySourceMode.PcEcho
    val linkedDetailOpen = linkedPageOpen &&
        (linkedAlbumDetail != null || linkedArtistDetail != null || linkedPlaylistDetail != null)
    val reportLinkedPage = rememberUpdatedState(onLinkedLibraryPageOpenChanged)
    // The PC browser (including the playlist list) owns its gestures before a detail opens.
    androidx.compose.runtime.DisposableEffect(linkedPageOpen) {
        reportLinkedPage.value(linkedPageOpen)
        onDispose { reportLinkedPage.value(false) }
    }
    val linkedBack = app.echo.android.design.rememberEchoBackProgress()
    app.echo.android.design.EchoOverlayBackHandler(
        enabled = linkedDetailOpen && linkedDetailBackEnabled,
        onProgress = linkedBack::update,
        onCancel = { linkedBack.restore() },
    ) {
        linkedBack.reset()
        when {
            selectedLinkedAlbum != null -> selectedLinkedAlbum = null
            selectedLinkedArtist != null -> selectedLinkedArtist = null
            else -> selectedLinkedPlaylist = null
        }
    }
    val detailTransitionTarget = when {
        selectedSource == LibrarySourceMode.PcEcho && linkedAlbumDetail != null ->
            LibraryDetailTransitionTarget.LinkedAlbum(linkedAlbumDetail)
        selectedSource == LibrarySourceMode.PcEcho && linkedArtistDetail != null ->
            LibraryDetailTransitionTarget.LinkedArtist(linkedArtistDetail)
        selectedSource == LibrarySourceMode.PcEcho && linkedPlaylistDetail != null ->
            LibraryDetailTransitionTarget.LinkedPlaylist(linkedPlaylistDetail)
        selectedSource == LibrarySourceMode.PcEcho -> LibraryDetailTransitionTarget.Browser
        activeAlbumDetail != null && albumDetailTracks != null ->
            LibraryDetailTransitionTarget.AlbumDetail(activeAlbumDetail, albumDetailTracks, albumLiked, onOpenAlbumArtist)
        selectedGenre != null && genreDetailTracks != null ->
            LibraryDetailTransitionTarget.GenreDetail(selectedGenre, genreDetailTracks)
        activeArtistDetail != null && artistDetailTracks != null ->
            LibraryDetailTransitionTarget.ArtistDetail(
                activeArtistDetail, artistDetailTracks, artistDetailAlbums,
                artistQuery, artistSort, artistListState, artistAlbumListState,
            )
        activeFolderDetail != null && folderDetailTracks != null ->
            LibraryDetailTransitionTarget.FolderDetail(activeFolderDetail, folderDetailTracks)
        activePlaylistDetail != null && playlistDetailTracks != null -> {
            val livePlaylist = playlists.firstOrNull { it.id == activePlaylistDetail.id }
                ?: activePlaylistDetail
            LibraryDetailTransitionTarget.PlaylistDetail(livePlaylist, playlistDetailTracks)
        }
        else -> LibraryDetailTransitionTarget.Browser
    }

    val splitDetails = prefersSplit
    // Scrub only the plain "detail -> browser" return; stepping album -> artist or leaving
    // for another tab keeps its committed transition.
    val detailReturnsToBrowser = !splitDetails && !outerNavigationOwnsTransition && when (detailTransitionTarget) {
        LibraryDetailTransitionTarget.Browser -> false
        is LibraryDetailTransitionTarget.AlbumDetail -> activeArtistDetail == null
        is LibraryDetailTransitionTarget.LinkedAlbum -> linkedArtistDetail == null
        else -> true
    }
    val browserRenderer: @Composable () -> Unit = { SharedLibraryBrowser() }
    val latestBrowserRenderer = rememberUpdatedState(browserRenderer)
    val browserPane = remember { movableContentOf { latestBrowserRenderer.value() } }

    @Composable
    fun DetailContent(target: LibraryDetailTransitionTarget) {
        when (target) {
            is LibraryDetailTransitionTarget.AlbumDetail -> AlbumDetailPage(
                album = target.album,
                liked = target.liked,
                onLikedChange = { liked -> onSetAlbumLiked(target.album.albumKey, liked) },
                onWriteNfc = onWriteAlbumNfc?.takeIf { selectedSource == LibrarySourceMode.Local }?.let { write -> { write(target.album) } },
                tracks = target.tracks,
                onBack = onCloseDetail,
                onPlayTrack = { track ->
                    onPlayTrack(track, LibraryPlaybackOrigin.Album(target.album.albumKey))
                },
                onMoveTrack = if (selectedSource == LibrarySourceMode.Local) {
                    { from, to -> onReorderAlbumTracks(target.album, from, to) }
                } else {
                    null
                },
                onUpdateTrackMetadata = onUpdateTrackMetadata,
                onImportLyrics = onImportLyricsForTrack,
                onPickArtwork = onPickTrackArtwork,
                onAddToPlaylist = { track -> addToPlaylistTrack = track },
                onPlayNext = playNext,
                onEnqueue = enqueueTrack,
                onOpenArtist = target.onOpenArtist,
                onOpenTrackArtist = onOpenTrackArtist,
                artistAlbums = albumArtistAlbums?.let { albums -> { albums(target.album) } },
                onOpenAlbum = onOpenAlbum,
                playingTrackId = playingTrackId,
                modifier = Modifier.fillMaxSize(),
                isPlaying = isPlaying,
            )
            is LibraryDetailTransitionTarget.GenreDetail -> GenreTrackDetailPage(
                artist = ArtistSummary(
                    artistKey = target.genre.genreKey,
                    name = target.genre.name,
                    artworkUri = target.genre.artworkUri,
                    albumCount = target.genre.albumCount,
                    trackCount = target.genre.trackCount,
                    durationMs = target.genre.durationMs,
                ),
                tracks = target.tracks,
                onBack = onCloseDetail,
                onPlayAll = { onPlayGenre(target.genre) },
                onShuffle = { onPlayGenre(target.genre) },
                onPlayTrack = { track ->
                    onPlayTrack(track, LibraryPlaybackOrigin.Songs)
                },
                onUpdateTrackMetadata = onUpdateTrackMetadata,
                onImportLyrics = onImportLyricsForTrack,
                onPickArtwork = onPickTrackArtwork,
                onAddToPlaylist = { track -> addToPlaylistTrack = track },
                onPlayNext = playNext,
                onEnqueue = enqueueTrack,
                onOpenArtist = onOpenTrackArtist,
                onOpenAlbum = onOpenTrackAlbum,
            )
            is LibraryDetailTransitionTarget.ArtistDetail -> ArtistDetailPage(
                artist = target.artist,
                tracks = target.tracks,
                onBack = onCloseDetail,
                onPlayAll = { onPlayArtist(target.artist) },
                albums = target.albums,
                query = target.query,
                sort = target.sort,
                onQueryChange = onArtistQueryChange,
                onSortChange = onArtistSortChange,
                onOpenAlbum = onOpenAlbum,
                listState = target.listState,
                albumListState = target.albumListState,
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
                onOpenTrackAlbum = onOpenTrackAlbum,
                modifier = Modifier.fillMaxSize(),
            )
            is LibraryDetailTransitionTarget.FolderDetail -> FolderDetailPage(
                folder = target.folder,
                tracks = target.tracks,
                onBack = onCloseDetail,
                onPlayTrack = { track ->
                    onPlayTrack(track, LibraryPlaybackOrigin.Folder(target.folder.folderKey))
                },
                onUpdateTrackMetadata = onUpdateTrackMetadata,
                onImportLyrics = onImportLyricsForTrack,
                onPickArtwork = onPickTrackArtwork,
                onAddToPlaylist = { track -> addToPlaylistTrack = track },
                onPlayNext = playNext,
                onEnqueue = enqueueTrack,
                onOpenArtist = onOpenTrackArtist,
                onOpenAlbum = onOpenTrackAlbum,
                modifier = Modifier.fillMaxSize(),
            )
            is LibraryDetailTransitionTarget.PlaylistDetail -> PlaylistDetailPage(
                playlist = target.playlist,
                tracks = target.tracks,
                onBack = onCloseDetail,
                onPlayAll = { onPlayPlaylist(target.playlist) },
                onPinShortcut = onPinPlaylistShortcut?.let { pin -> { play -> pin(target.playlist, play) } },
                onWriteNfc = onWritePlaylistNfc?.takeIf { target.playlist.source == LibrarySource.MediaStore.id }?.let { write -> { write(target.playlist) } },
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
                management = playlistManagement,
                experienceActions = experienceActions,
                onCopyCreated = onOpenPlaylist,
                onExport = { name -> onExportM3uPlaylist(target.playlist.copy(name = name)) },
                onEnqueuePlaylist = onEnqueuePlaylist?.let { enqueue -> { name -> enqueue(target.playlist.copy(name = name)) } },
                onSharePlaylist = onSharePlaylist?.let { share -> { name -> share(target.playlist.copy(name = name)) } },
                onUpdateTrackMetadata = onUpdateTrackMetadata,
                onImportLyrics = onImportLyricsForTrack,
                onPickArtwork = onPickTrackArtwork,
                onAddToPlaylist = { track -> addToPlaylistTrack = track },
                onPlayNext = playNext,
                onEnqueue = enqueueTrack,
                onOpenArtist = onOpenTrackArtist,
                onOpenAlbum = onOpenTrackAlbum,
                modifier = Modifier.fillMaxSize(),
            )
            is LibraryDetailTransitionTarget.LinkedAlbum -> {
                val linkedState by linkedLibraryState.collectAsState()
                val remoteAlbumId = EchoLinkLibraryQueryPolicy.remoteAlbumId(target.album.albumKey)
                val tracks = if (remoteAlbumId != null) {
                    linkedState.albumTracks[remoteAlbumId].orEmpty()
                } else {
                    linkedState.tracks.filter { it.linkedAlbumKey() == target.album.albumKey }
                }
                LinkedAlbumTracksPage(
                    album = target.album,
                    tracks = tracks,
                    isLoading = remoteAlbumId != null && linkedState.loadingAlbumId == remoteAlbumId,
                    onBack = { selectedLinkedAlbum = null },
                    onPlayLinkedQueue = onPlayLinkedQueue,
                    onOpenArtist = {
                        val name = target.album.albumArtist ?: target.album.artist ?: return@LinkedAlbumTracksPage
                        selectedLinkedAlbum = null
                        selectedLinkedArtist = linkedArtistSummary(name, target.album.artworkUri)
                    },
                    onOpenRemoteArtist = { remote ->
                        selectedLinkedAlbum = null
                        selectedLinkedArtist = remote.toLinkedArtistSummary()
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            is LibraryDetailTransitionTarget.LinkedArtist -> {
                val linkedState by linkedLibraryState.collectAsState()
                Column(Modifier.fillMaxSize()) {
                    LinkedArtistPagingControls(
                        linkedState.tracks.size, linkedState.totalCount, linkedState.isLoadingMore,
                        onLoadMore = {
                            if (linkedState.tracks.size >= linkedState.totalCount) {
                                onRefreshLinkedLibrary(linkedState.query, EchoLibraryCategory.Artists)
                            }
                            else onLoadMoreLinkedTracks()
                        },
                        error = linkedState.error,
                    )
                    LinkedArtistTracksPage(
                        artist = target.artist,
                        tracks = linkedState.tracks.filter { it.linkedArtistKey() == target.artist.artistKey },
                        onBack = { selectedLinkedArtist = null },
                        onPlayLinkedQueue = onPlayLinkedQueue,
                        onPlayLinkedQueueOnPc = onPlayLinkedQueueOnPc,
                        onOpenRemoteAlbum = { remote ->
                            selectedLinkedAlbum = remote.toLinkedAlbumSummary()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            is LibraryDetailTransitionTarget.LinkedPlaylist -> {
                val linkedState by linkedLibraryState.collectAsState()
                LinkedPlaylistTracksPage(
                    playlist = target.playlist,
                    tracks = linkedState.playlistTracks[target.playlist.id].orEmpty(),
                    isLoading = linkedState.loadingPlaylistId == target.playlist.id,
                    error = linkedState.error,
                    onBack = { selectedLinkedPlaylist = null },
                    onLoadArtwork = onLoadLinkedPlaylistArtwork,
                    onPlayLinkedQueue = onPlayLinkedQueue,
                    onPlayLinkedQueueOnPc = onPlayLinkedQueueOnPc,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            LibraryDetailTransitionTarget.Browser -> {
                if (splitDetails) LibrarySplitPlaceholder() else browserPane()
            }
        }
    }

    val detailRenderer: @Composable () -> Unit = {
        LibraryDetailTransition(
            targetState = detailTransitionTarget,
            split = splitDetails,
            outerNavigationOwnsTransition = outerNavigationOwnsTransition,
            back = if (detailReturnsToBrowser) {
                app.echo.android.design.EchoPageBack<LibraryDetailTransitionTarget>(
                    LibraryDetailTransitionTarget.Browser,
                ) { maxOf(detailBackProgress(), linkedBack.value) }
            } else null,
            modifier = Modifier.fillMaxSize(),
            content = { DetailContent(it) },
        )
    }
    val latestDetailRenderer = rememberUpdatedState(detailRenderer)
    val detailPane = remember { movableContentOf { latestDetailRenderer.value() } }

    if (splitDetails) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Row(Modifier.widthIn(max = 1440.dp).fillMaxSize()) {
                app.echo.android.design.EchoLayoutBounds(Modifier.weight(0.42f).fillMaxHeight()) { browserPane() }
                Box(Modifier.width(1.dp).fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)))
                app.echo.android.design.EchoLayoutBounds(Modifier.weight(0.58f).fillMaxHeight()) {
                    detailPane()
                }
            }
        }
    } else {
        detailPane()
    }

    addToPlaylistTrack?.let { track ->
        val memberships = remember(track.id, playlistManagement) {
            playlistManagement?.memberships?.invoke(track.id) ?: kotlinx.coroutines.flow.flowOf(emptySet<String>())
        }.collectAsState(initial = emptySet()).value
        AddToPlaylistDialog(
            playlists = playlists,
            containingPlaylistIds = memberships,
            fileOnly = track.source == app.echo.android.model.library.LibrarySource.FilePlaylist,
            allowFilePlaylists = track.source.isLocalAudioFile,
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
private fun LinkedLibraryRefreshAction(
    state: StateFlow<EchoRemoteLibraryState>,
    onRefresh: () -> Unit,
) {
    val linkedState by state.collectAsState()
    IconButton(
        onClick = onRefresh,
        enabled = !linkedState.isLoading,
        modifier = Modifier.size(LibraryToolbarActionSize),
    ) {
        EchoIcon(
            Icons.Rounded.Refresh,
            contentDescription = stringResource(L10nR.string.feature_library_refresh_pc_echo_library_5b6275),
        )
    }
}

@Composable
private fun LinkedEchoLibraryPage(
    state: EchoRemoteLibraryState,
    libraryOnly: Boolean,
    query: String,
    selectedMode: LinkedLibraryMode,
    modes: List<LinkedLibraryMode>,
    onCustomize: (() -> Unit)?,
    selectedSource: LibrarySourceMode,
    selectedSortMode: LibraryTrackSortMode,
    albumSortMode: AlbumSortMode,
    artistSortMode: ArtistSortMode,
    folderSortMode: FolderSortMode,
    showTrackAudioInfoTags: Boolean,
    onSortModeChange: (LibraryTrackSortMode) -> Unit,
    onAlbumSortModeChange: (AlbumSortMode) -> Unit,
    onArtistSortModeChange: (ArtistSortMode) -> Unit,
    onFolderSortModeChange: (FolderSortMode) -> Unit,
    onSelectMode: (LinkedLibraryMode) -> Unit,
    onOpenAlbum: (AlbumSummary) -> Unit,
    onOpenArtist: (ArtistSummary) -> Unit,
    onOpenPlaylist: (EchoRemotePlaylist) -> Unit,
    onLoadPlaylistArtwork: suspend (EchoRemotePlaylist) -> String?,
    onRefresh: (String) -> Unit,
    onPrioritize: (EchoLibraryCategory) -> Unit,
    onRefreshFolders: (String) -> Unit,
    onLoadMoreTracks: () -> Unit,
    onLoadMoreAlbums: () -> Unit,
    onLoadMorePlaylists: () -> Unit,
    onChangeFolderPage: (Int) -> Unit,
    onPlayLinkedQueue: (List<EchoRemoteTrack>, Int) -> Unit,
    onPlayLinkedQueueOnPc: ((List<EchoRemoteTrack>, Int) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val tracks = state.tracks
    val playlists = state.playlists
    val normalizedQuery = remember(query) { query.trim() }
    val remoteQuery = remember(state.query) { state.query.trim() }
    val pager = remember(modes) {
        PagerState(currentPage = modes.indexOf(selectedMode).coerceAtLeast(0)) { modes.size }
    }
    val searchText = remember { LinkedLibrarySearchTextCache() }
    val randomOrder = remember(selectedSource, normalizedQuery) { LinkedLibraryRandomOrder() }
    val interactive = LocalEchoPageInteractive.current

    LaunchedEffect(interactive, selectedMode) {
        if (interactive) onPrioritize(selectedMode.category)
    }

    LaunchedEffect(interactive, selectedMode, normalizedQuery, remoteQuery) {
        if (!interactive) return@LaunchedEffect
        val queryToSend = EchoLinkLibraryQueryPolicy.remoteSearchQuery(normalizedQuery)
        if (queryToSend != remoteQuery) {
            if (queryToSend.isNotEmpty()) delay(150L)
            onRefresh(queryToSend)
        }
    }

    LaunchedEffect(interactive) {
        if (interactive && normalizedQuery.isEmpty() && remoteQuery.isEmpty() && !state.isLoading &&
            state.error.isNullOrBlank() && tracks.isEmpty() && state.totalCount == 0
        ) {
            onRefresh("")
        }
    }

    Column(modifier.fillMaxSize()) {
        LinkedLibraryHeader(
            selectedMode = selectedMode,
            modes = modes,
            pager = pager,
            onSelectMode = onSelectMode,
            onCustomize = onCustomize,
            selectedSortMode = selectedSortMode,
            albumSortMode = albumSortMode,
            artistSortMode = artistSortMode,
            folderSortMode = folderSortMode,
            onSortModeChange = onSortModeChange,
            onAlbumSortModeChange = onAlbumSortModeChange,
            onArtistSortModeChange = onArtistSortModeChange,
            onFolderSortModeChange = onFolderSortModeChange,
        )
        LibraryCategoryPager(
            pager = pager,
            modes = modes,
            selectedMode = selectedMode,
            onSelectMode = onSelectMode,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { mode ->
            val catalog = rememberLinkedLibraryCatalog(
                mode, state, normalizedQuery, remoteQuery, selectedSortMode,
                albumSortMode, artistSortMode, searchText, randomOrder,
            )
            val sortedTracks = catalog.tracks
            val albums = catalog.albums
            val artists = catalog.artists
            val filteredPlaylists = catalog.playlists
            // The next page can be laid out during a drag. Wait for selection before fetching.
            val canLoadMore = interactive && mode == selectedMode
            Column(Modifier.fillMaxSize()) {
                state.error?.takeIf { it.isNotBlank() && mode != LinkedLibraryMode.Artists }?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
                if (state.isLoadingMore && mode == LinkedLibraryMode.Songs) {
                    Text(
                        text = stringResource(L10nR.string.feature_library_loading_more_from_pc_echo_tracks_size_state_e85d63, (tracks.size).toString(), (state.totalCount).toString()),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
                if (mode == LinkedLibraryMode.Artists && catalog !== LinkedLibraryCatalog.Empty) {
                    LinkedArtistPagingControls(
                        tracks.size, state.totalCount, state.isLoadingMore,
                        onLoadMore = {
                            if (tracks.size >= state.totalCount) onRefresh(normalizedQuery) else onLoadMoreTracks()
                        },
                        error = state.error,
                    )
                }
                when {
                    (mode != LinkedLibraryMode.Folders && catalog === LinkedLibraryCatalog.Empty) ||
                        (state.isLoading && tracks.isEmpty() && albums.isEmpty() && playlists.isEmpty()) -> LibraryCollectionEmpty(
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
                    mode == LinkedLibraryMode.Folders && state.folders.isEmpty() && state.folderTracks.isEmpty() &&
                        state.folderPath.isBlank() && state.folderPageCount == 1 -> {
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
                        onPlayLinkedQueue = onPlayLinkedQueue,
                        onPlayOnPc = onPlayLinkedQueueOnPc,
                        showAudioInfoTags = showTrackAudioInfoTags,
                        loadedCount = state.tracks.size,
                        totalCount = state.totalCount,
                        isLoadingMore = state.isLoadingMore || !canLoadMore,
                        onLoadMore = { if (state.error.isNullOrBlank() && normalizedQuery == remoteQuery) onLoadMoreTracks() },
                        modifier = Modifier.fillMaxSize(),
                    )
                    mode == LinkedLibraryMode.Albums -> LinkedAlbumWall(
                        albums = albums,
                        onOpenAlbum = onOpenAlbum,
                        loadedCount = state.albums.size,
                        totalCount = state.albumTotalCount,
                        isLoadingMore = state.isLoadingMore || !canLoadMore,
                        onLoadMore = { if (state.error.isNullOrBlank() && normalizedQuery == remoteQuery) onLoadMoreAlbums() },
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
                        onLoadArtwork = onLoadPlaylistArtwork,
                        isActive = canLoadMore && normalizedQuery == remoteQuery,
                        loadedCount = state.playlists.size,
                        totalCount = state.playlistTotalCount,
                        isLoadingMore = state.isLoadingMore,
                        hasError = !state.error.isNullOrBlank(),
                        onLoadMore = onLoadMorePlaylists,
                        modifier = Modifier.fillMaxSize(),
                    )
                    mode == LinkedLibraryMode.Folders -> LinkedFolderBrowser(
                        state = state,
                        libraryOnly = libraryOnly,
                        sortMode = folderSortMode,
                        onOpenFolder = onRefreshFolders,
                        onChangePage = onChangeFolderPage,
                        onPlayLinkedQueue = onPlayLinkedQueue,
                        onPlayLinkedQueueOnPc = onPlayLinkedQueueOnPc,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun LinkedLibraryHeader(
    selectedMode: LinkedLibraryMode,
    modes: List<LinkedLibraryMode>,
    pager: PagerState,
    onSelectMode: (LinkedLibraryMode) -> Unit,
    onCustomize: (() -> Unit)?,
    selectedSortMode: LibraryTrackSortMode,
    albumSortMode: AlbumSortMode,
    artistSortMode: ArtistSortMode,
    folderSortMode: FolderSortMode,
    onSortModeChange: (LibraryTrackSortMode) -> Unit,
    onAlbumSortModeChange: (AlbumSortMode) -> Unit,
    onArtistSortModeChange: (ArtistSortMode) -> Unit,
    onFolderSortModeChange: (FolderSortMode) -> Unit,
) {
    LibraryCategoryBar(tabs = {
        LibraryTextTabs(
            labels = modes.map { it.label() },
            selectedIndex = modes.indexOf(selectedMode).coerceAtLeast(0),
            onSelect = { onSelectMode(modes[it]) },
            pager = pager,
            inBrowserHeader = true,
        )
    }) {
        when (selectedMode) {
            LinkedLibraryMode.Songs -> LibraryTrackSortMenu(linkedTrackSort(selectedSortMode), onSortModeChange, onCustomize, linked = true)
            LinkedLibraryMode.Albums -> LibraryAlbumSortMenu(linkedAlbumSort(albumSortMode), onAlbumSortModeChange, onCustomize, linked = true)
            LinkedLibraryMode.Artists -> LibraryArtistSortMenu(artistSortMode, onArtistSortModeChange, onCustomize, linked = true)
            LinkedLibraryMode.Folders -> LibraryFolderSortMenu(folderSortMode, onFolderSortModeChange, onCustomize, linked = true)
            LinkedLibraryMode.Playlists -> LibraryCustomizeButton(onCustomize)
        }
    }
}


@Composable
private fun LinkedTrackList(
    tracks: List<EchoRemoteTrack>,
    onPlayLinkedQueue: (List<EchoRemoteTrack>, Int) -> Unit,
    onPlayOnPc: ((List<EchoRemoteTrack>, Int) -> Unit)? = null,
    showAudioInfoTags: Boolean,
    modifier: Modifier = Modifier,
    loadedCount: Int = tracks.size,
    totalCount: Int = tracks.size,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    itemKey: (Int, EchoRemoteTrack) -> String = { _, track ->
        track.id ?: "${track.title}-${track.artist}-${track.album.orEmpty()}"
    },
) {
    val listState = rememberLazyListState()
    val requestTotal = if (tracks.size < loadedCount) tracks.size else totalCount
    LoadMoreWhenNearEnd(listState, tracks.size, requestTotal, isLoadingMore, onLoadMore)
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = LibraryBottomControlsPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(
            items = tracks,
            key = itemKey,
        ) { index, track ->
            TrackRow(
                track = track.toEchoTrack(),
                onClick = { onPlayLinkedQueue(tracks, index) },
                onPlayOnPc = onPlayOnPc?.let { playOnPc -> { playOnPc(tracks, index) } },
                showAudioInfoTags = showAudioInfoTags,
            )
        }
    }
}

@Composable
private fun LinkedPlaylistList(
    playlists: List<EchoRemotePlaylist>,
    onOpenPlaylist: (EchoRemotePlaylist) -> Unit,
    onLoadArtwork: suspend (EchoRemotePlaylist) -> String?,
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
    LoadMoreWhenNearEnd(listState, playlists.size, requestTotal, isLoadingMore || !isActive || hasError, onLoadMore)
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = LibraryBottomControlsPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
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
            border = BorderStroke(1.dp, if (dark) echoTheme().glassBorder else echoTheme().glassBorder),
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
private fun LinkedPlaylistTracksPage(
    playlist: EchoRemotePlaylist,
    tracks: List<EchoRemoteTrack>,
    isLoading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onLoadArtwork: suspend (EchoRemotePlaylist) -> String?,
    onPlayLinkedQueue: (List<EchoRemoteTrack>, Int) -> Unit,
    onPlayLinkedQueueOnPc: ((List<EchoRemoteTrack>, Int) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val artwork = rememberLinkedPlaylistArtwork(playlist, LocalEchoPageInteractive.current, onLoadArtwork)
    LibraryDetailFrame(
        actions = {
            IconButton(onClick = onBack) {
                EchoIcon(
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
                    artworkUri = artwork,
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
                        fontWeight = FontWeight.SemiBold,
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
                if (tracks.isNotEmpty() && onPlayLinkedQueueOnPc != null) {
                    TextButton(
                        onClick = { onPlayLinkedQueueOnPc(tracks, 0) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(L10nR.string.feature_library_play_on_pc_7c21a4))
                    }
                }
                LinkedTrackList(
                    tracks = tracks,
                    onPlayLinkedQueue = onPlayLinkedQueue,
                    onPlayOnPc = onPlayLinkedQueueOnPc,
                    showAudioInfoTags = false,
                    modifier = Modifier.weight(1f),
                    itemKey = { index, track -> "$index:${track.id}" },
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
    loadedCount: Int = albums.size,
    totalCount: Int = albums.size,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
) {
    AlbumSummaryWall(
        albums = albums,
        onOpenAlbum = onOpenAlbum,
        modifier = modifier,
        totalCount = if (albums.size < loadedCount) albums.size else totalCount.coerceAtLeast(loadedCount),
        isLoadingMore = isLoadingMore,
        onLoadMore = onLoadMore,
    )
}

@Composable
private fun LinkedArtistWall(
    artists: List<ArtistSummary>,
    onOpenArtist: (ArtistSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    ArtistSummaryWall(
        artists = artists,
        onOpenArtist = onOpenArtist,
        modifier = modifier,
    )
}

@Composable
private fun LinkedAlbumTracksPage(
    album: AlbumSummary,
    tracks: List<EchoRemoteTrack>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onPlayLinkedQueue: (List<EchoRemoteTrack>, Int) -> Unit,
    onOpenArtist: (() -> Unit)? = null,
    onOpenRemoteArtist: ((EchoRemoteTrack) -> Unit)? = null,
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
                    EchoIcon(
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
        onPlayTrack = { track ->
            val remote = remoteTracksByUiId[track.id] ?: return@AlbumDetailListPage
            val index = tracks.indexOfFirst { it.id == remote.id }.coerceAtLeast(0)
            onPlayLinkedQueue(tracks, index)
        },
        onOpenArtist = onOpenArtist,
        onOpenTrackArtist = { track ->
            val remote = remoteTracksByUiId[track.id] ?: return@AlbumDetailListPage
            onOpenRemoteArtist?.invoke(remote)
        },
        modifier = modifier,
    )
}

@Composable
private fun LinkedArtistTracksPage(
    artist: ArtistSummary,
    tracks: List<EchoRemoteTrack>,
    onBack: () -> Unit,
    onPlayLinkedQueue: (List<EchoRemoteTrack>, Int) -> Unit,
    onPlayLinkedQueueOnPc: ((List<EchoRemoteTrack>, Int) -> Unit)?,
    onOpenRemoteAlbum: ((EchoRemoteTrack) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val artistTracks = remember(tracks) { tracks.map { it.toEchoTrack() } }
    val remoteTracksByUiId = remember(tracks, artistTracks) {
        tracks.zip(artistTracks).associate { (remote, uiTrack) -> uiTrack.id to remote }
    }
    val loadedArtist = remember(artist, artistTracks) {
        artist.copy(
            trackCount = artistTracks.size,
            albumCount = tracks.map { it.linkedAlbumKey() }.distinct().size,
            durationMs = artistTracks.sumOf { it.durationMs.coerceAtLeast(0L) },
        )
    }
    ArtistDetailListPage(
        artist = loadedArtist,
        tracks = artistTracks,
        onBack = onBack,
        onPlayAll = { if (tracks.isNotEmpty()) onPlayLinkedQueue(tracks, 0) },
        onShuffle = {
            if (tracks.isNotEmpty()) onPlayLinkedQueue(tracks.shuffled(), 0)
        },
        onPlayOnPc = onPlayLinkedQueueOnPc?.let { playOnPc -> { if (tracks.isNotEmpty()) playOnPc(tracks, 0) } },
        onPlayTrack = { track ->
            val remote = remoteTracksByUiId[track.id] ?: return@ArtistDetailListPage
            val index = tracks.indexOfFirst { it.id == remote.id }.coerceAtLeast(0)
            onPlayLinkedQueue(tracks, index)
        },
        onOpenTrackAlbum = { track ->
            val remote = remoteTracksByUiId[track.id] ?: return@ArtistDetailListPage
            onOpenRemoteAlbum?.invoke(remote)
        },
        modifier = modifier,
    )
}

internal fun EchoRemoteTrack.toEchoTrack(): EchoTrack =
    EchoTrack(
        id = "echo-link:${id ?: "${title.hashCode()}-${artist.hashCode()}-${album.hashCode()}"}",
        uri = "",
        title = title,
        artist = artist.ifBlank { "PC ECHO" },
        album = album,
        albumArtist = artist.takeIf { it.isNotBlank() },
        artworkUri = artworkUrl,
        durationMs = durationMs,
        source = LibrarySource.EchoLink,
    )

private fun EchoRemoteTrack.toLinkedArtistSummary(): ArtistSummary =
    linkedArtistSummary(artist, artworkUrl)

private fun EchoRemoteTrack.toLinkedAlbumSummary(): AlbumSummary =
    AlbumSummary(
        albumKey = linkedAlbumKey(),
        title = album.orEmpty(),
        albumArtist = artist.takeIf { it.isNotBlank() },
        artist = artist.takeIf { it.isNotBlank() },
        artworkUri = artworkUrl,
        trackCount = 0,
        durationMs = durationMs.coerceAtLeast(0L),
        year = null,
    )

private fun linkedArtistSummary(name: String, artworkUri: String?): ArtistSummary {
    val trimmed = name.trim().ifBlank { "PC ECHO" }
    return ArtistSummary(
        artistKey = "echo-link:${trimmed.lowercase()}",
        name = trimmed,
        artworkUri = artworkUri,
        albumCount = 0,
        trackCount = 0,
        durationMs = 0L,
    )
}

@Composable
private fun linkedPlaylistSubtitle(playlist: EchoRemotePlaylist): String {
    val source = playlist.sourceLabel?.takeIf { it.isNotBlank() } ?: "PC ECHO"
    return stringResource(L10nR.string.feature_library_playlist_trackcount_tracks_source_3431c6, (playlist.trackCount).toString(), (source).toString())
}

@Composable
private fun LibraryTrackSortMenu(
    selectedSortMode: LibraryTrackSortMode,
    onSortModeChange: (LibraryTrackSortMode) -> Unit,
    onCustomize: (() -> Unit)? = null,
    linked: Boolean = false,
) {
    LibrarySortMenu(
        selected = selectedSortMode,
        options = if (linked) LinkedTrackSortOptions else LocalTrackSortOptions,
        section = if (linked) ({ 0 }) else ::trackSortSection,
        label = { it.label() },
        contentDescription = stringResource(L10nR.string.feature_library_set_track_sort_order_4053c0),
        onSelect = onSortModeChange,
        onCustomize = onCustomize,
        scopeNote = if (linked) stringResource(L10nR.string.library_sort_loaded_items) else null,
        showButtonBackground = !linked,
    )
}

@Composable
private fun LibraryFolderSortMenu(
    selectedSortMode: FolderSortMode,
    onSortModeChange: (FolderSortMode) -> Unit,
    onCustomize: (() -> Unit)? = null,
    linked: Boolean = false,
) {
    LibrarySortMenu(
        selected = selectedSortMode,
        options = FolderSortMode.entries,
        label = { it.label() },
        contentDescription = stringResource(L10nR.string.feature_library_set_folder_sort_order_9a4f33),
        onSelect = onSortModeChange,
        onCustomize = onCustomize,
        scopeNote = if (linked) stringResource(L10nR.string.library_sort_loaded_items) else null,
        showButtonBackground = !linked,
    )
}

@Composable
private fun LibraryBrowserSortAction(
    layout: EchoLibraryLayout,
    onLayoutChange: ((EchoLibraryLayout) -> Unit)?,
    selectedSource: LibrarySourceMode,
    selectedMode: LibraryViewMode,
    onCustomize: (() -> Unit)?,
    selectedSortMode: LibraryTrackSortMode,
    trackFilter: LibraryTrackFilter,
    lyricsIndexState: StateFlow<LibraryLyricsIndexState>?,
    onInspectLyrics: () -> Unit,
    onCancelLyricsInspection: () -> Unit,
    onTrackFilterChange: (LibraryTrackFilter) -> Unit,
    albumSortMode: AlbumSortMode,
    artistSortMode: ArtistSortMode,
    folderSortMode: FolderSortMode,
    playlistSortMode: PlaylistCatalogSort?,
    onSortModeChange: (LibraryTrackSortMode) -> Unit,
    onAlbumSortModeChange: (AlbumSortMode) -> Unit,
    onArtistSortModeChange: (ArtistSortMode) -> Unit,
    onFolderSortModeChange: (FolderSortMode) -> Unit,
    onPlaylistSortModeChange: (PlaylistCatalogSort) -> Unit,
) {
    when (selectedMode) {
        LibraryViewMode.Songs -> if (selectedSource == LibrarySourceMode.Local) {
            LibraryBrowseOptionsMenu(selectedSortMode, trackFilter, onSortModeChange, onTrackFilterChange,
                lyricsIndexState, onInspectLyrics, onCancelLyricsInspection, onCustomize, layout, onLayoutChange)
        }
        LibraryViewMode.Albums, LibraryViewMode.Cloud ->
            LibraryAlbumSortMenu(albumSortMode, onAlbumSortModeChange, onCustomize)
        LibraryViewMode.Artists -> LibraryArtistSortMenu(
            selectedSortMode = artistSortMode,
            onSortModeChange = onArtistSortModeChange,
            onCustomize = onCustomize,
            showAlbumArtists = layout.showAlbumArtists,
            onShowAlbumArtistsChange = onLayoutChange?.let { updateLayout ->
                { showAlbumArtists -> updateLayout(layout.copy(showAlbumArtists = showAlbumArtists)) }
            },
        )
        LibraryViewMode.Folders -> LibraryFolderSortMenu(folderSortMode, onFolderSortModeChange, onCustomize)
        LibraryViewMode.Playlists -> if (playlistSortMode != null) {
            PlaylistCatalogSortMenu(playlistSortMode, onPlaylistSortModeChange, onCustomize)
        } else {
            LibraryCustomizeButton(onCustomize)
        }
        LibraryViewMode.Genres, LibraryViewMode.Composers, LibraryViewMode.Radio -> Unit
    }
}
