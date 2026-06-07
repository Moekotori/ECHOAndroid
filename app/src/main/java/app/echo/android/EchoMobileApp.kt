package app.echo.android

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import app.echo.android.connect.EchoRemoteClient
import app.echo.android.connect.EchoRemoteCommand
import app.echo.android.connect.EchoRemoteEndpoint
import app.echo.android.connect.EchoRemoteConnectionState
import app.echo.android.connect.EchoRemotePlaybackState
import app.echo.android.data.LibraryTrackEntity
import app.echo.android.design.EchoColors
import app.echo.android.design.EchoIconBadge
import app.echo.android.design.EchoMetricTile
import app.echo.android.design.EchoMobileTheme
import app.echo.android.design.EchoPanel
import app.echo.android.design.EchoPlaceholderLine
import app.echo.android.design.EchoSectionTitle
import app.echo.android.design.EchoSegmentChip
import app.echo.android.design.EchoTextButton
import app.echo.android.playback.EchoPlaybackState
import app.echo.android.playback.EchoPlaybackStatus
import app.echo.android.playback.EchoRepeatMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class EchoTab(
    val label: String,
    val icon: ImageVector,
) {
    Now("播放", Icons.Rounded.MusicNote),
    Library("曲库", Icons.Rounded.LibraryMusic),
    Connect("连接", Icons.Rounded.Devices),
    Diagnostics("状态", Icons.Rounded.GraphicEq),
}

private enum class LibraryViewMode(
    val label: String,
    val icon: ImageVector,
) {
    Songs("歌曲", Icons.AutoMirrored.Rounded.QueueMusic),
    Folders("文件夹", Icons.Rounded.LibraryMusic),
    Albums("专辑", Icons.Rounded.LibraryMusic),
    Artists("艺术家", Icons.Rounded.Person),
    Cloud("网盘", Icons.Rounded.CloudQueue),
}

private data class LibraryAlbumSummary(
    val title: String,
    val artist: String,
    val artworkUri: String?,
    val tracks: List<LibraryTrackEntity>,
)

private data class LibraryArtistSummary(
    val name: String,
    val artworkUri: String?,
    val albumCount: Int,
    val tracks: List<LibraryTrackEntity>,
)

private val EchoContentMaxWidth = 560.dp

// === 默认主题：Roon 风格（Shakespeare 蓝 + 中性炭灰）。多主题切换时改这一组即可 ===
private val EchoAccent = Color(0xFF4DB7E8)
private val EchoAccentText = Color(0xFF1E88C8)
private val EchoAccentDeep = Color(0xFF7C6CF2)
private val EchoBgTop = Color(0xFFFDFEFF)
private val EchoBgMid = Color(0xFFF0F8FF)
private val EchoBgBottom = Color(0xFFEAF2FF)
private val RoonBlue = Color(0xFF5A6CFF)
private val RoonInk = Color(0xFF25242A)
private val RoonMuted = Color(0xFF6D6D73)
private val RoonPaper = Color(0xFFFDFEFF)
private val RoonPanel = Color(0xFFF4F8FF)
private val EchoHomeBlue = Color(0xFF4DB7E8)
private val EchoHomeBlueDeep = Color(0xFF7C6CF2)
private val EchoHomeMist = Color(0xFFF4F8FF)
private val EchoGlassBorder = Color(0xCCFFFFFF)
private val EchoSoftLine = Color(0xFFD8E8F3)

@Composable
fun EchoMobileApp(viewModel: EchoAndroidViewModel = viewModel()) {
    val context = LocalContext.current
    val permission = remember { audioPermissionName() }
    var hasAudioPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasAudioPermission = granted
        if (granted) viewModel.refreshLibrary()
    }

    val remoteClient = remember { EchoRemoteClient() }
    val remoteStatus by remoteClient.status.collectAsStateWithLifecycle()
    val playbackStatus by viewModel.playbackStatus.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val tracks = viewModel.tracks.collectAsLazyPagingItems()
    val homeLoadedTracks = tracks.itemSnapshotList.items
    val homeAlbumCount = remember(homeLoadedTracks) { buildAlbumSummaries(homeLoadedTracks).size }
    val homeArtistCount = remember(homeLoadedTracks) { buildArtistSummaries(homeLoadedTracks).size }
    var selectedTab by remember { mutableIntStateOf(EchoTab.Now.ordinal) }

    LaunchedEffect(hasAudioPermission) {
        if (hasAudioPermission && scanState.lastScanCount == null && !scanState.isScanning) {
            viewModel.refreshLibrary()
        }
    }

    val darkTheme = isSystemInDarkTheme()

    EchoMobileTheme(darkTheme = darkTheme) {
        Box(Modifier.fillMaxSize()) {
            EchoGlassBackground(Modifier.fillMaxSize())
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                val dockOnLightSurface = true
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    MiniPlayer(
                        status = playbackStatus,
                        onPlayPause = viewModel::playPause,
                        modifier = Modifier
                            .widthIn(max = EchoContentMaxWidth)
                            .fillMaxWidth(),
                    )
                    BottomDock(
                        selectedTab = selectedTab,
                        onLightSurface = dockOnLightSurface,
                        onSelectTab = { selectedTab = it },
                        modifier = Modifier
                            .widthIn(max = EchoContentMaxWidth)
                            .fillMaxWidth(),
                    )
                }
            },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                key(selectedTab) {
                    when (EchoTab.entries[selectedTab]) {
                        EchoTab.Library -> LibraryScreen(
                            hasPermission = hasAudioPermission,
                            scanState = scanState,
                            tracks = tracks,
                            onRequestPermission = { permissionLauncher.launch(permission) },
                            onScan = viewModel::refreshLibrary,
                            onPlayQueue = viewModel::playQueue,
                        )

                        EchoTab.Now -> NowPlayingScreen(
                            status = playbackStatus,
                            trackCount = tracks.itemCount,
                            albumCount = homeAlbumCount,
                            artistCount = homeArtistCount,
                            onPlayPause = viewModel::playPause,
                            onNext = viewModel::skipNext,
                            onPrevious = viewModel::skipPrevious,
                            onCycleRepeatMode = viewModel::cycleRepeatMode,
                            onToggleShuffle = viewModel::toggleShuffle,
                            onOpenLibrary = { selectedTab = EchoTab.Library.ordinal },
                            onOpenConnect = { selectedTab = EchoTab.Connect.ordinal },
                        )

                        EchoTab.Connect -> ConnectScreen(
                            remoteState = remoteStatus.connectionState,
                            pcTitle = remoteStatus.endpoint?.name ?: "PC ECHO",
                            trackTitle = remoteStatus.playback.track?.title ?: "未连接",
                            trackArtist = remoteStatus.playback.track?.artist ?: "点按配对",
                            isPlaying = remoteStatus.playback.state == EchoRemotePlaybackState.Playing,
                            onPairDemo = {
                                remoteClient.pair(
                                    EchoRemoteEndpoint(
                                        id = "echo-pc-demo",
                                        name = "PC ECHO 演示",
                                        host = "192.168.1.12",
                                        port = 26789,
                                        token = "demo-token-echo-remote",
                                    ),
                                )
                            },
                            onPlayPause = { remoteClient.send(EchoRemoteCommand.PlayPause) },
                            onNext = { remoteClient.send(EchoRemoteCommand.Next) },
                            onDisconnect = remoteClient::disconnect,
                        )

                        EchoTab.Diagnostics -> DiagnosticsScreen(status = playbackStatus)
                    }
                }
                }
            }
        }
    }
}
}

