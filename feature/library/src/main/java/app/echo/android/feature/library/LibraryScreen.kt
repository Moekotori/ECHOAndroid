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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import app.echo.android.design.EchoAccent
import app.echo.android.design.EchoAccentDeep
import app.echo.android.design.EchoAccentText
import app.echo.android.design.EchoContentMaxWidth
import app.echo.android.design.EchoGlassBorder
import app.echo.android.design.EchoHomeMist
import app.echo.android.design.EchoPanel
import app.echo.android.design.EmptyState
import app.echo.android.design.LocalEchoWidthSizeClass
import app.echo.android.design.PageChrome
import app.echo.android.design.echoString
import app.echo.android.model.connect.EchoRemoteLibraryState
import app.echo.android.model.connect.EchoRemotePlaylist
import app.echo.android.model.connect.EchoRemoteTrack
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.ArtistSummary
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

private val LibraryFolderMotionEasing = CubicBezierEasing(0.16f, 1f, 0.30f, 1f)
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
    Cloud(Icons.Rounded.CloudQueue),
    Playlists(Icons.Rounded.LibraryMusic),
}

@Composable
internal fun LibraryViewMode.label(): String = when (this) {
    LibraryViewMode.Songs -> echoString(en = "Songs", zh = "歌曲", ja = "曲")
    LibraryViewMode.Folders -> echoString(en = "Folders", zh = "文件夹", ja = "フォルダー")
    LibraryViewMode.Albums -> echoString(en = "Albums", zh = "专辑", ja = "アルバム")
    LibraryViewMode.Artists -> echoString(en = "Artists", zh = "艺术家", ja = "アーティスト")
    LibraryViewMode.Cloud -> echoString(en = "Cloud", zh = "网盘", ja = "クラウド")
    LibraryViewMode.Playlists -> echoString(en = "Playlists", zh = "歌单", ja = "プレイリスト")
}

private enum class LinkedLibraryMode(
    val icon: ImageVector,
) {
    Songs(Icons.AutoMirrored.Rounded.QueueMusic),
    Albums(Icons.Rounded.LibraryMusic),
    Artists(Icons.Rounded.Person),
    Playlists(Icons.Rounded.LibraryMusic),
}

@Composable
private fun LinkedLibraryMode.label(): String = when (this) {
    LinkedLibraryMode.Songs -> echoString(en = "Songs", zh = "歌曲", ja = "曲")
    LinkedLibraryMode.Albums -> echoString(en = "Albums", zh = "专辑", ja = "アルバム")
    LinkedLibraryMode.Artists -> echoString(en = "Artists", zh = "艺术家", ja = "アーティスト")
    LinkedLibraryMode.Playlists -> echoString(en = "Playlists", zh = "歌单", ja = "プレイリスト")
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
    LibrarySourceMode.Local -> echoString(en = "Local", zh = "本地", ja = "ローカル")
    LibrarySourceMode.PcEcho -> echoString(en = "PC ECHO", zh = "PC ECHO", ja = "PC ECHO")
    LibrarySourceMode.Cloud -> echoString(en = "Cloud", zh = "网盘", ja = "クラウド")
}

@Composable
internal fun LibraryTrackSortMode.label(): String = when (this) {
    LibraryTrackSortMode.Title -> echoString(en = "song title", zh = "歌曲标题", ja = "曲名")
    LibraryTrackSortMode.Duration -> echoString(en = "duration", zh = "音乐时间", ja = "再生時間")
    LibraryTrackSortMode.FrequentlyPlayed -> echoString(en = "frequently played", zh = "常听歌曲", ja = "よく聴く曲")
    LibraryTrackSortMode.Random -> echoString(en = "shuffle", zh = "随机排序", ja = "ランダム")
    LibraryTrackSortMode.Artist -> echoString(en = "artist", zh = "艺术家", ja = "アーティスト")
    LibraryTrackSortMode.Album -> echoString(en = "album", zh = "专辑", ja = "アルバム")
    LibraryTrackSortMode.RecentlyUpdated -> echoString(en = "recently updated", zh = "最近更新", ja = "最近の更新")
}

@Composable
internal fun unknownArtistLabel(): String =
    echoString(en = "Unknown artist", zh = "未知艺术家", ja = "不明なアーティスト")

@Composable
internal fun unknownAlbumLabel(): String =
    echoString(en = "Unknown album", zh = "未知专辑", ja = "不明なアルバム")

@Composable
internal fun unknownTrackLabel(): String =
    echoString(en = "Unknown track", zh = "未知曲目", ja = "不明な曲")

@Composable
internal fun libraryTrackCountLabel(count: Int): String =
    echoString(en = "$count tracks", zh = "$count 首", ja = "$count 曲")

