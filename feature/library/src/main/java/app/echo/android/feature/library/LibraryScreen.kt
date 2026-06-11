package app.echo.android.feature.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Devices
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.paging.compose.LazyPagingItems
import app.echo.android.design.ArtworkTile
import app.echo.android.design.EchoAccent
import app.echo.android.design.EchoAccentDeep
import app.echo.android.design.EchoAccentText
import app.echo.android.design.EchoContentMaxWidth
import app.echo.android.design.EchoColors
import app.echo.android.design.EchoGlassBorder
import app.echo.android.design.EchoHomeBlue
import app.echo.android.design.EchoHomeMist
import app.echo.android.design.EchoIconBadge
import app.echo.android.design.EchoPanel
import app.echo.android.design.EmptyState
import app.echo.android.design.PageChrome
import app.echo.android.design.RoonInk
import app.echo.android.design.RoonMuted
import app.echo.android.design.formatDuration
import app.echo.android.model.connect.EchoRemoteLibraryState
import app.echo.android.model.connect.EchoRemotePlaylist
import app.echo.android.model.connect.EchoRemoteTrack
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.ArtistSummary
import app.echo.android.model.library.EchoPlaylist
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.EchoTrackMetadataUpdate
import app.echo.android.model.library.FolderSummary
import app.echo.android.model.library.LibraryScanProgress
import app.echo.android.model.library.LibrarySource
import app.echo.android.model.library.LibraryTrackSortMode
import app.echo.android.model.library.NeteaseAccountState
import app.echo.android.model.library.NeteaseAudioQuality
import app.echo.android.model.library.NeteaseImportState
import kotlinx.coroutines.delay

private val LibraryFolderMotionEasing = CubicBezierEasing(0.16f, 1f, 0.30f, 1f)

internal enum class LibraryViewMode(
    val label: String,
    val icon: ImageVector,
) {
    Songs("歌曲", Icons.AutoMirrored.Rounded.QueueMusic),
    Folders("文件夹", Icons.Rounded.LibraryMusic),
    Albums("专辑", Icons.Rounded.LibraryMusic),
    Artists("艺术家", Icons.Rounded.Person),
    Cloud("网盘", Icons.Rounded.CloudQueue),
    Playlists("歌单", Icons.Rounded.LibraryMusic),
}

private enum class LinkedLibraryMode(
    val label: String,
    val icon: ImageVector,
) {
    Songs("歌曲", Icons.AutoMirrored.Rounded.QueueMusic),
    Albums("专辑", Icons.Rounded.LibraryMusic),
    Artists("艺术家", Icons.Rounded.Person),
    Playlists("歌单", Icons.Rounded.LibraryMusic),
}

private enum class LibrarySourceMode(
    val id: String,
    val label: String,
    val icon: ImageVector,
) {
    Local(LibrarySourceIds.Local, "本地", Icons.Rounded.LibraryMusic),
    PcEcho(LibrarySourceIds.PcEcho, "PC ECHO", Icons.Rounded.Devices),
    Cloud(LibrarySourceIds.Cloud, "网盘", Icons.Rounded.CloudQueue),
}

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
}

private sealed interface LibraryFolderTransitionTarget {
    object Browser : LibraryFolderTransitionTarget

    data class Detail(
        val folder: FolderSummary,
        val tracks: LazyPagingItems<EchoTrack>,
    ) : LibraryFolderTransitionTarget
}