@Composable
private fun LibraryScreen(
    hasPermission: Boolean,
    scanState: LibraryScanUiState,
    tracks: LazyPagingItems<LibraryTrackEntity>,
    onRequestPermission: () -> Unit,
    onScan: () -> Unit,
    onPlayQueue: (List<LibraryTrackEntity>, Int) -> Unit,
) {
    val loadedTracks = tracks.itemSnapshotList.items
    val albums = remember(loadedTracks) { buildAlbumSummaries(loadedTracks) }
    val artists = remember(loadedTracks) { buildArtistSummaries(loadedTracks) }
    val pagerState = rememberPagerState(pageCount = { LibraryViewMode.entries.size })
    val scope = rememberCoroutineScope()
    PageChrome(
        title = "曲库",
        subtitle = null,
        badge = "本地",
        showBrand = false,
        compactHeader = true,
        actions = {
            LibraryScanAction(
                hasPermission = hasPermission,
                scanState = scanState,
                onRequestPermission = onRequestPermission,
                onScan = onScan,
            )
        },
    ) {
        when {
            !hasPermission -> EmptyState("授权后即可索引本机音乐。")
            scanState.isScanning || tracks.loadState.refresh is LoadState.Loading -> EmptyState("正在扫描音频文件...")
            tracks.loadState.refresh is LoadState.Error -> EmptyState("曲库查询失败。")
            else -> {
                LibraryPagerTabs(
                    selectedMode = LibraryViewMode.entries[pagerState.currentPage],
                    onSelectMode = { mode ->
                        scope.launch { pagerState.animateScrollToPage(mode.ordinal) }
                    },
                )
                if (tracks.itemCount > 0) {
                    LibraryOverview(
                        trackCount = tracks.itemCount,
                        albumCount = albums.size,
                        artistCount = artists.size,
                    )
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                ) { page ->
                    when (LibraryViewMode.entries[page]) {
                        LibraryViewMode.Songs -> {
                            if (tracks.itemCount == 0) {
                                LibraryBootstrapState()
                            } else {
                                TrackList(
                                    tracks = tracks,
                                    onPlayQueue = onPlayQueue,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }

                        LibraryViewMode.Folders -> LibraryPlaceholderPage(
                            title = "文件夹视图",
                            subtitle = "按存储路径浏览本机音乐会放在这里。",
                        )

                        LibraryViewMode.Albums -> AlbumWall(
                            albums = albums,
                            onPlayAlbum = { albumTracks -> onPlayQueue(albumTracks, 0) },
                            modifier = Modifier.fillMaxSize(),
                        )

                        LibraryViewMode.Artists -> ArtistWall(
                            artists = artists,
                            onPlayArtist = { artistTracks -> onPlayQueue(artistTracks, 0) },
                            modifier = Modifier.fillMaxSize(),
                        )

                        LibraryViewMode.Cloud -> LibraryPlaceholderPage(
                            title = "网盘音乐",
                            subtitle = "之后可以接入云端曲库和同步播放列表。",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryPagerTabs(
    selectedMode: LibraryViewMode,
    onSelectMode: (LibraryViewMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 0.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LibraryViewMode.entries.forEach { mode ->
            val selected = selectedMode == mode
            Text(
                text = mode.label,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onSelectMode(mode) }
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                color = if (selected) EchoAccentText else RoonMuted,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun LibraryPlaceholderPage(
    title: String,
    subtitle: String,
) {
    EchoPanel(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EchoIconBadge(Icons.Rounded.LibraryMusic)
            Text(title, color = RoonInk, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, color = RoonMuted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun LibraryOverview(
    trackCount: Int,
    albumCount: Int,
    artistCount: Int,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.72f),
                        EchoHomeMist.copy(alpha = 0.60f),
                        EchoAccentDeep.copy(alpha = 0.08f),
                    ),
                ),
            )
            .border(BorderStroke(1.dp, EchoGlassBorder.copy(alpha = 0.86f)), RoundedCornerShape(22.dp))
            .padding(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            LibraryMetric("歌曲", trackCount.toString(), Modifier.weight(1f))
            LibraryMetric("专辑", albumCount.toString(), Modifier.weight(1f))
            LibraryMetric("艺人", artistCount.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun LibraryMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, color = RoonInk, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, color = RoonMuted, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun LibraryViewSwitcher(
    selectedMode: LibraryViewMode,
    onSelectMode: (LibraryViewMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.54f))
            .border(BorderStroke(1.dp, EchoGlassBorder.copy(alpha = 0.74f)), RoundedCornerShape(18.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LibraryViewMode.entries.forEach { mode ->
            val selected = selectedMode == mode
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) EchoHomeBlue.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable { onSelectMode(mode) }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    mode.icon,
                    contentDescription = mode.label,
                    tint = if (selected) EchoAccentText else RoonMuted,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    mode.label,
                    color = if (selected) RoonInk else RoonMuted,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LibraryViewModeMenu(
    selectedMode: LibraryViewMode,
    onSelectMode: (LibraryViewMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.62f))
                .border(BorderStroke(1.dp, EchoGlassBorder), RoundedCornerShape(14.dp))
                .clickable { expanded = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                selectedMode.icon,
                contentDescription = "切换曲库显示方式",
                tint = EchoHomeBlue,
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = Color.White,
        ) {
            LibraryViewMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Text(
                            mode.label,
                            color = if (mode == selectedMode) EchoHomeBlue else RoonInk,
                            fontWeight = if (mode == selectedMode) FontWeight.Bold else FontWeight.SemiBold,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            mode.icon,
                            contentDescription = null,
                            tint = if (mode == selectedMode) EchoHomeBlue else RoonMuted,
                        )
                    },
                    onClick = {
                        onSelectMode(mode)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun AlbumWall(
    albums: List<LibraryAlbumSummary>,
    onPlayAlbum: (List<LibraryTrackEntity>) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (albums.isEmpty()) {
        EmptyState("当前加载范围内还没有可展示的专辑。")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 142.dp),
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 10.dp),
    ) {
        gridItems(albums, key = { it.title + it.artist }) { album ->
            AlbumWallCard(album = album, onClick = { onPlayAlbum(album.tracks) })
        }
    }
}

@Composable
private fun AlbumWallCard(
    album: LibraryAlbumSummary,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.66f))
            .border(BorderStroke(1.dp, EchoGlassBorder.copy(alpha = 0.86f)), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        ArtworkTile(
            artworkUri = album.artworkUri,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            accent = EchoAccent,
            showSignal = album.artworkUri == null,
            cornerRadius = 14.dp,
            elevation = 6.dp,
        )
        Text(album.title, color = RoonInk, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(album.artist, color = RoonMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${album.tracks.size} 首", color = EchoAccentText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ArtistWall(
    artists: List<LibraryArtistSummary>,
    onPlayArtist: (List<LibraryTrackEntity>) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (artists.isEmpty()) {
        EmptyState("当前加载范围内还没有可展示的艺人。")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 158.dp),
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 10.dp),
    ) {
        gridItems(artists, key = { it.name }) { artist ->
            ArtistWallCard(artist = artist, onClick = { onPlayArtist(artist.tracks) })
        }
    }
}

@Composable
private fun ArtistWallCard(
    artist: LibraryArtistSummary,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .heightIn(min = 152.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.66f),
                        EchoAccent.copy(alpha = 0.18f),
                        EchoAccentDeep.copy(alpha = 0.08f),
                    ),
                ),
            )
            .border(BorderStroke(1.dp, EchoGlassBorder.copy(alpha = 0.84f)), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ArtworkTile(
                artworkUri = artist.artworkUri,
                modifier = Modifier.size(70.dp),
                accent = EchoColors.Coral,
                showSignal = artist.artworkUri == null,
                cornerRadius = 35.dp,
                elevation = 5.dp,
            )
            Text(artist.name, color = RoonInk, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${artist.albumCount} 张专辑 / ${artist.tracks.size} 首", color = RoonMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LibraryScanAction(
    hasPermission: Boolean,
    scanState: LibraryScanUiState,
    onRequestPermission: () -> Unit,
    onScan: () -> Unit,
) {
    val description = when {
        !hasPermission -> "授权音乐权限"
        scanState.isScanning -> "正在扫描曲库"
        else -> "扫描曲库"
    }
    Box(
        modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.62f))
                .border(BorderStroke(1.dp, EchoGlassBorder), RoundedCornerShape(14.dp))
            .clickable(
                enabled = !scanState.isScanning,
                onClick = if (hasPermission) onScan else onRequestPermission,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Refresh,
            contentDescription = description,
            tint = if (scanState.error != null) Color(0xFFE0796E) else EchoHomeBlue,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun LibraryBootstrapState() {
    EchoPanel(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EchoIconBadge(Icons.Rounded.LibraryMusic)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("暂无本机歌曲", color = RoonInk, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "点右上角扫描本机音乐。",
                    color = RoonMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun TrackList(
    tracks: LazyPagingItems<LibraryTrackEntity>,
    onPlayQueue: (List<LibraryTrackEntity>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 10.dp),
    ) {
        items(tracks.itemCount) { index ->
            tracks[index]?.let { track ->
                TrackRow(
                    track = track,
                    onClick = {
                        val loadedQueue = tracks.itemSnapshotList.items
                        val queueIndex = loadedQueue.indexOfFirst { it.id == track.id }.takeIf { it >= 0 } ?: index
                        onPlayQueue(loadedQueue.ifEmpty { listOf(track) }, queueIndex)
                    },
                )
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: LibraryTrackEntity,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.66f))
            .border(BorderStroke(1.dp, EchoGlassBorder.copy(alpha = 0.84f)), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ArtworkTile(track.artworkUri, Modifier.size(50.dp), accent = EchoAccent, cornerRadius = 13.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    track.title,
                    color = RoonInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    trackSubtitle(track),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = RoonMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                formatDuration(track.durationMs),
                color = RoonMuted,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun NowPlayingScreen(
    status: EchoPlaybackStatus,
    trackCount: Int,
    albumCount: Int,
    artistCount: Int,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleShuffle: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenConnect: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val compactViewport = configuration.screenHeightDp < 620 ||
        configuration.screenWidthDp > configuration.screenHeightDp
    val scrollState = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(scrollState)
                .padding(bottom = 212.dp),
        ) {
            RoonHomeHeader(
                status = status,
                compact = compactViewport,
                onOpenLibrary = onOpenLibrary,
            )
            Box(Modifier.padding(horizontal = 24.dp)) {
                LibraryOverview(
                    trackCount = trackCount,
                    albumCount = albumCount,
                    artistCount = artistCount,
                )
            }
            Spacer(Modifier.height(if (compactViewport) 14.dp else 22.dp))
            RoonRecentActivitySection(
                status = status,
                onPlayPause = onPlayPause,
                onOpenLibrary = onOpenLibrary,
            )
            Spacer(Modifier.height(if (compactViewport) 44.dp else 62.dp))
        }
    }
}

@Composable
private fun RoonHomeHeader(
    status: EchoPlaybackStatus,
    compact: Boolean,
    onOpenLibrary: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = if (compact) 10.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 22.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenLibrary, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Rounded.Menu, contentDescription = "打开曲库", tint = RoonMuted, modifier = Modifier.size(32.dp))
                }
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, tint = Color(0xFFD5D5D7), modifier = Modifier.size(30.dp))
            }
            Icon(Icons.Rounded.Search, contentDescription = "搜索", tint = RoonMuted, modifier = Modifier.size(34.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = Color.White.copy(alpha = 0.58f),
                border = BorderStroke(1.dp, EchoGlassBorder),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = RoonMuted, modifier = Modifier.size(20.dp))
                    Text("搜索本机音乐、专辑、歌手", color = RoonMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (false) {
                Text(
                text = status.track?.title ?: "让本机音乐醒过来",
                color = RoonMuted,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RoonRecentActivitySection(
    status: EchoPlaybackStatus,
    onPlayPause: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(30.dp),
                ambientColor = EchoAccentDeep.copy(alpha = 0.16f),
                spotColor = EchoHomeBlue.copy(alpha = 0.14f),
            )
            .clip(RoundedCornerShape(32.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.72f),
                        EchoHomeMist.copy(alpha = 0.62f),
                        EchoHomeBlue.copy(alpha = 0.13f),
                        EchoAccentDeep.copy(alpha = 0.08f),
                    ),
                ),
            )
            .border(BorderStroke(1.dp, EchoGlassBorder.copy(alpha = 0.90f)), RoundedCornerShape(32.dp))
            .padding(top = 18.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "最近活动",
                    color = RoonInk,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (false) {
                    Text(
                    "本机会话",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = EchoAccentDeep.copy(alpha = 0.14f),
                    contentColor = EchoAccentText,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.62f)),
                ) {
                    Text(
                        "曲库",
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.Bottom) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("正在播放", color = EchoAccentText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Box(
                        Modifier
                            .width(74.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(EchoAccent),
                    )
                }
                Text("最近导入", color = RoonMuted, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RoonRecentActivityCard(
                title = status.track?.title ?: "本地音乐",
                subtitle = status.track?.artist ?: "从曲库选择",
                artworkUri = status.track?.artworkUri?.toString(),
                accent = EchoAccent,
                onClick = if (status.track != null) onPlayPause else onOpenLibrary,
            )
            RoonRecentActivityCard(
                title = "每日推荐",
                subtitle = "按你的本机曲库",
                artworkUri = null,
                accent = EchoColors.Brass,
                onClick = onOpenLibrary,
            )
            RoonRecentActivityCard(
                title = "PC ECHO",
                subtitle = "桌面接力播放",
                artworkUri = null,
                accent = EchoColors.Coral,
                onClick = onOpenLibrary,
            )
        }
    }
}

@Composable
private fun RoonRecentActivityCard(
    title: String,
    subtitle: String,
    artworkUri: String?,
    accent: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(126.dp)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box {
            ArtworkTile(
                artworkUri = artworkUri,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                accent = accent,
                showSignal = artworkUri == null,
                cornerRadius = 14.dp,
                elevation = 4.dp,
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(9.dp),
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.62f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.76f)),
            ) {
                Icon(
                    Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = EchoHomeBlue,
                    modifier = Modifier
                        .padding(6.dp)
                        .size(22.dp),
                )
            }
        }
        Text(
            title,
            color = RoonInk,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            subtitle,
            color = RoonMuted,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RoonListenLaterPanel(onOpenConnect: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(Color.White.copy(alpha = 0.66f))
            .border(BorderStroke(1.dp, EchoGlassBorder.copy(alpha = 0.82f)), RoundedCornerShape(32.dp))
            .padding(horizontal = 22.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "稍后聆听",
            color = RoonInk,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "为本机曲库留一条线索",
            color = RoonInk,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "把想听的专辑、歌手和曲目先放在这里，稍后继续。",
            color = RoonMuted,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Surface(
            modifier = Modifier
                .padding(top = 8.dp)
                .widthIn(min = 230.dp)
                .clickable(onClick = onOpenConnect),
            shape = RoundedCornerShape(28.dp),
            color = EchoHomeBlue,
            contentColor = Color.White,
        ) {
            Text(
                "连接 PC ECHO",
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun HomeTopChrome(onOpenLibrary: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIconButton(
            icon = Icons.Rounded.Menu,
            description = "打开曲库",
            onClick = onOpenLibrary,
        )
        GlassSurface(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 46.dp),
            alpha = 0.18f,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.76f), modifier = Modifier.size(20.dp))
                Text("搜索本机音乐...", color = Color.White.copy(alpha = 0.72f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun HomeGreeting(status: EchoPlaybackStatus) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Good Evening",
            color = Color.White.copy(alpha = 0.68f),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            status.track?.artist?.takeIf { it.isNotBlank() } ?: "ECHO Mobile",
            color = Color.White,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (status.track != null) "不是所有的旅途都有终点" else "让本机音乐醒过来",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DailyRecommendationCard(
    status: EchoPlaybackStatus,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 158.dp)
            .shadow(elevation = 14.dp, shape = RoundedCornerShape(24.dp), clip = false)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF2C3A47),
                        Color(0xFF24303B),
                        Color(0xFF1C2730),
                        EchoAccentDeep.copy(alpha = 0.55f),
                    ),
                ),
            ),
    ) {
        AmbientPlanet(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 14.dp, end = 34.dp),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                if (status.track != null) "继续播放" else "每日推荐",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                status.track?.title ?: "发现好音乐",
                color = Color.White.copy(alpha = 0.88f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun HomeModeRibbon(
    repeatMode: EchoRepeatMode,
    shuffleEnabled: Boolean,
    onCycleRepeatMode: () -> Unit,
    onToggleShuffle: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenConnect: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        HomeModeChip(
            icon = if (repeatMode == EchoRepeatMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            label = repeatModeLabel(repeatMode),
            selected = repeatMode != EchoRepeatMode.Off,
            onClick = onCycleRepeatMode,
            modifier = Modifier.weight(1f),
        )
        HomeModeChip(
            icon = Icons.Rounded.Shuffle,
            label = if (shuffleEnabled) "随机" else "顺序",
            selected = shuffleEnabled,
            onClick = onToggleShuffle,
            modifier = Modifier.weight(1f),
        )
        HomeModeChip(
            icon = Icons.Rounded.LibraryMusic,
            label = "曲库",
            selected = false,
            onClick = onOpenLibrary,
            modifier = Modifier.weight(1f),
        )
        HomeModeChip(
            icon = Icons.Rounded.Devices,
            label = "接力",
            selected = false,
            onClick = onOpenConnect,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HomeModeChip(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .heightIn(min = 58.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = if (selected) 0.24f else 0.14f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = if (selected) 0.42f else 0.24f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = label, tint = if (selected) EchoAccent else Color.White.copy(alpha = 0.82f), modifier = Modifier.size(21.dp))
            Text(label, color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PlaybackQueuePanel(
    status: EchoPlaybackStatus,
    compact: Boolean,
    onOpenLibrary: () -> Unit,
    onOpenConnect: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleShuffle: () -> Unit,
) {
    val hasTrack = status.track != null
    EchoPanel(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(if (compact) 12.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
        ) {
            EchoSectionTitle(
                if (hasTrack) "播放队列" else "准备播放",
                status.track?.album ?: "队列为空",
            )
            QueuePreviewList(status = status, compact = compact)
            PlaybackModeControls(
                repeatMode = status.repeatMode,
                shuffleEnabled = status.shuffleEnabled,
                onCycleRepeatMode = onCycleRepeatMode,
                onToggleShuffle = onToggleShuffle,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                PlaybackActionCard(
                    icon = Icons.Rounded.LibraryMusic,
                    title = if (hasTrack) "回到曲库" else "选择曲目",
                    detail = if (hasTrack) "调整本地队列" else "从本机音乐开始",
                    onClick = onOpenLibrary,
                    modifier = Modifier.weight(1f),
                )
                PlaybackActionCard(
                    icon = Icons.Rounded.Devices,
                    title = "PC 接力",
                    detail = if (hasTrack) "切换到 PC ECHO" else "配对后远程播放",
                    onClick = onOpenConnect,
                    modifier = Modifier.weight(1f),
                )
            }
            if (!compact) {
                PlaybackHandoffFlow(active = hasTrack)
                EchoPlaceholderLine(if (hasTrack) "下一步补歌词、循环与队列重排" else "歌词、循环与队列重排位已预留")
            }
        }
    }
}

@Composable
private fun QueuePreviewList(
    status: EchoPlaybackStatus,
    compact: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        QueuePreviewItem(
            icon = Icons.Rounded.MusicNote,
            label = "当前",
            title = status.track?.title ?: "暂无播放",
            detail = status.track?.artist ?: "从曲库选择一首歌",
            active = status.track != null,
        )
        if (!compact) {
            QueuePreviewItem(
                icon = Icons.Rounded.LibraryMusic,
                label = "下一首",
                title = "智能队列",
                detail = if (status.track != null) "跟随本机队列继续播放" else "选歌后显示即将播放",
                active = false,
            )
            QueuePreviewItem(
                icon = Icons.Rounded.Devices,
                label = "接力",
                title = "PC ECHO",
                detail = if (status.track != null) "可切换到桌面输出" else "配对后接管远程播放",
                active = false,
            )
        }
    }
}

@Composable
private fun PlaybackModeControls(
    repeatMode: EchoRepeatMode,
    shuffleEnabled: Boolean,
    onCycleRepeatMode: () -> Unit,
    onToggleShuffle: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        PlaybackModeButton(
            icon = if (repeatMode == EchoRepeatMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            title = repeatModeLabel(repeatMode),
            detail = "点按切换",
            selected = repeatMode != EchoRepeatMode.Off,
            onClick = onCycleRepeatMode,
            modifier = Modifier.weight(1f),
        )
        PlaybackModeButton(
            icon = Icons.Rounded.Shuffle,
            title = if (shuffleEnabled) "随机开启" else "顺序播放",
            detail = if (shuffleEnabled) "队列随机" else "按队列顺序",
            selected = shuffleEnabled,
            onClick = onToggleShuffle,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PlaybackModeButton(
    icon: ImageVector,
    title: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.60f),
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = title,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(
                    detail,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun QueuePreviewItem(
    icon: ImageVector,
    label: String,
    title: String,
    detail: String,
    active: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.56f),
        border = BorderStroke(
            1.dp,
            if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = if (active) 0.16f else 0.10f),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(8.dp).size(20.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(label, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(
                    detail,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun PlaybackHandoffFlow(active: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)),
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("接力路径", fontWeight = FontWeight.SemiBold)
                Text(
                    if (active) "可接力" else "待选择曲目",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                HandoffStep("1", "本机播放", selected = true, modifier = Modifier.weight(1f))
                HandoffStep("2", "连接 PC", selected = active, modifier = Modifier.weight(1f))
                HandoffStep("3", "PC 输出", selected = active, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HandoffStep(
    number: String,
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.60f),
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(number, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PlaybackActionCard(
    icon: ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)),
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                detail,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NowPlayingHero(
    status: EchoPlaybackStatus,
    compact: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    val heroBrush = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = 0.72f),
            EchoHomeMist.copy(alpha = 0.58f),
            EchoHomeBlue.copy(alpha = 0.12f),
        ),
    )
    if (compact) {
        CompactNowPlayingHero(
            status = status,
            heroBrush = heroBrush,
            onPlayPause = onPlayPause,
            onNext = onNext,
            onPrevious = onPrevious,
        )
        return
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 274.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(heroBrush)
            .border(BorderStroke(1.dp, EchoGlassBorder.copy(alpha = 0.84f)), RoundedCornerShape(26.dp))
            .padding(18.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "本机会话",
                    style = MaterialTheme.typography.labelSmall,
                    color = EchoAccentText,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    playbackStateLabel(status.state),
                    style = MaterialTheme.typography.labelMedium,
                    color = RoonMuted,
                )
            }
            ArtworkTile(
                artworkUri = status.track?.artworkUri?.toString(),
                modifier = Modifier
                    .fillMaxWidth(0.44f)
                    .aspectRatio(1f),
                accent = EchoAccent,
                showSignal = true,
                cornerRadius = 24.dp,
                elevation = 18.dp,
            )
            Text(
                status.track?.title ?: "暂无播放",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = RoonInk,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                status.track?.artist ?: "从曲库选择一首歌",
                color = RoonMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            PlaybackProgress(status.positionMs, status.durationMs, light = false)
            TransportControls(
                isPlaying = status.isPlaying,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
            )
        }
    }
}

@Composable
private fun CompactNowPlayingHero(
    status: EchoPlaybackStatus,
    heroBrush: Brush,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(heroBrush)
            .border(BorderStroke(1.dp, EchoGlassBorder.copy(alpha = 0.82f)), RoundedCornerShape(22.dp))
            .padding(14.dp),
    ) {
        val artworkSize = if (maxWidth < 420.dp) 76.dp else 92.dp
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtworkTile(
                artworkUri = status.track?.artworkUri?.toString(),
                modifier = Modifier.size(artworkSize),
                accent = EchoAccent,
                showSignal = true,
                cornerRadius = 18.dp,
                elevation = 12.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            "本机会话",
                            style = MaterialTheme.typography.labelSmall,
                            color = EchoAccentText,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            playbackStateLabel(status.state),
                            style = MaterialTheme.typography.labelMedium,
                            color = RoonMuted,
                        )
                    }
                    TransportControls(
                        isPlaying = status.isPlaying,
                        onPlayPause = onPlayPause,
                        onNext = onNext,
                        onPrevious = onPrevious,
                    )
                }
                Text(
                    status.track?.title ?: "暂无播放",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = RoonInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    status.track?.artist ?: "从曲库选择一首歌",
                    color = RoonMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                PlaybackProgress(status.positionMs, status.durationMs, light = false)
            }
        }
    }
}

@Composable
private fun HeroMetaRail(status: EchoPlaybackStatus) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactFact("输出", status.diagnostics.outputRoute, Modifier.weight(1.25f))
            CompactFact("处理", if (status.diagnostics.offloadActive) "硬件直通" else "清晰", Modifier.weight(1f))
        }
    }
}

@Composable
private fun CompactFact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ConnectScreen(
    remoteState: EchoRemoteConnectionState,
    pcTitle: String,
    trackTitle: String,
    trackArtist: String,
    isPlaying: Boolean,
    onPairDemo: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val connected = remoteState == EchoRemoteConnectionState.Connected
    PageChrome(title = "连接", subtitle = "串流服务 · PC 联动", badge = "互联", scrollable = true) {
        EchoSectionTitle("音乐服务", "连接你的曲库来源")
        Spacer(Modifier.height(12.dp))
        ServiceCard(
            name = "网易云音乐",
            subtitle = "歌单 · 每日推荐 · 私人 FM",
            icon = Icons.Rounded.CloudQueue,
            brandColor = Color(0xFFE0243A),
            statusLabel = "即将上线",
            active = false,
            locked = true,
            onClick = {},
        )
        Spacer(Modifier.height(10.dp))
        ServiceCard(
            name = "本地曲库",
            subtitle = "已扫描本机音频文件",
            icon = Icons.Rounded.LibraryMusic,
            brandColor = Color(0xFF35C28E),
            statusLabel = "已连接",
            active = true,
            locked = false,
            onClick = {},
        )
        Spacer(Modifier.height(20.dp))
        EchoSectionTitle("设备联动", if (connected) "手机控制，PC 输出" else "配对后接管 PC ECHO 播放")
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.70f),
                            EchoHomeMist.copy(alpha = 0.62f),
                            EchoHomeBlue.copy(alpha = 0.08f),
                        ),
                    ),
                )
                .border(
                    BorderStroke(1.dp, EchoGlassBorder.copy(alpha = 0.86f)),
                    RoundedCornerShape(20.dp),
                ),
        ) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(13.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(EchoHomeBlue),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Devices,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(25.dp),
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            pcTitle,
                            color = RoonInk,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            remoteConnectionLabel(remoteState),
                            color = RoonMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    ServiceStatusPill(
                        label = if (connected) "已配对" else "未配对",
                        active = connected,
                        locked = false,
                    )
                }
                if (connected) {
                    RemoteNowPlaying(
                        title = trackTitle,
                        artist = trackArtist,
                        isPlaying = isPlaying,
                        controlsEnabled = true,
                        onPlayPause = onPlayPause,
                        onNext = onNext,
                    )
                } else {
                    EchoPlaceholderLine("局域网内发现 PC ECHO 后，输入配对码即可接管播放")
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EchoTextButton(
                        text = if (connected) "已配对" else "配对 PC",
                        onClick = onPairDemo,
                        enabled = !connected,
                    )
                    if (connected) {
                        TextButton(onClick = onDisconnect) {
                            Text("断开", color = EchoHomeBlue)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceCard(
    name: String,
    subtitle: String,
    icon: ImageVector,
    brandColor: Color,
    statusLabel: String,
    active: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.70f),
                        brandColor.copy(alpha = if (locked) 0.06f else 0.12f),
                        EchoHomeMist.copy(alpha = 0.28f),
                    ),
                ),
            )
            .border(
                BorderStroke(1.dp, EchoGlassBorder.copy(alpha = 0.86f)),
                RoundedCornerShape(20.dp),
            )
            .clickable(enabled = !locked, onClick = onClick)
            .padding(15.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(brandColor.copy(alpha = if (locked) 0.55f else 0.95f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(25.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    name,
                    color = RoonInk,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    color = RoonMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ServiceStatusPill(label = statusLabel, active = active, locked = locked)
        }
    }
}

@Composable
private fun ServiceStatusPill(
    label: String,
    active: Boolean,
    locked: Boolean,
) {
    val background = when {
        locked -> Color.White.copy(alpha = 0.52f)
        active -> Color(0xFF35C28E).copy(alpha = 0.22f)
        else -> EchoAccent.copy(alpha = 0.22f)
    }
    val foreground = when {
        locked -> RoonMuted
        active -> Color(0xFF1A9B68)
        else -> EchoAccentText
    }
    Surface(shape = RoundedCornerShape(20.dp), color = background) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                if (locked) Icons.Rounded.Lock else Icons.Rounded.Check,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(13.dp),
            )
            Text(
                label,
                color = foreground,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PcLinkStatusStrip(connected: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.56f),
        border = BorderStroke(1.dp, EchoGlassBorder.copy(alpha = 0.76f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(
                    if (connected) "手机控制，PC 输出" else "等待 PC ECHO 配对",
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (connected) "队列、音量、下一首将进入联动通道" else "配对后显示延迟、输出设备和队列状态",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PcHandoffPanel(connected: Boolean) {
    EchoPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EchoSectionTitle(
                "接力控制台",
                if (connected) "本机和 PC 队列保持同步" else "完成配对后接管 PC 播放",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                EchoSegmentChip("手机控制", selected = true, Modifier.weight(1f))
                EchoSegmentChip("PC 输出", selected = connected, Modifier.weight(1f))
                EchoSegmentChip("队列同步", selected = connected, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                EchoMetricTile("延迟", if (connected) "24ms" else "--", Modifier.weight(1f), detail = "估算")
                EchoMetricTile("音量", if (connected) "同步" else "待机", Modifier.weight(1f), detail = "映射")
                EchoMetricTile("设备", if (connected) "桌面" else "未选", Modifier.weight(1f), detail = "输出")
            }
            EchoPlaceholderLine(
                if (connected) "下一首会同步到 PC ECHO" else "配对后显示 PC 队列和输出设备",
            )
            EchoPlaceholderLine(
                if (connected) "延迟监测和音量映射就绪" else "预留音量、延迟、输出设备联动",
            )
        }
    }
}

@Composable
private fun RemoteNowPlaying(
    title: String,
    artist: String,
    isPlaying: Boolean,
    controlsEnabled: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.58f),
        border = BorderStroke(1.dp, EchoGlassBorder.copy(alpha = 0.76f)),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ArtworkTile(null, Modifier.size(56.dp), accent = EchoColors.Coral, cornerRadius = 14.dp, elevation = 6.dp)
            Column(Modifier.weight(1f)) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onPlayPause, enabled = controlsEnabled) {
                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = "播放或暂停 PC")
            }
            IconButton(onClick = onNext, enabled = controlsEnabled) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "PC 下一首")
            }
        }
    }
}

@Composable
private fun PairingPill(
    number: String,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
        border = BorderStroke(
            1.dp,
            if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = if (active) 0.24f else 0.14f)) {
                Text(
                    number,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DiagnosticsScreen(status: EchoPlaybackStatus) {
    val diagnostics = status.diagnostics
    val bufferSeconds = "${diagnostics.bufferedMs / 1000}s"
    val codec = diagnostics.codec ?: "Media3"
    val lastCommand = commandLabel(diagnostics.lastCommand)
    PageChrome(title = "信号", subtitle = "音频链路与解码状态", badge = "状态", scrollable = true) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SignalHeroCard(
                status = status,
                output = diagnostics.outputRoute,
                codec = codec,
                buffer = bufferSeconds,
                lastCommand = lastCommand,
            )
            SignalFlowPanel(
                codec = codec,
                output = diagnostics.outputRoute,
                offloadActive = diagnostics.offloadActive,
            )
            CurrentStreamPanel(
                status = status,
                lastCommand = lastCommand,
                requestToken = diagnostics.requestToken,
            )
            HealthPanel(status = status)
        }
    }
}

@Composable
private fun SignalHeroCard(
    status: EchoPlaybackStatus,
    output: String,
    codec: String,
    buffer: String,
    lastCommand: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.72f),
                        EchoHomeMist.copy(alpha = 0.64f),
                        EchoHomeBlue.copy(alpha = 0.12f),
                    ),
                ),
            )
            .border(BorderStroke(1.dp, EchoGlassBorder.copy(alpha = 0.86f)), RoundedCornerShape(22.dp))
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "链路总览",
                        color = RoonInk,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (status.isPlaying) "正在输出稳定音频流" else "等待播放，链路保持就绪",
                        color = RoonMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                StatePill(label = playbackStateLabel(status.state), active = status.isPlaying)
            }
            SignalBars(active = status.isPlaying)
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                SignalStatTile("输出", output, EchoAccent, Modifier.weight(1f))
                SignalStatTile("解码", codec, EchoAccent, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                SignalStatTile("缓冲", buffer, Color(0xFF35C28E), Modifier.weight(1f))
                SignalStatTile("命令", lastCommand, EchoAccent, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatePill(label: String, active: Boolean) {
    val accent = if (active) Color(0xFF1A9B68) else EchoAccentText
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = accent.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.34f)),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            color = accent,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SignalStatTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.62f))
            .border(BorderStroke(1.dp, EchoGlassBorder.copy(alpha = 0.80f)), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                label,
                color = accent,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                value,
                color = RoonInk,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SignalFlowPanel(
    codec: String,
    output: String,
    offloadActive: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.64f))
            .border(BorderStroke(1.dp, EchoGlassBorder.copy(alpha = 0.84f)), RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            EchoSectionTitle("链路路径", "从曲库到输出设备")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SignalFlowStage("曲库", "本机", EchoAccent, Modifier.weight(1f))
                FlowArrow()
                SignalFlowStage("解码", codec, EchoAccent, Modifier.weight(1f))
                FlowArrow()
                SignalFlowStage("输出", output, EchoAccent, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FlowChip("本机优先", selected = true, Modifier.weight(1f))
                FlowChip("DSP ${if (offloadActive) "开启" else "关闭"}", selected = offloadActive, Modifier.weight(1f))
                FlowChip("PC 待接力", selected = false, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SignalFlowStage(
    title: String,
    detail: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.22f), accent.copy(alpha = 0.08f)),
                ),
            )
            .border(BorderStroke(1.dp, accent.copy(alpha = 0.32f)), RoundedCornerShape(13.dp))
            .padding(horizontal = 9.dp, vertical = 10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Text(
                detail,
                color = RoonInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun FlowArrow() {
    Text(
        "→",
        modifier = Modifier.padding(horizontal = 4.dp),
        color = RoonMuted,
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun FlowChip(label: String, selected: Boolean, modifier: Modifier = Modifier) {
    val accent = EchoAccent
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) accent.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.54f))
            .border(
                BorderStroke(1.dp, if (selected) accent.copy(alpha = 0.28f) else EchoGlassBorder.copy(alpha = 0.74f)),
                RoundedCornerShape(20.dp),
            )
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) EchoHomeBlue else RoonMuted,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun CurrentStreamPanel(
    status: EchoPlaybackStatus,
    lastCommand: String,
    requestToken: Long,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.64f))
            .border(BorderStroke(1.dp, EchoGlassBorder.copy(alpha = 0.84f)), RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EchoSectionTitle("当前流", status.track?.album ?: "暂无曲目")
            DiagnosticLine("曲目", status.track?.title ?: "无")
            DiagnosticLine("进度", "${formatDuration(status.positionMs)} / ${formatDuration(status.durationMs)}")
            DiagnosticLine("命令", lastCommand)
            DiagnosticLine("令牌", requestToken.toString())
        }
    }
}

@Composable
private fun HealthPanel(status: EchoPlaybackStatus) {
    val diagnostics = status.diagnostics
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.64f))
            .border(BorderStroke(1.dp, EchoGlassBorder.copy(alpha = 0.84f)), RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            EchoSectionTitle("健康", diagnostics.lastError?.message ?: "未记录掉音")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FlowChip("稳定", selected = diagnostics.lastError == null, Modifier.weight(1f))
                FlowChip("本机", selected = true, Modifier.weight(1f))
                FlowChip("接力", selected = false, Modifier.weight(1f))
            }
            EchoPlaceholderLine(if (diagnostics.lastError == null) "解码回退记录为空" else "查看解码回退")
            EchoPlaceholderLine("PC 链路质量待测")
        }
    }
}

@Composable
private fun SignalBars(active: Boolean) {
    val heights = listOf(18.dp, 30.dp, 24.dp, 42.dp, 28.dp, 48.dp, 34.dp, 22.dp, 38.dp, 26.dp, 44.dp, 20.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.46f))
            .border(BorderStroke(1.dp, EchoGlassBorder.copy(alpha = 0.62f)), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            heights.forEachIndexed { index, height ->
                Box(
                    modifier = Modifier
                        .width(5.dp)
                        .height(if (active || index % 2 == 0) height else height * 0.5f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    EchoAccent.copy(alpha = if (active) 0.95f else 0.4f),
                                    EchoAccent.copy(alpha = if (active) 0.7f else 0.3f),
                                ),
                            ),
                        ),
                )
            }
        }
    }
}