@Composable
internal fun libraryAlbumCountLabel(count: Int): String =
    echoString(en = "$count albums", zh = "$count 张专辑", ja = "アルバム $count 枚")

@Composable
internal fun libraryMinutesLabel(minutes: Int): String =
    echoString(en = "$minutes min", zh = "$minutes 分钟", ja = "$minutes 分")

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
    tracks: Flow<PagingData<EchoTrack>>,
    albums: Flow<PagingData<AlbumSummary>>,
    remoteAlbums: Flow<PagingData<AlbumSummary>>,
    linkedLibraryActive: Boolean,
    linkedLibraryAvailable: Boolean,
    linkedLibraryState: StateFlow<EchoRemoteLibraryState>,
    selectedLibrarySourceId: String,
    artists: Flow<PagingData<ArtistSummary>>,
    folders: Flow<PagingData<FolderSummary>>,
    playlists: List<EchoPlaylist>,
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
    onPlayFolder: (FolderSummary) -> Unit,
    onPlayPlaylist: (EchoPlaylist) -> Unit,
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
) {
    val playNext = onPlayNext
    val enqueueTrack = onEnqueueTrack
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
            title = echoString(en = "Library", zh = "曲库", ja = "ライブラリ"),
            subtitle = "PC ECHO",
            badge = selectedSource.label(),
            showBrand = false,
            compactHeader = true,
            badgeContent = {},
            actions = {
                IconButton(onClick = { onRefreshLinkedLibrary(libraryQuery) }) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = echoString(
                            en = "Refresh PC ECHO library",
                            zh = "刷新 PC ECHO 曲库",
                            ja = "PC ECHO ライブラリを更新",
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LibrarySearchBar(
                    query = libraryQuery,
                    onQueryChange = onLibraryQueryChange,
                    expandedWidth = 240.dp,
                )
            },
        ) {
            LibrarySourceStrip(
                selectedSource = selectedSource,
                linkedLibraryAvailable = linkedLibraryAvailable,
                onSelectSource = ::selectSource,
            )
            EmptyState(
                echoString(
                    en = "Connect PC ECHO in Link first, then you can browse the PC library here.",
                    zh = "先到“互联”连接 PC ECHO，然后这里就能浏览 PC 曲库。",
                    ja = "先に「連携」で PC ECHO を接続すると、ここで PC ライブラリを閲覧できます。",
                ),
            )
        }
        return
    }

    val prefersSplit = LocalEchoWidthSizeClass.current.prefersLibrarySplit

    @Composable
    fun LocalBrowserPane() {
        PageChrome(
            title = echoString(en = "Library", zh = "曲库", ja = "ライブラリ"),
            subtitle = null,
            badge = selectedSource.label(),
            showBrand = false,
            compactHeader = true,
            badgeContent = {},
            titleContent = {},
            actions = {
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
                LibrarySearchBar(
                    query = libraryQuery,
                    onQueryChange = onLibraryQueryChange,
                    expandedWidth = 240.dp,
                )
            },
        ) {
            when {
                scanState.isScanning -> LibraryScanStatus(scanState = scanState, onCancelScan = onCancelScan)
                else -> {
                    LibraryBrowserHeader(
                        scanState = scanState,
                        showScanResultBanner = showScanResultBanner,
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
                                val trackItems = tracks.collectAsLazyPagingItems()
                                val showInitialTrackLoading =
                                    trackItems.itemCount == 0 &&
                                        trackItems.loadState.refresh is LoadState.Loading
                                val showInitialTrackError =
                                    trackItems.itemCount == 0 &&
                                        trackItems.loadState.refresh is LoadState.Error
                                when {
                                    !hasPermission ->
                                        EmptyState(
                                            echoString(
                                                en = "Grant access to index local music. Cloud libraries can open the Cloud tab directly.",
                                                zh = "授权后即可索引本地音乐；云端曲库可直接进入“网盘”页。",
                                                ja = "許可するとローカル音楽を索引できます。クラウドライブラリは「クラウド」から直接開けます。",
                                            ),
                                        )
                                    showInitialTrackLoading -> EmptyState(
                                        echoString(
                                            en = "Loading library...",
                                            zh = "正在加载曲库...",
                                            ja = "ライブラリを読み込み中...",
                                        ),
                                    )
                                    showInitialTrackError -> EmptyState(
                                        echoString(
                                            en = "Library query failed.",
                                            zh = "曲库查询失败。",
                                            ja = "ライブラリの照会に失敗しました。",
                                        ),
                                    )
                                    trackItems.itemCount == 0 -> LibraryBootstrapState()
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
                                modifier = Modifier.fillMaxSize(),
                            )
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
                        modifier = Modifier.fillMaxSize(),
                    )
                    selectedFolder != null && folderDetailTracks != null -> FolderDetailPage(
                        folder = selectedFolder,
                        tracks = folderDetailTracks,
                        onBack = onCloseDetail,
                        onPlayAll = { onPlayFolder(selectedFolder) },
                        onPlayTrack = { track ->
                            onPlayTrack(track, LibraryPlaybackOrigin.Folder(selectedFolder.folderKey))
                        },
                        onUpdateTrackMetadata = onUpdateTrackMetadata,
                        onImportLyrics = onImportLyricsForTrack,
                        onPickArtwork = onPickTrackArtwork,
                        onAddToPlaylist = { track -> addToPlaylistTrack = track },
                        modifier = Modifier.fillMaxSize(),
                    )
                    livePlaylist != null && playlistDetailTracks != null -> PlaylistDetailPage(
                        playlist = livePlaylist,
                        tracks = playlistDetailTracks,
                        onBack = onCloseDetail,
                        onPlayAll = { onPlayPlaylist(livePlaylist) },
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
                        showAudioInfoTags = showTrackAudioInfoTags,
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
                    val livePlaylist = playlists.firstOrNull { it.id == activePlaylistDetail.id }
                        ?: activePlaylistDetail
                    PlaylistDetailPage(
                        playlist = livePlaylist,
                        tracks = playlistDetailTracks,
                        onBack = onCloseDetail,
                        onPlayAll = { onPlayPlaylist(livePlaylist) },
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
                            onPlayTrack = { track ->
                                onPlayTrack(track, LibraryPlaybackOrigin.Folder(target.folder.folderKey))
                            },
                            onUpdateTrackMetadata = onUpdateTrackMetadata,
                            onImportLyrics = onImportLyricsForTrack,
                            onPickArtwork = onPickTrackArtwork,
                            onAddToPlaylist = { track -> addToPlaylistTrack = track },
                            modifier = Modifier.fillMaxSize(),
                        )
                        LibraryFolderTransitionTarget.Browser -> LocalBrowserPane()
                    }
                }
            }
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
        EmptyState(
            echoString(
                en = "Pick an album, artist, folder, or playlist.",
                zh = "选择一张专辑、一位艺术家、一个文件夹或歌单。",
                ja = "アルバム、アーティスト、フォルダー、またはプレイリストを選んでください。",
            ),
        )
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
                    selectedSource.label(),
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
                            source.label(),
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
                                echoString(en = "Not connected", zh = "未连接", ja = "未接続"),
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
        !hasPermission -> echoString(en = "Allow music access", zh = "授权音乐权限", ja = "音楽へのアクセスを許可")
        scanState.isScanning -> echoString(en = "Cancel library scan", zh = "取消扫描曲库", ja = "ライブラリのスキャンをキャンセル")
        else -> echoString(en = "Scan library", zh = "扫描曲库", ja = "ライブラリをスキャン")
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
                contentDescription = echoString(
                    en = "Switch library source; long-press to scan tracks",
                    zh = "切换曲库来源；长按扫描歌曲",
                    ja = "ライブラリのソースを切り替え。長押しで曲をスキャン",
                ),
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
                            source.label(),
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
                                echoString(en = "Not connected", zh = "未连接", ja = "未接続"),
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
        includeSortedTracks,
        includeAlbums,
        includeArtists,
        includePlaylists,
    ) {
        value = withContext(Dispatchers.Default) {
            LinkedLibraryCatalog.build(
                tracks = tracks,
                playlists = playlists,
                query = normalizedQuery,
                remoteQuery = remoteQuery,
                sortMode = selectedSortMode,
                includeSortedTracks = includeSortedTracks,
                includeAlbums = includeAlbums,
                includeArtists = includeArtists,
                includePlaylists = includePlaylists,
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
    val selectedAlbumTracks = remember(sortedTracks, selectedAlbumKey) {
        if (selectedAlbumKey == null) {
            emptyList()
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
            IconButton(onClick = { onRefresh(normalizedQuery) }, enabled = !state.isLoading) {
                Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = echoString(
                        en = "Refresh PC ECHO library",
                        zh = "刷新 PC ECHO 曲库",
                        ja = "PC ECHO ライブラリを更新",
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LibrarySearchBar(
                query = query,
                onQueryChange = onQueryChange,
                expandedWidth = 240.dp,
            )
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
            state.isLoading -> EmptyState(
                echoString(
                    en = "Reading PC ECHO library...",
                    zh = "正在读取 PC ECHO 曲库...",
                    ja = "PC ECHO ライブラリを読み込み中...",
                ),
            )
            !errorMessage.isNullOrBlank() -> EmptyState(errorMessage)
            selectedMode == LinkedLibraryMode.Songs && sortedTracks.isEmpty() -> {
                EmptyState(
                    if (query.isBlank()) {
                        echoString(
                            en = "PC ECHO has no songs to show.",
                            zh = "PC ECHO 暂无可显示歌曲。",
                            ja = "PC ECHO に表示できる曲はありません。",
                        )
                    } else {
                        echoString(
                            en = "PC ECHO has no matching songs.",
                            zh = "PC ECHO 没有匹配的歌曲。",
                            ja = "PC ECHO に一致する曲はありません。",
                        )
                    },
                )
            }
            selectedMode == LinkedLibraryMode.Albums && albums.isEmpty() -> {
                EmptyState(
                    if (query.isBlank()) {
                        echoString(
                            en = "PC ECHO has no albums to show.",
                            zh = "PC ECHO 暂无可显示专辑。",
                            ja = "PC ECHO に表示できるアルバムはありません。",
                        )
                    } else {
                        echoString(
                            en = "PC ECHO has no matching albums.",
                            zh = "PC ECHO 没有匹配的专辑。",
                            ja = "PC ECHO に一致するアルバムはありません。",
                        )
                    },
                )
            }
            selectedMode == LinkedLibraryMode.Artists && artists.isEmpty() -> {
                EmptyState(
                    if (query.isBlank()) {
                        echoString(
                            en = "PC ECHO has no artists to show.",
                            zh = "PC ECHO 暂无可显示艺术家。",
                            ja = "PC ECHO に表示できるアーティストはありません。",
                        )
                    } else {
                        echoString(
                            en = "PC ECHO has no matching artists.",
                            zh = "PC ECHO 没有匹配的艺术家。",
                            ja = "PC ECHO に一致するアーティストはありません。",
                        )
                    },
                )
            }
            selectedMode == LinkedLibraryMode.Playlists && filteredPlaylists.isEmpty() -> {
                EmptyState(
                    if (query.isBlank()) {
                        echoString(
                            en = "PC ECHO has no playlists to show.",
                            zh = "PC ECHO 暂无可显示歌单。",
                            ja = "PC ECHO に表示できるプレイリストはありません。",
                        )
                    } else {
                        echoString(
                            en = "PC ECHO has no matching playlists.",
                            zh = "PC ECHO 没有匹配的歌单。",
                            ja = "PC ECHO に一致するプレイリストはありません。",
                        )
                    },
                )
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
                    mode.label(),
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
            .padding(start = 16.dp, end = 16.dp, top = LinkedLibraryHeaderTopPadding),
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = EchoContentMaxWidth)
                .fillMaxSize()
                .align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(LinkedLibraryHeaderRowHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    actions()
                }
            }
            Spacer(Modifier.height(LinkedLibraryHeaderBottomSpacing))
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
                    contentDescription = echoString(
                        en = "Back to PC ECHO playlists",
                        zh = "返回 PC ECHO 歌单",
                        ja = "PC ECHO のプレイリストに戻る",
                    ),
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
            isLoading -> EmptyState(
                echoString(
                    en = "Reading PC ECHO playlist...",
                    zh = "正在读取 PC ECHO 歌单...",
                    ja = "PC ECHO のプレイリストを読み込み中...",
                ),
            )
            tracks.isEmpty() && !error.isNullOrBlank() -> EmptyState(error)
            tracks.isEmpty() -> EmptyState(
                echoString(
                    en = "This PC ECHO playlist has no playable tracks yet.",
                    zh = "这个 PC ECHO 歌单暂时没有可播放曲目。",
                    ja = "この PC ECHO のプレイリストには再生できる曲がありません。",
                ),
            )
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
    return echoString(
        en = "${playlist.trackCount} tracks · $source",
        zh = "${playlist.trackCount} 首 · $source",
        ja = "${playlist.trackCount} 曲 · $source",
    )
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
                contentDescription = echoString(
                    en = "Set track sort order",
                    zh = "设置歌曲排序",
                    ja = "曲の並び順を設定",
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            LibraryTrackSortMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label()) },
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
    showScanResultBanner: Boolean,
    selectedSource: LibrarySourceMode,
    linkedLibraryAvailable: Boolean,
    onSelectSource: (LibrarySourceMode) -> Unit,
    selectedMode: LibraryViewMode,
    selectedSortMode: LibraryTrackSortMode,
    onSelectMode: (LibraryViewMode) -> Unit,
    onSortModeChange: (LibraryTrackSortMode) -> Unit,
) {
    if (showScanResultBanner) {
        LibraryScanResultBanner(scanState)
    }
    LibraryPagerTabs(
        selectedMode = selectedMode,
        onSelectMode = onSelectMode,
    )
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