@Composable
fun LibraryScreen(
    hasPermission: Boolean,
    scanState: LibraryScanProgress,
    libraryQuery: String,
    trackSortMode: LibraryTrackSortMode,
    tracks: LazyPagingItems<EchoTrack>,
    albums: LazyPagingItems<AlbumSummary>,
    remoteAlbums: LazyPagingItems<AlbumSummary>,
    linkedLibraryActive: Boolean,
    linkedLibraryAvailable: Boolean,
    linkedLibraryState: EchoRemoteLibraryState,
    selectedLibrarySourceId: String,
    artists: LazyPagingItems<ArtistSummary>,
    folders: LazyPagingItems<FolderSummary>,
    playlists: List<EchoPlaylist>,
    neteaseAccountState: NeteaseAccountState,
    neteaseImportState: NeteaseImportState,
    neteaseQuality: NeteaseAudioQuality,
    showTrackAudioInfoTags: Boolean,
    selectedAlbum: AlbumSummary?,
    selectedArtist: ArtistSummary?,
    selectedFolder: FolderSummary?,
    selectedPlaylist: EchoPlaylist?,
    albumDetailTracks: LazyPagingItems<EchoTrack>?,
    artistDetailTracks: LazyPagingItems<EchoTrack>?,
    folderDetailTracks: LazyPagingItems<EchoTrack>?,
    playlistDetailTracks: LazyPagingItems<EchoTrack>?,
    onRequestPermission: () -> Unit,
    onLibraryQueryChange: (String) -> Unit,
    onLibrarySourceChange: (String) -> Unit,
    onTrackSortModeChange: (LibraryTrackSortMode) -> Unit,
    onScanFolder: () -> Unit,
    onScanAll: () -> Unit,
    onCancelScan: () -> Unit,
    onRefreshLinkedLibrary: (String) -> Unit,
    onOpenLinkedPlaylist: (EchoRemotePlaylist) -> Unit,
    onPlayLinkedTrack: (EchoRemoteTrack) -> Unit,
    onPlayTrack: (EchoTrack) -> Unit,
    onUpdateTrackMetadata: (EchoTrackMetadataUpdate) -> Unit,
    onImportLyricsForTrack: (EchoTrack) -> Unit,
    onPickTrackArtwork: (EchoTrack) -> Unit,
    onApplyNeteaseMetadata: (EchoTrack) -> Unit,
    onPlayAlbum: (AlbumSummary) -> Unit,
    onShuffleAlbum: (AlbumSummary) -> Unit,
    onPlayArtist: (ArtistSummary) -> Unit,
    onShuffleArtist: (ArtistSummary) -> Unit,
    onPlayFolder: (FolderSummary) -> Unit,
    onPlayPlaylist: (EchoPlaylist) -> Unit,
    onOpenAlbum: (AlbumSummary) -> Unit,
    onOpenArtist: (ArtistSummary) -> Unit,
    onOpenFolder: (FolderSummary) -> Unit,
    onOpenPlaylist: (EchoPlaylist) -> Unit,
    onLoginNeteaseByPhone: (String, String) -> Unit,
    onLoginNeteaseWithCookie: (String) -> Unit,
    onLogoutNetease: () -> Unit,
    onRefreshNeteasePlaylists: () -> Unit,
    onOpenNeteaseApp: () -> Unit,
    onNeteaseQualityChange: (NeteaseAudioQuality) -> Unit,
    onImportNeteasePlaylist: (Long) -> Unit,
    onCloseDetail: () -> Unit,
) {
    var selectedModeIndex by remember { mutableIntStateOf(LibraryViewMode.Songs.ordinal) }
    val selectedMode = LibraryViewMode.entries[selectedModeIndex]
    var selectedSource by remember(selectedLibrarySourceId, linkedLibraryActive) {
        mutableStateOf(librarySourceModeFromId(selectedLibrarySourceId, linkedLibraryActive))
    }
    var linkedMode by remember { mutableStateOf(LinkedLibraryMode.Songs) }
    var selectedLinkedAlbumKey by remember { mutableStateOf<String?>(null) }
    var selectedLinkedArtistKey by remember { mutableStateOf<String?>(null) }
    var selectedLinkedPlaylistId by remember { mutableStateOf<String?>(null) }
    val songListState = rememberLazyListState()
    val showInitialTrackLoading = tracks.itemCount == 0 && tracks.loadState.refresh is LoadState.Loading
    val showInitialTrackError = tracks.itemCount == 0 && tracks.loadState.refresh is LoadState.Error

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
        LinkedEchoLibraryPage(
            state = linkedLibraryState,
            query = libraryQuery,
            selectedMode = linkedMode,
            selectedAlbumKey = selectedLinkedAlbumKey,
            selectedSource = selectedSource,
            selectedSortMode = trackSortMode,
            showTrackAudioInfoTags = showTrackAudioInfoTags,
            selectedPlaylistId = selectedLinkedPlaylistId,
            onQueryChange = onLibraryQueryChange,
            onSelectSource = ::selectSource,
            onSortModeChange = onTrackSortModeChange,
            onSelectMode = { mode ->
                linkedMode = mode
                selectedLinkedAlbumKey = null
                selectedLinkedArtistKey = null
                selectedLinkedPlaylistId = null
            },
            onOpenAlbum = { album -> selectedLinkedAlbumKey = album.albumKey },
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
            onPlayLinkedTrack = onPlayLinkedTrack,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    if (selectedSource == LibrarySourceMode.PcEcho) {
        PageChrome(
            title = "曲库",
            subtitle = "PC ECHO",
            badge = selectedSource.label,
            showBrand = false,
            compactHeader = true,
            badgeContent = {},
            actions = {
                LibrarySearchBar(
                    query = libraryQuery,
                    onQueryChange = onLibraryQueryChange,
                    expandedWidth = 240.dp,
                )
                IconButton(onClick = { onRefreshLinkedLibrary(libraryQuery) }) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = "刷新 PC ECHO 曲库",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        ) {
            LibrarySourceStrip(
                selectedSource = selectedSource,
                linkedLibraryAvailable = linkedLibraryAvailable,
                onSelectSource = ::selectSource,
            )
            EmptyState("先到“互联”连接 PC ECHO，然后这里就能浏览 PC 曲库。")
        }
        return
    }

    // 详情页走全屏沉浸式页面，不套用曲库的 PageChrome
    val activeAlbumDetail = selectedAlbum
    val activeArtistDetail = selectedArtist
    val detailTransitionTarget = when {
        activeAlbumDetail != null && albumDetailTracks != null ->
            LibraryDetailTransitionTarget.AlbumDetail(activeAlbumDetail, albumDetailTracks)
        activeArtistDetail != null && artistDetailTracks != null ->
            LibraryDetailTransitionTarget.ArtistDetail(activeArtistDetail, artistDetailTracks)
        else -> LibraryDetailTransitionTarget.Browser
    }

    AnimatedContent(
        targetState = detailTransitionTarget,
        transitionSpec = {
            if (targetState != LibraryDetailTransitionTarget.Browser) {
                val enter = slideInHorizontally(tween(durationMillis = 380, easing = LibraryFolderMotionEasing)) { it / 4 } +
                    fadeIn(tween(durationMillis = 220, easing = LibraryFolderMotionEasing)) +
                    scaleIn(
                        initialScale = 0.985f,
                        animationSpec = tween(durationMillis = 380, easing = LibraryFolderMotionEasing),
                    )
                val exit = slideOutHorizontally(tween(durationMillis = 240, easing = LibraryFolderMotionEasing)) { -it / 10 } +
                    fadeOut(tween(durationMillis = 150, easing = LibraryFolderMotionEasing)) +
                    scaleOut(
                        targetScale = 0.992f,
                        animationSpec = tween(durationMillis = 240, easing = LibraryFolderMotionEasing),
                    )
                enter togetherWith exit
            } else {
                val enter = slideInHorizontally(tween(durationMillis = 300, delayMillis = 35, easing = LibraryFolderMotionEasing)) { -it / 7 } +
                    fadeIn(tween(durationMillis = 190, delayMillis = 55, easing = LibraryFolderMotionEasing)) +
                    scaleIn(
                        initialScale = 0.992f,
                        animationSpec = tween(durationMillis = 300, delayMillis = 35, easing = LibraryFolderMotionEasing),
                    )
                val exit = slideOutHorizontally(tween(durationMillis = 320, easing = LibraryFolderMotionEasing)) { it / 3 } +
                    fadeOut(tween(durationMillis = 180, easing = LibraryFolderMotionEasing)) +
                    scaleOut(
                        targetScale = 0.985f,
                        animationSpec = tween(durationMillis = 320, easing = LibraryFolderMotionEasing),
                    )
                enter togetherWith exit
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
                onPlayTrack = onPlayTrack,
                onUpdateTrackMetadata = onUpdateTrackMetadata,
                onImportLyrics = onImportLyricsForTrack,
                onPickArtwork = onPickTrackArtwork,
                onMatchNeteaseMetadata = onApplyNeteaseMetadata,
                modifier = Modifier.fillMaxSize(),
            )
            is LibraryDetailTransitionTarget.ArtistDetail -> ArtistDetailPage(
                artist = target.artist,
                tracks = target.tracks,
                onBack = onCloseDetail,
                onPlayAll = { onPlayArtist(target.artist) },
                onShuffle = { onShuffleArtist(target.artist) },
                onPlayTrack = onPlayTrack,
                onUpdateTrackMetadata = onUpdateTrackMetadata,
                onImportLyrics = onImportLyricsForTrack,
                onPickArtwork = onPickTrackArtwork,
                onMatchNeteaseMetadata = onApplyNeteaseMetadata,
                modifier = Modifier.fillMaxSize(),
            )
            LibraryDetailTransitionTarget.Browser -> {

                val activeFolderDetail = selectedFolder
                val activePlaylistDetail = selectedPlaylist
                val folderTransitionTarget =
                    if (activeFolderDetail != null && folderDetailTracks != null) {
                        LibraryFolderTransitionTarget.Detail(activeFolderDetail, folderDetailTracks)
                    } else {
                        LibraryFolderTransitionTarget.Browser
                    }
                if (activePlaylistDetail != null && playlistDetailTracks != null) {
                    LibraryDetailPage(
                        title = activePlaylistDetail.name,
                        subtitle = "${activePlaylistDetail.trackCount} 首 · 本地歌单",
                        tracks = playlistDetailTracks,
                        onBack = onCloseDetail,
                        onPlayAll = { onPlayPlaylist(activePlaylistDetail) },
                        onPlayTrack = onPlayTrack,
                        onUpdateTrackMetadata = onUpdateTrackMetadata,
                        onImportLyrics = onImportLyricsForTrack,
                        onPickArtwork = onPickTrackArtwork,
                        onMatchNeteaseMetadata = onApplyNeteaseMetadata,
                        showAudioInfoTags = showTrackAudioInfoTags,
                        modifier = Modifier.fillMaxSize(),
                    )
                    return@AnimatedContent
                }

                AnimatedContent(
                    targetState = folderTransitionTarget,
                    contentKey = { target ->
                        when (target) {
                            LibraryFolderTransitionTarget.Browser -> "folder-browser"
                            is LibraryFolderTransitionTarget.Detail -> target.folder.folderKey
                        }
                    },
                    transitionSpec = {
                        if (targetState is LibraryFolderTransitionTarget.Detail) {
                            val enter = slideInHorizontally(
                                tween(durationMillis = 300, delayMillis = 24, easing = LibraryFolderMotionEasing),
                            ) { it / 7 } +
                                fadeIn(tween(durationMillis = 180, delayMillis = 36, easing = LibraryFolderMotionEasing))
                            val exit = slideOutHorizontally(
                                tween(durationMillis = 180, easing = LibraryFolderMotionEasing),
                            ) { -it / 28 } +
                                fadeOut(tween(durationMillis = 110, easing = LibraryFolderMotionEasing))
                            (enter togetherWith exit).apply {
                                targetContentZIndex = 1f
                            }
                        } else {
                            val enter = slideInHorizontally(
                                tween(durationMillis = 180, delayMillis = 36, easing = LibraryFolderMotionEasing),
                            ) { -it / 30 } +
                                fadeIn(tween(durationMillis = 150, delayMillis = 30, easing = LibraryFolderMotionEasing))
                            val exit = slideOutHorizontally(
                                tween(durationMillis = 190, easing = LibraryFolderMotionEasing),
                            ) { it / 7 } +
                                fadeOut(tween(durationMillis = 120, easing = LibraryFolderMotionEasing))
                            (enter togetherWith exit).apply {
                                targetContentZIndex = -1f
                            }
                        }
                    },
                    label = "library-folder-detail-transition",
                    modifier = Modifier.fillMaxSize(),
                ) { target ->
                    when (target) {
                        is LibraryFolderTransitionTarget.Detail -> FolderDetailPage(
                            folder = target.folder,
                            tracks = target.tracks,
                            onBack = onCloseDetail,
                            onPlayAll = { onPlayFolder(target.folder) },
                            onPlayTrack = onPlayTrack,
                            onUpdateTrackMetadata = onUpdateTrackMetadata,
                            onImportLyrics = onImportLyricsForTrack,
                            onPickArtwork = onPickTrackArtwork,
                            onMatchNeteaseMetadata = onApplyNeteaseMetadata,
                            modifier = Modifier.fillMaxSize(),
                        )
                        LibraryFolderTransitionTarget.Browser -> PageChrome(
                            title = "曲库",
                            subtitle = null,
                            badge = selectedSource.label,
                            showBrand = false,
                            compactHeader = true,
                            badgeContent = {},
                            titleContent = {},
                            actions = {
                                LibrarySearchBar(
                                    query = libraryQuery,
                                    onQueryChange = onLibraryQueryChange,
                                    expandedWidth = 240.dp,
                                )
                                if (selectedSource == LibrarySourceMode.Local && selectedMode == LibraryViewMode.Songs) {
                                    LibraryTrackSortMenu(
                                        selectedSortMode = trackSortMode,
                                        onSortModeChange = onTrackSortModeChange,
                                    )
                                }
                                LibrarySourceScanButton(
                                    selectedSource = selectedSource,
                                    linkedLibraryAvailable = linkedLibraryAvailable,
                                    onSelectSource = ::selectSource,
                                    hasPermission = hasPermission,
                                    scanState = scanState,
                                    onRequestPermission = onRequestPermission,
                                    onScanFolder = onScanFolder,
                                    onScanAll = onScanAll,
                                    onCancelScan = onCancelScan,
                                )
                            },
                        ) {
                            when {
                                scanState.isScanning -> LibraryScanStatus(scanState = scanState, onCancelScan = onCancelScan)
                                showInitialTrackLoading -> {
                                    LibraryBrowserHeader(
                                        scanState = scanState,
                                        selectedSource = selectedSource,
                                        linkedLibraryAvailable = linkedLibraryAvailable,
                                        onSelectSource = ::selectSource,
                                        selectedMode = selectedMode,
                                        selectedSortMode = trackSortMode,
                                        onSelectMode = { mode ->
                                            selectedModeIndex = mode.ordinal
                                            if (selectedSource == LibrarySourceMode.Cloud && mode != LibraryViewMode.Albums) {
                                                selectedSource = LibrarySourceMode.Local
                                                onLibrarySourceChange(LibrarySourceMode.Local.id)
                                            }
                                        },
                                        onSortModeChange = onTrackSortModeChange,
                                    )
                                    EmptyState("正在加载曲库...")
                                }
                                showInitialTrackError -> {
                                    LibraryBrowserHeader(
                                        scanState = scanState,
                                        selectedSource = selectedSource,
                                        linkedLibraryAvailable = linkedLibraryAvailable,
                                        onSelectSource = ::selectSource,
                                        selectedMode = selectedMode,
                                        selectedSortMode = trackSortMode,
                                        onSelectMode = { mode ->
                                            selectedModeIndex = mode.ordinal
                                            if (selectedSource == LibrarySourceMode.Cloud && mode != LibraryViewMode.Albums) {
                                                selectedSource = LibrarySourceMode.Local
                                                onLibrarySourceChange(LibrarySourceMode.Local.id)
                                            }
                                        },
                                        onSortModeChange = onTrackSortModeChange,
                                    )
                                    EmptyState("曲库查询失败。")
                                }
                                else -> {
                                    LibraryBrowserHeader(
                                        scanState = scanState,
                                        selectedSource = selectedSource,
                                        linkedLibraryAvailable = linkedLibraryAvailable,
                                        onSelectSource = ::selectSource,
                                        selectedMode = selectedMode,
                                        selectedSortMode = trackSortMode,
                                        onSelectMode = { mode ->
                                            selectedModeIndex = mode.ordinal
                                            if (selectedSource == LibrarySourceMode.Cloud && mode != LibraryViewMode.Albums) {
                                                selectedSource = LibrarySourceMode.Local
                                                onLibrarySourceChange(LibrarySourceMode.Local.id)
                                            }
                                        },
                                        onSortModeChange = onTrackSortModeChange,
                                    )
                                    Box(modifier = Modifier.weight(1f)) {
                                        when (selectedMode) {
                                            LibraryViewMode.Songs -> {
                                                if (!hasPermission) {
                                                    EmptyState("授权后即可索引本地音乐；云端曲库可直接进入“网盘”页。")
                                                } else if (tracks.itemCount == 0) {
                                                    LibraryBootstrapState()
                                                } else {
                                                    TrackList(
                                                        tracks = tracks,
                                                        onPlayTrack = onPlayTrack,
                                                        onUpdateTrackMetadata = onUpdateTrackMetadata,
                                                        onImportLyrics = onImportLyricsForTrack,
                                                        onPickArtwork = onPickTrackArtwork,
                                                        onMatchNeteaseMetadata = onApplyNeteaseMetadata,
                                                        showAudioInfoTags = showTrackAudioInfoTags,
                                                        listState = songListState,
                                                        modifier = Modifier.fillMaxSize(),
                                                    )
                                                }
                                            }

                                            LibraryViewMode.Folders -> FolderList(
                                                folders = folders,
                                                onOpenFolder = onOpenFolder,
                                                modifier = Modifier.fillMaxSize(),
                                            )

                                            LibraryViewMode.Albums -> AlbumWall(
                                                albums = if (selectedSource == LibrarySourceMode.Cloud) remoteAlbums else albums,
                                                onOpenAlbum = onOpenAlbum,
                                                modifier = Modifier.fillMaxSize(),
                                            )

                                            LibraryViewMode.Artists -> ArtistWall(
                                                artists = artists,
                                                onOpenArtist = onOpenArtist,
                                                modifier = Modifier.fillMaxSize(),
                                            )

                                            LibraryViewMode.Cloud -> AlbumWall(
                                                albums = remoteAlbums,
                                                onOpenAlbum = onOpenAlbum,
                                                modifier = Modifier.fillMaxSize(),
                                            )

                                            LibraryViewMode.Playlists -> LocalPlaylistPanel(
                                                playlists = playlists,
                                                onOpenPlaylist = onOpenPlaylist,
                                                onPlayPlaylist = onPlayPlaylist,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibrarySourceMenu(
    selectedSource: LibrarySourceMode,
    linkedLibraryAvailable: Boolean,
    onSelectSource: (LibrarySourceMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Box(modifier = modifier) {
        Surface(
            modifier = Modifier.clickable { expanded = true },
            shape = RoundedCornerShape(8.dp),
            color = scheme.surface.copy(alpha = 0.50f),
            border = BorderStroke(1.dp, EchoGlassBorder),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    selectedSource.icon,
                    contentDescription = null,
                    tint = scheme.onSurface,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    selectedSource.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurface,
                    maxLines = 1,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = scheme.surface,
        ) {
            LibrarySourceMode.entries.forEach { source ->
                DropdownMenuItem(
                    text = {
                        Text(
                            source.label,
                            fontWeight = if (source == selectedSource) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (source == selectedSource) EchoAccentText else scheme.onSurface,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            source.icon,
                            contentDescription = null,
                            tint = if (source == selectedSource) EchoAccentText else scheme.onSurfaceVariant,
                        )
                    },
                    trailingIcon = if (source == LibrarySourceMode.PcEcho && !linkedLibraryAvailable) {
                        {
                            Text(
                                "未连接",
                                color = scheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        onSelectSource(source)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibrarySourceScanButton(
    selectedSource: LibrarySourceMode,
    linkedLibraryAvailable: Boolean,
    onSelectSource: (LibrarySourceMode) -> Unit,
    hasPermission: Boolean,
    scanState: LibraryScanProgress,
    onRequestPermission: () -> Unit,
    onScanFolder: () -> Unit,
    onScanAll: () -> Unit,
    onCancelScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sourceExpanded by remember { mutableStateOf(false) }
    var showScanOptions by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val scanDescription = when {
        !hasPermission -> "授权音乐权限"
        scanState.isScanning -> "取消扫描曲库"
        else -> "扫描曲库"
    }
    val scanAction = when {
        !hasPermission -> onRequestPermission
        scanState.isScanning -> onCancelScan
        else -> {
            { showScanOptions = true }
        }
    }

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier.combinedClickable(
                onClick = { sourceExpanded = true },
                onLongClick = scanAction,
                onLongClickLabel = scanDescription,
            ),
            shape = RoundedCornerShape(8.dp),
            color = scheme.surface.copy(alpha = 0.50f),
            border = BorderStroke(1.dp, EchoGlassBorder),
        ) {
            Icon(
                imageVector = if (scanState.isScanning) Icons.Rounded.Close else selectedSource.icon,
                contentDescription = "切换曲库来源；长按扫描歌曲",
                tint = if (scanState.error != null) Color(0xFFE0796E) else scheme.onSurface,
                modifier = Modifier
                    .padding(horizontal = 10.dp, vertical = 7.dp)
                    .size(20.dp),
            )
        }
        DropdownMenu(
            expanded = sourceExpanded,
            onDismissRequest = { sourceExpanded = false },
            containerColor = scheme.surface,
        ) {
            LibrarySourceMode.entries.forEach { source ->
                DropdownMenuItem(
                    text = {
                        Text(
                            source.label,
                            fontWeight = if (source == selectedSource) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (source == selectedSource) EchoAccentText else scheme.onSurface,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            source.icon,
                            contentDescription = null,
                            tint = if (source == selectedSource) EchoAccentText else scheme.onSurfaceVariant,
                        )
                    },
                    trailingIcon = if (source == LibrarySourceMode.PcEcho && !linkedLibraryAvailable) {
                        {
                            Text(
                                "未连接",
                                color = scheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        onSelectSource(source)
                        sourceExpanded = false
                    },
                )
            }
        }
    }

    if (showScanOptions) {
        LibraryScanOptionsDialog(
            onDismiss = { showScanOptions = false },
            onScanFolder = {
                showScanOptions = false
                onScanFolder()
            },
            onScanAll = {
                showScanOptions = false
                onScanAll()
            },
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
private fun LibrarySourceStrip(
    selectedSource: LibrarySourceMode,
    linkedLibraryAvailable: Boolean,
    onSelectSource: (LibrarySourceMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LibrarySourceMenu(
            selectedSource = selectedSource,
            linkedLibraryAvailable = linkedLibraryAvailable,
            onSelectSource = onSelectSource,
        )
    }
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
    showTrackAudioInfoTags: Boolean,
    selectedPlaylistId: String?,
    onQueryChange: (String) -> Unit,
    onSelectSource: (LibrarySourceMode) -> Unit,
    onSortModeChange: (LibraryTrackSortMode) -> Unit,
    onSelectMode: (LinkedLibraryMode) -> Unit,
    onOpenAlbum: (AlbumSummary) -> Unit,
    onOpenArtist: (ArtistSummary) -> Unit,
    onOpenPlaylist: (EchoRemotePlaylist) -> Unit,
    onCloseAlbum: () -> Unit,
    onCloseArtist: () -> Unit,
    onClosePlaylist: () -> Unit,
    onRefresh: (String) -> Unit,
    onPlayLinkedTrack: (EchoRemoteTrack) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tracks = state.tracks
    val playlists = state.playlists
    val normalizedQuery = remember(query) { query.trim() }
    val remoteQuery = remember(state.query) { state.query.trim() }
    val filteredTracks = remember(tracks, normalizedQuery, remoteQuery) {
        if (normalizedQuery.isNotBlank() && normalizedQuery == remoteQuery) {
            tracks
        } else {
            tracks.filterLinkedLibraryQuery(normalizedQuery)
        }
    }
    val sortedTracks = remember(filteredTracks, selectedSortMode) {
        filteredTracks.sortedForLinkedLibrary(selectedSortMode)
    }
    val albums = remember(filteredTracks) { filteredTracks.toLinkedAlbums() }
    val artists = remember(filteredTracks) { filteredTracks.toLinkedArtists() }
    val filteredPlaylists = remember(playlists, normalizedQuery, remoteQuery) {
        if (normalizedQuery.isNotBlank() && normalizedQuery == remoteQuery) {
            playlists
        } else {
            playlists.filterLinkedPlaylistQuery(normalizedQuery)
        }
    }
    val selectedAlbum = remember(albums, selectedAlbumKey) {
        albums.firstOrNull { it.albumKey == selectedAlbumKey }
    }
    val selectedArtist = remember(artists, selectedArtistKey) {
        artists.firstOrNull { it.artistKey == selectedArtistKey }
    }
    val selectedPlaylist = remember(playlists, selectedPlaylistId) {
        playlists.firstOrNull { it.id == selectedPlaylistId }
    }
    val selectedAlbumTracks = remember(filteredTracks, selectedAlbumKey) {
        if (selectedAlbumKey == null) {
            emptyList()
        } else {
            filteredTracks.filter { it.linkedAlbumKey() == selectedAlbumKey }
        }
    }
    val selectedArtistTracks = remember(filteredTracks, selectedArtistKey) {
        if (selectedArtistKey == null) {
            emptyList()
        } else {
            filteredTracks.filter { it.linkedArtistKey() == selectedArtistKey }
        }
    }

    LaunchedEffect(normalizedQuery, remoteQuery) {
        if (normalizedQuery != remoteQuery) {
            delay(300L)
            onRefresh(normalizedQuery)
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

    if (selectedAlbum != null) {
        LinkedAlbumTracksPage(
            album = selectedAlbum,
            tracks = selectedAlbumTracks,
            onBack = onCloseAlbum,
            onPlayLinkedTrack = onPlayLinkedTrack,
            modifier = modifier,
        )
        return
    }
    if (selectedArtist != null) {
        LinkedArtistTracksPage(
            artist = selectedArtist,
            tracks = selectedArtistTracks,
            onBack = onCloseArtist,
            onPlayLinkedTrack = onPlayLinkedTrack,
            modifier = modifier,
        )
        return
    }
    if (selectedPlaylist != null) {
        LinkedPlaylistTracksPage(
            playlist = selectedPlaylist,
            tracks = state.playlistTracks[selectedPlaylist.id].orEmpty(),
            isLoading = state.loadingPlaylistId == selectedPlaylist.id,
            error = state.error,
            onBack = onClosePlaylist,
            onPlayLinkedTrack = onPlayLinkedTrack,
            modifier = modifier,
        )
        return
    }

    LinkedLibraryChrome(
        actions = {
            LibrarySearchBar(
                query = query,
                onQueryChange = onQueryChange,
                expandedWidth = 240.dp,
            )
            IconButton(onClick = { onRefresh(normalizedQuery) }, enabled = !state.isLoading) {
                Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = "刷新 PC ECHO 曲库",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        modifier = modifier,
    ) {
        val errorMessage = state.error
        LinkedLibraryHeader(
            selectedSource = selectedSource,
            linkedLibraryAvailable = true,
            selectedMode = selectedMode,
            selectedSortMode = selectedSortMode,
            onSelectSource = onSelectSource,
            onSelectMode = onSelectMode,
            onSortModeChange = onSortModeChange,
        )
        when {
            state.isLoading -> EmptyState("正在读取 PC ECHO 曲库...")
            !errorMessage.isNullOrBlank() -> EmptyState(errorMessage)
            selectedMode == LinkedLibraryMode.Songs && sortedTracks.isEmpty() -> {
                EmptyState(if (query.isBlank()) "PC ECHO 暂无可显示歌曲。" else "PC ECHO 没有匹配的歌曲。")
            }
            selectedMode == LinkedLibraryMode.Albums && albums.isEmpty() -> {
                EmptyState(if (query.isBlank()) "PC ECHO 暂无可显示专辑。" else "PC ECHO 没有匹配的专辑。")
            }
            selectedMode == LinkedLibraryMode.Artists && artists.isEmpty() -> {
                EmptyState(if (query.isBlank()) "PC ECHO 暂无可显示艺术家。" else "PC ECHO 没有匹配的艺术家。")
            }
            selectedMode == LinkedLibraryMode.Playlists && filteredPlaylists.isEmpty() -> {
                EmptyState(if (query.isBlank()) "PC ECHO 暂无可显示歌单。" else "PC ECHO 没有匹配的歌单。")
            }
            selectedMode == LinkedLibraryMode.Songs -> LinkedTrackList(
                tracks = sortedTracks,
                onPlayLinkedTrack = onPlayLinkedTrack,
                showAudioInfoTags = showTrackAudioInfoTags,
                modifier = Modifier.weight(1f),
            )
            selectedMode == LinkedLibraryMode.Albums -> LinkedAlbumWall(
                albums = albums,
                onOpenAlbum = onOpenAlbum,
                modifier = Modifier.weight(1f),
            )
            selectedMode == LinkedLibraryMode.Artists -> LinkedArtistWall(
                artists = artists,
                onOpenArtist = onOpenArtist,
                modifier = Modifier.weight(1f),
            )
            selectedMode == LinkedLibraryMode.Playlists -> LinkedPlaylistList(
                playlists = filteredPlaylists,
                onOpenPlaylist = onOpenPlaylist,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LinkedLibraryHeader(
    selectedSource: LibrarySourceMode,
    linkedLibraryAvailable: Boolean,
    selectedMode: LinkedLibraryMode,
    selectedSortMode: LibraryTrackSortMode,
    onSelectSource: (LibrarySourceMode) -> Unit,
    onSelectMode: (LinkedLibraryMode) -> Unit,
    onSortModeChange: (LibraryTrackSortMode) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LibrarySourceMenu(
            selectedSource = selectedSource,
            linkedLibraryAvailable = linkedLibraryAvailable,
            onSelectSource = onSelectSource,
        )
        if (selectedMode == LinkedLibraryMode.Songs) {
            LibraryTrackSortMenu(
                selectedSortMode = selectedSortMode,
                onSortModeChange = onSortModeChange,
            )
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LinkedLibraryMode.entries.forEach { mode ->
            val selected = mode == selectedMode
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onSelectMode(mode) }
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    mode.label,
                    color = if (selected) EchoAccentText else scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                )
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(
                            if (selected) Brush.horizontalGradient(listOf(EchoAccent, EchoAccentDeep))
                            else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)),
                        ),
                )
            }
        }
    }
}

private fun List<EchoRemoteTrack>.sortedForLinkedLibrary(
    sortMode: LibraryTrackSortMode,
): List<EchoRemoteTrack> =
    when (sortMode) {
        LibraryTrackSortMode.Title,
        LibraryTrackSortMode.FrequentlyPlayed,
        LibraryTrackSortMode.RecentlyUpdated,
        -> sortedWith(
            compareBy<EchoRemoteTrack> { it.title.lowercase() }
                .thenBy { it.artist.lowercase() }
                .thenBy { it.album.orEmpty().lowercase() },
        )
        LibraryTrackSortMode.Duration -> sortedWith(
            compareByDescending<EchoRemoteTrack> { it.durationMs }
                .thenBy { it.title.lowercase() },
        )
        LibraryTrackSortMode.Random -> shuffled()
        LibraryTrackSortMode.Artist -> sortedWith(
            compareBy<EchoRemoteTrack> { it.artist.lowercase() }
                .thenBy { it.album.orEmpty().lowercase() }
                .thenBy { it.title.lowercase() },
        )
        LibraryTrackSortMode.Album -> sortedWith(
            compareBy<EchoRemoteTrack> { it.album.orEmpty().lowercase() }
                .thenBy { it.artist.lowercase() }
                .thenBy { it.title.lowercase() },
        )
    }

@Composable
private fun LinkedLibraryChrome(
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.30f),
                        EchoHomeMist.copy(alpha = 0.22f),
                        Color.Transparent,
                    ),
                ),
            )
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = EchoContentMaxWidth)
                .fillMaxSize()
                .align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    actions()
                }
            }
            Spacer(Modifier.height(4.dp))
            content()
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(EchoHomeMist.copy(alpha = 0.46f))
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkTile(
            artworkUri = playlist.artworkUrl,
            modifier = Modifier.size(58.dp),
            accent = EchoAccent,
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
            color = EchoAccentDeep.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, EchoGlassBorder),
            shape = RoundedCornerShape(12.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
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
    modifier: Modifier = Modifier,
) {
    LinkedLibraryChrome(
        actions = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "返回 PC ECHO 歌单",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        modifier = modifier,
    ) {
        EchoPanel(Modifier.fillMaxWidth()) {
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
                    accent = EchoAccent,
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
            isLoading -> EmptyState("正在读取 PC ECHO 歌单...")
            tracks.isEmpty() && !error.isNullOrBlank() -> EmptyState(error)
            tracks.isEmpty() -> EmptyState("这个 PC ECHO 歌单暂时没有可播放曲目。")
            else -> LinkedTrackList(
                tracks = tracks,
                onPlayLinkedTrack = onPlayLinkedTrack,
                showAudioInfoTags = false,
                modifier = Modifier.weight(1f),
            )
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
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
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
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
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
    onBack: () -> Unit,
    onPlayLinkedTrack: (EchoRemoteTrack) -> Unit,
    modifier: Modifier = Modifier,
) {
    val albumTracks = remember(tracks) { tracks.map { it.toEchoTrack() } }
    val remoteTracksByUiId = remember(tracks, albumTracks) {
        tracks.zip(albumTracks).associate { (remote, uiTrack) -> uiTrack.id to remote }
    }
    AlbumDetailListPage(
        album = album,
        tracks = albumTracks,
        onBack = onBack,
        onPlayAll = { tracks.firstOrNull()?.let(onPlayLinkedTrack) },
        onShuffle = { tracks.shuffled().firstOrNull()?.let(onPlayLinkedTrack) },
        onPlayTrack = { track -> remoteTracksByUiId[track.id]?.let(onPlayLinkedTrack) },
        modifier = modifier,
    )
}

@Composable
private fun LinkedArtistTracksPage(
    artist: ArtistSummary,
    tracks: List<EchoRemoteTrack>,
    onBack: () -> Unit,
    onPlayLinkedTrack: (EchoRemoteTrack) -> Unit,
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
        onPlayAll = { tracks.firstOrNull()?.let(onPlayLinkedTrack) },
        onShuffle = { tracks.shuffled().firstOrNull()?.let(onPlayLinkedTrack) },
        onPlayTrack = { track -> remoteTracksByUiId[track.id]?.let(onPlayLinkedTrack) },
        modifier = modifier,
    )
}

private fun List<EchoRemoteTrack>.toLinkedAlbums(): List<AlbumSummary> =
    groupBy { it.linkedAlbumKey() }
        .values
        .map { albumTracks ->
            val first = albumTracks.first()
            AlbumSummary(
                albumKey = first.linkedAlbumKey(),
                title = first.album?.takeIf { it.isNotBlank() } ?: "未知专辑",
                albumArtist = first.artist.takeIf { it.isNotBlank() },
                artist = first.artist.takeIf { it.isNotBlank() },
                artworkUri = albumTracks.firstNotNullOfOrNull { it.artworkUrl?.takeIf(String::isNotBlank) },
                trackCount = albumTracks.size,
                durationMs = albumTracks.sumOf { it.durationMs.coerceAtLeast(0L) },
                year = null,
            )
        }
        .sortedWith(compareBy<AlbumSummary> { it.title.lowercase() }.thenBy { it.albumArtist.orEmpty().lowercase() })

private fun List<EchoRemoteTrack>.toLinkedArtists(): List<ArtistSummary> =
    groupBy { it.linkedArtistKey() }
        .values
        .map { artistTracks ->
            val first = artistTracks.first()
            ArtistSummary(
                artistKey = first.linkedArtistKey(),
                name = first.artist.takeIf { it.isNotBlank() } ?: "未知艺术家",
                artworkUri = artistTracks.firstNotNullOfOrNull { it.artworkUrl?.takeIf(String::isNotBlank) },
                albumCount = artistTracks.map { it.linkedAlbumKey() }.distinct().size,
                trackCount = artistTracks.size,
                durationMs = artistTracks.sumOf { it.durationMs.coerceAtLeast(0L) },
            )
        }
        .sortedWith(compareBy<ArtistSummary> { it.name.lowercase() }.thenByDescending { it.trackCount })

private fun EchoRemoteTrack.linkedAlbumKey(): String =
    "echo-link:${album?.trim().orEmpty().lowercase()}|${artist.trim().lowercase()}"

private fun EchoRemoteTrack.linkedArtistKey(): String =
    "echo-link:${artist.trim().ifBlank { "PC ECHO" }.lowercase()}"

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

private fun List<EchoRemoteTrack>.filterLinkedLibraryQuery(query: String): List<EchoRemoteTrack> {
    val terms = query.trim()
        .lowercase()
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)
    if (terms.isEmpty()) return this
    return filter { track ->
        val searchableText = buildString {
            append(track.title)
            append(' ')
            append(track.artist)
            append(' ')
            append(track.album.orEmpty())
        }.lowercase()
        terms.all(searchableText::contains)
    }
}

private fun List<EchoRemotePlaylist>.filterLinkedPlaylistQuery(query: String): List<EchoRemotePlaylist> {
    val terms = query.trim()
        .lowercase()
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)
    if (terms.isEmpty()) return this
    return filter { playlist ->
        val searchableText = buildString {
            append(playlist.name)
            append(' ')
            append(playlist.sourceLabel.orEmpty())
        }.lowercase()
        terms.all(searchableText::contains)
    }
}

private fun linkedPlaylistSubtitle(playlist: EchoRemotePlaylist): String {
    val source = playlist.sourceLabel?.takeIf { it.isNotBlank() } ?: "PC ECHO"
    return "${playlist.trackCount} 首 · $source"
}

@Composable
private fun LibraryTrackSortMenu(
    selectedSortMode: LibraryTrackSortMode,
    onSortModeChange: (LibraryTrackSortMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.AutoMirrored.Rounded.Sort,
                contentDescription = "设置歌曲排序",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            LibraryTrackSortMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label) },
                    onClick = {
                        expanded = false
                        onSortModeChange(mode)
                    },
                    trailingIcon = {
                        if (mode == selectedSortMode) {
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

@Composable
private fun LibraryBrowserHeader(
    scanState: LibraryScanProgress,
    selectedSource: LibrarySourceMode,
    linkedLibraryAvailable: Boolean,
    onSelectSource: (LibrarySourceMode) -> Unit,
    selectedMode: LibraryViewMode,
    selectedSortMode: LibraryTrackSortMode,
    onSelectMode: (LibraryViewMode) -> Unit,
    onSortModeChange: (LibraryTrackSortMode) -> Unit,
) {
    LibraryScanResultBanner(scanState)
    LibraryPagerTabs(
        selectedMode = selectedMode,
        onSelectMode = onSelectMode,
    )
}