@Composable
private fun GlassSurface(
    modifier: Modifier = Modifier,
    alpha: Float = 0.16f,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = Color.White.copy(alpha = alpha),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.32f)),
        content = { content() },
    )
}

@Composable
private fun GlassIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    GlassSurface(modifier = Modifier.size(46.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            IconButton(onClick = onClick) {
                Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(25.dp))
            }
        }
    }
}

@Composable
private fun EchoGlassBackground(modifier: Modifier = Modifier) {
    // Roon 风格：平面中性炭灰，极克制的顶部冷光，无光斑、无星点
    val baseGradient = Brush.verticalGradient(
        listOf(
            EchoBgTop,
            EchoBgMid,
            EchoBgBottom,
        ),
    )
    Canvas(modifier = modifier.background(baseGradient)) {
        val w = size.width
        val h = size.height
        // 顶部一抹极淡的 Roon 蓝冷光，给纯黑一点层次
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(EchoHomeBlue.copy(alpha = 0.22f), Color.Transparent),
                center = Offset(w * 0.08f, h * 0.18f),
                radius = h * 0.40f,
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(EchoAccentDeep.copy(alpha = 0.18f), Color.Transparent),
                center = Offset(w * 0.92f, h * 0.28f),
                radius = h * 0.46f,
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFC7E3).copy(alpha = 0.18f), Color.Transparent),
                center = Offset(w * 0.40f, h * 0.78f),
                radius = h * 0.44f,
            ),
        )
    }
}

