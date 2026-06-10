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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Devices
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
import app.echo.android.model.connect.EchoRemoteTrack
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.ArtistSummary
import app.echo.android.model.library.EchoPlaylist
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.FolderSummary
import app.echo.android.model.library.LibraryScanProgress
import app.echo.android.model.library.LibrarySource
import app.echo.android.model.library.NeteaseAccountState
import app.echo.android.model.library.NeteaseAudioQuality
import app.echo.android.model.library.NeteaseImportState

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
}

private enum class LibrarySourceMode(
    val label: String,
    val icon: ImageVector,
) {
    Local("本地", Icons.Rounded.LibraryMusic),
    PcEcho("PC ECHO", Icons.Rounded.Devices),
    Cloud("网盘", Icons.Rounded.CloudQueue),
}

@Composable
fun LibraryScreen(
    hasPermission: Boolean,
    scanState: LibraryScanProgress,
    tracks: LazyPagingItems<EchoTrack>,
    albums: LazyPagingItems<AlbumSummary>,
    remoteAlbums: LazyPagingItems<AlbumSummary>,
    linkedLibraryActive: Boolean,
    linkedLibraryAvailable: Boolean,
    linkedLibraryState: EchoRemoteLibraryState,
    artists: LazyPagingItems<ArtistSummary>,
    folders: LazyPagingItems<FolderSummary>,
    neteaseImportedPlaylists: List<EchoPlaylist>,
    neteaseAccountState: NeteaseAccountState,
    neteaseImportState: NeteaseImportState,
    neteaseQuality: NeteaseAudioQuality,
    selectedAlbum: AlbumSummary?,
    selectedArtist: ArtistSummary?,
    selectedFolder: FolderSummary?,
    selectedPlaylist: EchoPlaylist?,
    albumDetailTracks: LazyPagingItems<EchoTrack>?,
    artistDetailTracks: LazyPagingItems<EchoTrack>?,
    folderDetailTracks: LazyPagingItems<EchoTrack>?,
    playlistDetailTracks: LazyPagingItems<EchoTrack>?,
    onRequestPermission: () -> Unit,
    onScanFolder: () -> Unit,
    onScanAll: () -> Unit,
    onCancelScan: () -> Unit,
    onRefreshLinkedLibrary: () -> Unit,
    onPlayLinkedTrack: (EchoRemoteTrack) -> Unit,
    onPlayTrack: (EchoTrack) -> Unit,
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
    var selectedSource by remember {
        mutableStateOf(if (linkedLibraryActive) LibrarySourceMode.PcEcho else LibrarySourceMode.Local)
    }
    var linkedMode by remember { mutableStateOf(LinkedLibraryMode.Songs) }
    var selectedLinkedAlbumKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(linkedLibraryActive) {
        if (linkedLibraryActive) {
            selectedSource = LibrarySourceMode.PcEcho
        }
    }

    fun selectSource(source: LibrarySourceMode) {
        selectedSource = source
        selectedLinkedAlbumKey = null
        when (source) {
            LibrarySourceMode.Local -> {
                if (selectedMode == LibraryViewMode.Cloud) {
                    selectedModeIndex = LibraryViewMode.Songs.ordinal
                }
            }
            LibrarySourceMode.Cloud -> selectedModeIndex = LibraryViewMode.Cloud.ordinal
            LibrarySourceMode.PcEcho -> if (linkedLibraryAvailable) onRefreshLinkedLibrary()
        }
    }

    if (selectedSource == LibrarySourceMode.PcEcho && linkedLibraryAvailable) {
        LinkedEchoLibraryPage(
            state = linkedLibraryState,
            selectedMode = linkedMode,
            selectedAlbumKey = selectedLinkedAlbumKey,
            selectedSource = selectedSource,
            onSelectSource = ::selectSource,
            onSelectMode = { mode ->
                linkedMode = mode
                selectedLinkedAlbumKey = null
            },
            onOpenAlbum = { album -> selectedLinkedAlbumKey = album.albumKey },
            onCloseAlbum = { selectedLinkedAlbumKey = null },
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
            badgeContent = {
                LibrarySourceMenu(
                    selectedSource = selectedSource,
                    linkedLibraryAvailable = linkedLibraryAvailable,
                    onSelectSource = ::selectSource,
                )
            },
            actions = {
                IconButton(onClick = onRefreshLinkedLibrary) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = "刷新 PC ECHO 曲库",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        ) {
            EmptyState("先到“互联”连接 PC ECHO，然后这里就能浏览 PC 曲库。")
        }
        return
    }

    // 专辑详情走全屏沉浸式页面，不套用曲库的 PageChrome
    val activeAlbumDetail = selectedAlbum
    if (activeAlbumDetail != null && albumDetailTracks != null) {
        AlbumDetailPage(
            album = activeAlbumDetail,
            tracks = albumDetailTracks,
            onBack = onCloseDetail,
            onPlayAll = { onPlayAlbum(activeAlbumDetail) },
            onShuffle = { onShuffleAlbum(activeAlbumDetail) },
            onPlayTrack = onPlayTrack,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    // 艺术家详情同样走全屏沉浸式页面
    val activeArtistDetail = selectedArtist
    if (activeArtistDetail != null && artistDetailTracks != null) {
        ArtistDetailPage(
            artist = activeArtistDetail,
            tracks = artistDetailTracks,
            onBack = onCloseDetail,
            onPlayAll = { onPlayArtist(activeArtistDetail) },
            onShuffle = { onShuffleArtist(activeArtistDetail) },
            onPlayTrack = onPlayTrack,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    val activeFolderDetail = selectedFolder
    val activePlaylistDetail = selectedPlaylist
    if (activePlaylistDetail != null && playlistDetailTracks != null) {
        LibraryDetailPage(
            title = activePlaylistDetail.name,
            subtitle = "${activePlaylistDetail.trackCount} 首 · 网易云歌单",
            tracks = playlistDetailTracks,
            onBack = onCloseDetail,
            onPlayAll = { onPlayPlaylist(activePlaylistDetail) },
            onPlayTrack = onPlayTrack,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    AnimatedContent(
        targetState = activeFolderDetail != null && folderDetailTracks != null,
        transitionSpec = {
            if (targetState) {
                val enter = slideInHorizontally(tween(durationMillis = 360, easing = LibraryFolderMotionEasing)) { it / 3 } +
                    fadeIn(tween(durationMillis = 180, easing = LibraryFolderMotionEasing)) +
                    scaleIn(
                        initialScale = 0.985f,
                        animationSpec = tween(durationMillis = 360, easing = LibraryFolderMotionEasing),
                    )
                val exit = slideOutHorizontally(tween(durationMillis = 220, easing = LibraryFolderMotionEasing)) { -it / 8 } +
                    fadeOut(tween(durationMillis = 140, easing = LibraryFolderMotionEasing)) +
                    scaleOut(
                        targetScale = 0.992f,
                        animationSpec = tween(durationMillis = 220, easing = LibraryFolderMotionEasing),
                    )
                enter togetherWith exit
            } else {
                val enter = slideInHorizontally(tween(durationMillis = 320, easing = LibraryFolderMotionEasing)) { -it / 5 } +
                    fadeIn(tween(durationMillis = 160, easing = LibraryFolderMotionEasing))
                val exit = slideOutHorizontally(tween(durationMillis = 240, easing = LibraryFolderMotionEasing)) { it / 3 } +
                    fadeOut(tween(durationMillis = 140, easing = LibraryFolderMotionEasing))
                enter togetherWith exit
            }
        },
        label = "library-folder-detail-transition",
        modifier = Modifier.fillMaxSize(),
    ) { showFolderDetail ->
        if (showFolderDetail && activeFolderDetail != null && folderDetailTracks != null) {
            FolderDetailPage(
                folder = activeFolderDetail,
                tracks = folderDetailTracks,
                onBack = onCloseDetail,
                onPlayAll = { onPlayFolder(activeFolderDetail) },
                onPlayTrack = onPlayTrack,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            PageChrome(
                title = "曲库",
                subtitle = null,
                badge = selectedSource.label,
                showBrand = false,
                compactHeader = true,
                badgeContent = {
                    LibrarySourceMenu(
                        selectedSource = selectedSource,
                        linkedLibraryAvailable = linkedLibraryAvailable,
                        onSelectSource = ::selectSource,
                    )
                },
                actions = {
                    LibraryScanAction(
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
                    tracks.loadState.refresh is LoadState.Loading -> {
                        LibraryBrowserHeader(
                            scanState = scanState,
                            selectedMode = selectedMode,
                            onSelectMode = { mode ->
                                selectedModeIndex = mode.ordinal
                                selectedSource = if (mode == LibraryViewMode.Cloud) {
                                    LibrarySourceMode.Cloud
                                } else {
                                    LibrarySourceMode.Local
                                }
                            },
                        )
                        EmptyState("正在加载曲库...")
                    }
                    tracks.loadState.refresh is LoadState.Error -> {
                        LibraryBrowserHeader(
                            scanState = scanState,
                            selectedMode = selectedMode,
                            onSelectMode = { mode ->
                                selectedModeIndex = mode.ordinal
                                selectedSource = if (mode == LibraryViewMode.Cloud) {
                                    LibrarySourceMode.Cloud
                                } else {
                                    LibrarySourceMode.Local
                                }
                            },
                        )
                        EmptyState("曲库查询失败。")
                    }
                    else -> {
                        LibraryBrowserHeader(
                            scanState = scanState,
                            selectedMode = selectedMode,
                            onSelectMode = { mode ->
                                selectedModeIndex = mode.ordinal
                                selectedSource = if (mode == LibraryViewMode.Cloud) {
                                    LibrarySourceMode.Cloud
                                } else {
                                    LibrarySourceMode.Local
                                }
                            },
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
                                    albums = albums,
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

                                LibraryViewMode.Playlists -> NeteasePlaylistPanel(
                                    accountState = neteaseAccountState,
                                    importState = neteaseImportState,
                                    importedPlaylists = neteaseImportedPlaylists,
                                    selectedQuality = neteaseQuality,
                                    onLoginByPhone = onLoginNeteaseByPhone,
                                    onLoginWithCookie = onLoginNeteaseWithCookie,
                                    onLogout = onLogoutNetease,
                                    onRefreshRemotePlaylists = onRefreshNeteasePlaylists,
                                    onOpenNeteaseApp = onOpenNeteaseApp,
                                    onQualityChange = onNeteaseQualityChange,
                                    onImportPlaylist = onImportNeteasePlaylist,
                                    onOpenImportedPlaylist = onOpenPlaylist,
                                    onPlayImportedPlaylist = onPlayPlaylist,
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

@Composable
private fun LibrarySourceMenu(
    selectedSource: LibrarySourceMode,
    linkedLibraryAvailable: Boolean,
    onSelectSource: (LibrarySourceMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Box {
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

@Composable
private fun LinkedEchoLibraryPage(
    state: EchoRemoteLibraryState,
    selectedMode: LinkedLibraryMode,
    selectedAlbumKey: String?,
    selectedSource: LibrarySourceMode,
    onSelectSource: (LibrarySourceMode) -> Unit,
    onSelectMode: (LinkedLibraryMode) -> Unit,
    onOpenAlbum: (AlbumSummary) -> Unit,
    onCloseAlbum: () -> Unit,
    onRefresh: () -> Unit,
    onPlayLinkedTrack: (EchoRemoteTrack) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tracks = state.tracks
    val albums = remember(tracks) { tracks.toLinkedAlbums() }
    val selectedAlbum = remember(albums, selectedAlbumKey) {
        albums.firstOrNull { it.albumKey == selectedAlbumKey }
    }
    val selectedAlbumTracks = remember(tracks, selectedAlbumKey) {
        if (selectedAlbumKey == null) {
            emptyList()
        } else {
            tracks.filter { it.linkedAlbumKey() == selectedAlbumKey }
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

    LinkedLibraryChrome(
        title = "曲库",
        subtitle = if (state.totalCount > 0) "PC ECHO · ${state.totalCount} 首" else "PC ECHO",
        badge = "PC ECHO",
        badgeContent = {
            LibrarySourceMenu(
                selectedSource = selectedSource,
                linkedLibraryAvailable = true,
                onSelectSource = onSelectSource,
            )
        },
        actions = {
            IconButton(onClick = onRefresh, enabled = !state.isLoading) {
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
            selectedMode = selectedMode,
            onSelectMode = onSelectMode,
        )
        when {
            state.isLoading -> EmptyState("正在读取 PC ECHO 曲库...")
            !errorMessage.isNullOrBlank() -> EmptyState(errorMessage)
            selectedMode == LinkedLibraryMode.Songs && tracks.isEmpty() -> EmptyState("PC ECHO 暂无可显示歌曲。")
            selectedMode == LinkedLibraryMode.Albums && albums.isEmpty() -> EmptyState("PC ECHO 暂无可显示专辑。")
            selectedMode == LinkedLibraryMode.Songs -> LinkedTrackList(
                tracks = tracks,
                onPlayLinkedTrack = onPlayLinkedTrack,
                modifier = Modifier.weight(1f),
            )
            selectedMode == LinkedLibraryMode.Albums -> LinkedAlbumWall(
                albums = albums,
                onOpenAlbum = onOpenAlbum,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LinkedLibraryHeader(
    selectedMode: LinkedLibraryMode,
    onSelectMode: (LinkedLibraryMode) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
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

@Composable
private fun LinkedLibraryChrome(
    title: String,
    subtitle: String?,
    badge: String,
    badgeContent: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    subtitle?.takeIf { it.isNotBlank() }?.let { value ->
                        Text(
                            value,
                            color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (badgeContent != null) {
                        badgeContent()
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = scheme.surface.copy(alpha = 0.50f),
                            border = BorderStroke(1.dp, EchoGlassBorder),
                        ) {
                            Text(
                                badge,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurface,
                            )
                        }
                    }
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
private fun LinkedAlbumTracksPage(
    album: AlbumSummary,
    tracks: List<EchoRemoteTrack>,
    onBack: () -> Unit,
    onPlayLinkedTrack: (EchoRemoteTrack) -> Unit,
    modifier: Modifier = Modifier,
) {
    val artistLabel = album.albumArtist ?: album.artist ?: "PC ECHO"
    LinkedLibraryChrome(
        title = album.title,
        subtitle = "$artistLabel · ${tracks.size} 首",
        badge = "PC 专辑",
        actions = {
            TextButton(onClick = onBack) {
                Text("返回")
            }
        },
        modifier = modifier,
    ) {
        if (tracks.isEmpty()) {
            EmptyState("这个 PC 专辑暂无可显示歌曲。")
        } else {
            LinkedTrackList(
                tracks = tracks,
                onPlayLinkedTrack = onPlayLinkedTrack,
                modifier = Modifier.weight(1f),
            )
        }
    }
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

private fun EchoRemoteTrack.linkedAlbumKey(): String =
    "echo-link:${album?.trim().orEmpty().lowercase()}|${artist.trim().lowercase()}"

private fun EchoRemoteTrack.toEchoTrack(): EchoTrack =
    EchoTrack(
        id = "echo-link:${id ?: "${title.hashCode()}-${artist.hashCode()}-${album.hashCode()}"}",
        uri = "",
        title = title,
        artist = artist.ifBlank { "PC ECHO" },
        album = album,
        artworkUri = artworkUrl,
        durationMs = durationMs,
        source = LibrarySource("echo-link"),
    )

@Composable
private fun LibraryBrowserHeader(
    scanState: LibraryScanProgress,
    selectedMode: LibraryViewMode,
    onSelectMode: (LibraryViewMode) -> Unit,
) {
    LibraryScanResultBanner(scanState)
    LibraryPagerTabs(
        selectedMode = selectedMode,
        onSelectMode = onSelectMode,
    )
}