@Composable
private fun AmbientPlanet(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(86.dp), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.size(78.dp),
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.16f),
            content = {},
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .height(10.dp),
            shape = RoundedCornerShape(8.dp),
            color = EchoAccent.copy(alpha = 0.55f),
            content = {},
        )
    }
}

@Composable
private fun PageChrome(
    title: String,
    subtitle: String?,
    badge: String = "移动端",
    scrollable: Boolean = false,
    showBrand: Boolean = false,
    compactHeader: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    key(title) {
        val configuration = LocalConfiguration.current
        val compactChrome = configuration.screenHeightDp < 620 ||
            configuration.screenWidthDp > configuration.screenHeightDp
        val contentScroll = if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.66f),
                            EchoHomeMist.copy(alpha = 0.78f),
                            Color(0xFFEAF2FF).copy(alpha = 0.86f),
                        ),
                    ),
                )
                .statusBarsPadding(),
        ) {
            val horizontalPadding = if (maxWidth >= 720.dp) 28.dp else 16.dp
            val topPadding = when {
                compactHeader -> 2.dp
                compactChrome -> 8.dp
                else -> 14.dp
            }
            val headerGap = when {
                compactHeader -> 4.dp
                compactChrome -> 6.dp
                else -> 8.dp
            }
            val contentGap = when {
                compactHeader -> 4.dp
                compactChrome -> 8.dp
                else -> 12.dp
            }
            val titleStyle = when {
                compactHeader -> MaterialTheme.typography.headlineMedium
                compactChrome -> MaterialTheme.typography.headlineSmall
                else -> MaterialTheme.typography.headlineMedium
            }
            Column(
                modifier = Modifier
                    .widthIn(max = EchoContentMaxWidth)
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .then(contentScroll)
                    .padding(start = horizontalPadding, end = horizontalPadding, top = topPadding, bottom = 212.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showBrand) {
                        Text("ECHO 移动端", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    } else {
                        Text(title, style = titleStyle, fontWeight = FontWeight.Bold, color = RoonInk)
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.56f),
                            border = BorderStroke(1.dp, EchoGlassBorder),
                        ) {
                            Text(
                                badge,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = RoonInk,
                            )
                        }
                        actions()
                    }
                }
                if (showBrand) {
                    Spacer(Modifier.height(headerGap))
                    Text(title, style = titleStyle, fontWeight = FontWeight.Bold, color = RoonInk)
                }
                if (subtitle != null) {
                    Text(subtitle, color = RoonMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(contentGap))
                content()
                if (scrollable) {
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun BottomDock(
    selectedTab: Int,
    onLightSurface: Boolean,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(26.dp)
    val dockBackground = if (onLightSurface) {
        Brush.verticalGradient(
            listOf(
                Color(0xFF5D6678).copy(alpha = 0.58f),
                Color(0xFF30394E).copy(alpha = 0.46f),
            ),
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.18f),
                Color.White.copy(alpha = 0.07f),
            ),
        )
    }
    val borderColor = if (onLightSurface) {
        Color.White.copy(alpha = 0.18f)
    } else {
        Color.White.copy(alpha = 0.24f)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (onLightSurface) 18.dp else 8.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = if (onLightSurface) 0.12f else 0.22f),
                spotColor = Color.Black.copy(alpha = if (onLightSurface) 0.10f else 0.18f),
            )
            .clip(shape)
            .background(dockBackground)
            .border(
                BorderStroke(1.dp, borderColor),
                shape,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EchoTab.entries.forEach { tab ->
                DockItem(
                    tab = tab,
                    selected = selectedTab == tab.ordinal,
                    onLightSurface = onLightSurface,
                    onClick = { onSelectTab(tab.ordinal) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DockItem(
    tab: EchoTab,
    selected: Boolean,
    onLightSurface: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = when {
        selected && onLightSurface -> Color(0xFFFFBCD5)
        selected -> EchoAccentText
        onLightSurface -> Color.White.copy(alpha = 0.70f)
        else -> Color.White.copy(alpha = 0.68f)
    }
    val selectedBackground = if (onLightSurface) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.12f),
                Color.White.copy(alpha = 0.06f),
            ),
        )
    } else {
        Brush.verticalGradient(
            listOf(
                EchoAccent.copy(alpha = 0.28f),
                EchoAccentDeep.copy(alpha = 0.12f),
            ),
        )
    }
    val selectedBorder = if (onLightSurface) {
        Color.White.copy(alpha = 0.14f)
    } else {
        EchoAccent.copy(alpha = 0.38f)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 46.dp, minHeight = 30.dp)
                .clip(RoundedCornerShape(15.dp))
                .then(
                    if (selected) {
                        Modifier
                            .background(selectedBackground)
                            .border(
                                BorderStroke(1.dp, selectedBorder),
                                RoundedCornerShape(15.dp),
                            )
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(tab.icon, contentDescription = tab.label, tint = contentColor, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun MiniPlayer(
    status: EchoPlaybackStatus,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 18.dp,
                shape = shape,
                ambientColor = EchoAccentDeep.copy(alpha = 0.16f),
                spotColor = EchoHomeBlue.copy(alpha = 0.12f),
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.50f),
                        EchoHomeMist.copy(alpha = 0.34f),
                        EchoAccentDeep.copy(alpha = 0.14f),
                    ),
                ),
            )
            .border(BorderStroke(1.dp, EchoGlassBorder.copy(alpha = 0.76f)), shape)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ArtworkTile(
                artworkUri = status.track?.artworkUri?.toString(),
                modifier = Modifier.size(52.dp),
                accent = EchoAccent,
                showSignal = status.track?.artworkUri == null,
                cornerRadius = 12.dp,
                elevation = 2.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = EchoAccentDeep.copy(alpha = 0.74f),
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        status.track?.title ?: "ECHO Mobile",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                        color = RoonInk,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text(
                    status.track?.artist ?: "就绪",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = RoonMuted,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                LinearProgressIndicator(
                    progress = { progressFraction(status.positionMs, status.durationMs) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(99.dp)),
                    color = Color(0xFFFF9FC8),
                    trackColor = Color.White.copy(alpha = 0.46f),
                )
            }
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(EchoAccentDeep.copy(alpha = 0.16f))
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.58f)), CircleShape)
                    .clickable(
                        enabled = status.state != EchoPlaybackState.Idle || status.track != null,
                        onClick = onPlayPause,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (status.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = "播放或暂停",
                    tint = EchoAccentText.copy(alpha = 0.92f),
                    modifier = Modifier.size(34.dp),
                )
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = {}),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = "播放队列",
                    tint = RoonMuted,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}
@Composable
private fun TransportControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(44.dp)) {
            Icon(
                Icons.Rounded.SkipPrevious,
                contentDescription = "上一首",
                tint = EchoHomeBlue,
                modifier = Modifier.size(28.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(56.dp)
                .shadow(elevation = 10.dp, shape = CircleShape, clip = false)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White, Color(0xFFD9E6EE)),
                    ),
                )
                .clickable(onClick = onPlayPause),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = "播放或暂停",
                tint = EchoHomeBlue,
                modifier = Modifier.size(30.dp),
            )
        }
        IconButton(onClick = onNext, modifier = Modifier.size(44.dp)) {
            Icon(
                Icons.Rounded.SkipNext,
                contentDescription = "下一首",
                tint = EchoHomeBlue,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun PlaybackProgress(positionMs: Long, durationMs: Long, light: Boolean = false) {
    val foreground = if (light) Color.White else EchoAccent
    val secondary = if (light) Color.White.copy(alpha = 0.66f) else Color.White.copy(alpha = 0.6f)
    val trackColor = if (light) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.14f)
    val fraction = progressFraction(positionMs, durationMs).coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(trackColor),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(foreground),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(positionMs), color = secondary, style = MaterialTheme.typography.labelSmall)
            Text(formatDuration(durationMs), color = secondary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ArtworkTile(
    artworkUri: String?,
    modifier: Modifier,
    accent: Color,
    showSignal: Boolean = false,
    cornerRadius: Dp = 14.dp,
    elevation: Dp = 0.dp,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(cornerRadius)
    val bitmap by produceState<Bitmap?>(initialValue = null, artworkUri) {
        value = withContext(Dispatchers.IO) {
            loadArtworkBitmap(context.contentResolver, artworkUri)
        }
    }
    Box(
        modifier = modifier
            .then(
                if (elevation > 0.dp) {
                    Modifier.shadow(elevation = elevation, shape = shape, clip = false)
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        accent,
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.surface,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Rounded.Headphones,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.88f),
                    modifier = Modifier.size(if (showSignal) 38.dp else 32.dp),
                )
                if (showSignal) {
                    Spacer(Modifier.height(18.dp))
                    EchoSignalStrip()
                }
            }
        }
    }
}

@Composable
private fun EchoSignalStrip() {
    val heights = listOf(12.dp, 24.dp, 16.dp, 34.dp, 22.dp, 42.dp, 28.dp, 18.dp, 30.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        heights.forEachIndexed { index, height ->
            Surface(
                modifier = Modifier
                    .width(4.dp)
                    .height(height),
                shape = RoundedCornerShape(8.dp),
                color = if (index % 3 == 0) {
                    EchoAccent.copy(alpha = 0.92f)
                } else {
                    Color.White.copy(alpha = 0.64f)
                },
                content = {},
            )
        }
    }
}

@Composable
private fun DiagnosticLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(16.dp))
        Text(
            value,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun EmptyState(message: String) {
    EchoPanel(Modifier.fillMaxWidth()) {
        Text(
            text = message,
            modifier = Modifier.padding(18.dp),
            color = Color.White.copy(alpha = 0.66f),
        )
    }
}

private fun audioPermissionName(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

private fun loadArtworkBitmap(contentResolver: android.content.ContentResolver, artworkUri: String?): Bitmap? {
    if (artworkUri.isNullOrBlank()) return null
    return runCatching {
        contentResolver.openInputStream(Uri.parse(artworkUri))?.use { stream ->
            BitmapFactory.decodeStream(stream)
        }
    }.getOrNull()
}

private fun trackSubtitle(track: LibraryTrackEntity): String =
    listOf(track.artist, track.album)
        .mapNotNull { value -> value?.takeIf { it.isNotBlank() } }
        .ifEmpty { listOf("本机音频") }
        .joinToString(" / ")

private fun buildAlbumSummaries(tracks: List<LibraryTrackEntity>): List<LibraryAlbumSummary> =
    tracks
        .filter { !it.album.isNullOrBlank() }
        .groupBy { "${it.album.orEmpty()}::${it.albumArtist ?: it.artist}" }
        .values
        .map { albumTracks ->
            val sortedTracks = albumTracks.sortedWith(
                compareBy<LibraryTrackEntity>(
                    { it.discNumber ?: 0 },
                    { it.trackNumber ?: 0 },
                    { it.title.lowercase() },
                ),
            )
            val first = sortedTracks.first()
            LibraryAlbumSummary(
                title = first.album.orEmpty(),
                artist = first.albumArtist?.takeIf { it.isNotBlank() } ?: first.artist,
                artworkUri = sortedTracks.firstNotNullOfOrNull { it.artworkUri },
                tracks = sortedTracks,
            )
        }
        .sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))

private fun buildArtistSummaries(tracks: List<LibraryTrackEntity>): List<LibraryArtistSummary> =
    tracks
        .filter { it.artist.isNotBlank() }
        .groupBy { it.artist }
        .values
        .map { artistTracks ->
            val sortedTracks = artistTracks.sortedWith(
                compareBy<LibraryTrackEntity>(
                    { it.album.orEmpty().lowercase() },
                    { it.discNumber ?: 0 },
                    { it.trackNumber ?: 0 },
                    { it.title.lowercase() },
                ),
            )
            LibraryArtistSummary(
                name = sortedTracks.first().artist,
                artworkUri = sortedTracks.firstNotNullOfOrNull { it.artworkUri },
                albumCount = sortedTracks.mapNotNull { it.album?.takeIf(String::isNotBlank) }.distinct().size,
                tracks = sortedTracks,
            )
        }
        .sortedBy { it.name.lowercase() }

private fun playbackStateLabel(state: EchoPlaybackState): String =
    when (state) {
        EchoPlaybackState.Idle -> "空闲"
        EchoPlaybackState.Loading -> "加载中"
        EchoPlaybackState.Playing -> "播放中"
        EchoPlaybackState.Paused -> "已暂停"
        EchoPlaybackState.Seeking -> "定位中"
        EchoPlaybackState.Buffering -> "缓冲中"
        EchoPlaybackState.Ended -> "已结束"
        EchoPlaybackState.Stopped -> "已停止"
        EchoPlaybackState.Error -> "错误"
    }

private fun repeatModeLabel(mode: EchoRepeatMode): String =
    when (mode) {
        EchoRepeatMode.Off -> "循环关闭"
        EchoRepeatMode.All -> "列表循环"
        EchoRepeatMode.One -> "单曲循环"
    }

private fun remoteConnectionLabel(state: EchoRemoteConnectionState): String =
    when (state) {
        EchoRemoteConnectionState.Disconnected -> "未连接"
        EchoRemoteConnectionState.Pairing -> "配对中"
        EchoRemoteConnectionState.Connecting -> "连接中"
        EchoRemoteConnectionState.Connected -> "已连接"
        EchoRemoteConnectionState.Reconnecting -> "重连中"
        EchoRemoteConnectionState.Error -> "错误"
    }

private fun commandLabel(command: String?): String =
    when (command?.lowercase()) {
        null, "idle" -> "空闲"
        "play", "playpause" -> "播放"
        "pause" -> "暂停"
        "next" -> "下一首"
        "previous" -> "上一首"
        "seek" -> "跳转"
        "stop" -> "停止"
        else -> command
    }

private fun progressFraction(positionMs: Long, durationMs: Long): Float =
    if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
