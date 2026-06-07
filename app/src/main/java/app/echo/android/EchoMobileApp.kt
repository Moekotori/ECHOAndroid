package app.echo.android

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import kotlinx.coroutines.withContext

private enum class EchoTab(
    val label: String,
    val icon: ImageVector,
) {
    Library("曲库", Icons.Rounded.LibraryMusic),
    Now("播放", Icons.Rounded.MusicNote),
    Connect("连接", Icons.Rounded.Devices),
    Diagnostics("状态", Icons.Rounded.GraphicEq),
}

private val EchoContentMaxWidth = 560.dp

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
    var selectedTab by remember { mutableIntStateOf(EchoTab.Now.ordinal) }

    LaunchedEffect(hasAudioPermission) {
        if (hasAudioPermission && scanState.lastScanCount == null && !scanState.isScanning) {
            viewModel.refreshLibrary()
        }
    }

    val darkTheme = isSystemInDarkTheme()

    EchoMobileTheme(darkTheme = darkTheme) {
        val appBackground = Brush.linearGradient(
            listOf(
                Color(0xFF516A9E),
                Color(0xFF8C4C99),
                Color(0xFF2C3B63),
                Color(0xFF141725),
            ),
        )

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    val shouldShowMiniPlayer = selectedTab != EchoTab.Now.ordinal &&
                        (playbackStatus.track != null || playbackStatus.state != EchoPlaybackState.Idle)
                    if (shouldShowMiniPlayer) {
                        MiniPlayer(
                            status = playbackStatus,
                            onPlayPause = viewModel::playPause,
                            modifier = Modifier
                                .widthIn(max = EchoContentMaxWidth)
                                .fillMaxWidth(),
                        )
                    }
                    BottomDock(
                        selectedTab = selectedTab,
                        onSelectTab = { selectedTab = it },
                        modifier = Modifier
                            .widthIn(max = EchoContentMaxWidth)
                            .fillMaxWidth(),
                    )
                }
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(appBackground)
                    .padding(padding)
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

@Composable
private fun LibraryScreen(
    hasPermission: Boolean,
    scanState: LibraryScanUiState,
    tracks: LazyPagingItems<LibraryTrackEntity>,
    onRequestPermission: () -> Unit,
    onScan: () -> Unit,
    onPlayQueue: (List<LibraryTrackEntity>, Int) -> Unit,
) {
    PageChrome(
        title = "曲库",
        subtitle = "本机音乐",
        badge = "本地",
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
            tracks.itemCount == 0 -> LibraryBootstrapState()
            else -> {
                TrackList(
                    tracks = tracks,
                    onPlayQueue = onPlayQueue,
                    modifier = Modifier.weight(1f),
                )
            }
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
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
    ) {
        IconButton(
            onClick = if (hasPermission) onScan else onRequestPermission,
            enabled = !scanState.isScanning,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                Icons.Rounded.Refresh,
                contentDescription = description,
                tint = if (scanState.error != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
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
            Column(Modifier.weight(1f)) {
                Text("暂无本机歌曲", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "点右上角扫描本机音乐。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ArtworkTile(track.artworkUri, Modifier.size(48.dp), accent = EchoColors.Brass)
            Column(Modifier.weight(1f)) {
                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(
                    trackSubtitle(track),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(formatDuration(track.durationMs), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NowPlayingScreen(
    status: EchoPlaybackStatus,
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
        val horizontalPadding = if (maxWidth < 390.dp) 14.dp else 18.dp
        val topGap = if (compactViewport) 10.dp else 18.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = horizontalPadding, vertical = topGap),
            verticalArrangement = Arrangement.spacedBy(if (compactViewport) 12.dp else 16.dp),
        ) {
            HomeTopChrome(onOpenLibrary = onOpenLibrary)
            HomeGreeting(status = status)
            DailyRecommendationCard(
                status = status,
                onClick = if (status.track != null) onPlayPause else onOpenLibrary,
            )
            NowPlayingHero(
                status = status,
                compact = compactViewport,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
            )
            HomeModeRibbon(
                repeatMode = status.repeatMode,
                shuffleEnabled = status.shuffleEnabled,
                onCycleRepeatMode = onCycleRepeatMode,
                onToggleShuffle = onToggleShuffle,
                onOpenLibrary = onOpenLibrary,
                onOpenConnect = onOpenConnect,
            )
            Spacer(Modifier.height(12.dp))
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
            style = MaterialTheme.typography.headlineMedium,
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
            .heightIn(min = 154.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF62D8FF),
                        Color(0xFF7A7CFF),
                        Color(0xFFFFA86B),
                        Color(0xFF4D357B),
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
            Icon(icon, contentDescription = label, tint = if (selected) Color(0xFFFFB5CB) else Color.White.copy(alpha = 0.82f), modifier = Modifier.size(21.dp))
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
            Color.White.copy(alpha = 0.24f),
            Color.White.copy(alpha = 0.10f),
            Color(0xFF231A42).copy(alpha = 0.18f),
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
            .clip(RoundedCornerShape(8.dp))
            .background(heroBrush)
            .background(Color.White.copy(alpha = 0.08f))
            .padding(16.dp),
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
                    color = Color.White.copy(alpha = 0.74f),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    playbackStateLabel(status.state),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.78f),
                )
            }
            ArtworkTile(
                artworkUri = status.track?.artworkUri?.toString(),
                modifier = Modifier
                    .fillMaxWidth(0.44f)
                    .aspectRatio(1f),
                accent = EchoColors.Sky,
                showSignal = true,
            )
            Text(
                status.track?.title ?: "暂无播放",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                status.track?.artist ?: "从曲库选择一首歌",
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            PlaybackProgress(status.positionMs, status.durationMs, light = true)
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
            .clip(RoundedCornerShape(8.dp))
            .background(heroBrush)
            .padding(12.dp),
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
                accent = EchoColors.Sky,
                showSignal = true,
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
                            color = Color.White.copy(alpha = 0.74f),
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            playbackStateLabel(status.state),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.78f),
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
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    status.track?.artist ?: "从曲库选择一首歌",
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                PlaybackProgress(status.positionMs, status.durationMs, light = true)
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
    PageChrome(title = "连接", subtitle = "配对并控制 PC ECHO", badge = "PC联动", scrollable = true) {
        EchoPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)) {
                        Icon(
                            Icons.Rounded.Devices,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(pcTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(remoteConnectionLabel(remoteState), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    EchoTextButton(
                        text = if (connected) "已配对" else "配对",
                        onClick = onPairDemo,
                        enabled = !connected,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    EchoMetricTile("配对码", if (connected) "已信任" else "ECHO-267", Modifier.weight(1f), detail = "验证")
                    EchoMetricTile("接力", if (connected) "可用" else "待机", Modifier.weight(1f), detail = "队列")
                    EchoMetricTile("输出", if (connected) "PC ECHO" else "本机", Modifier.weight(1f), detail = "目标")
                }
                PcLinkStatusStrip(connected = connected)
                RemoteNowPlaying(
                    title = trackTitle,
                    artist = trackArtist,
                    isPlaying = isPlaying,
                    controlsEnabled = connected,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        PcHandoffPanel(connected = connected)
        Spacer(Modifier.height(12.dp))
        EchoPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                EchoSectionTitle("配对", "发现、验证、接力")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    PairingPill("1", "发现", "局域网", Modifier.weight(1f), active = connected)
                    PairingPill("2", "信任", "配对码", Modifier.weight(1f), active = connected)
                    PairingPill("3", "接力", "队列", Modifier.weight(1f), active = connected)
                }
                TextButton(
                    onClick = onDisconnect,
                    enabled = remoteState != EchoRemoteConnectionState.Disconnected,
                ) {
                    Text("断开 PC")
                }
            }
        }
    }
}

@Composable
private fun PcLinkStatusStrip(connected: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)),
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
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ArtworkTile(null, Modifier.size(56.dp), accent = EchoColors.Coral)
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
            SignalOverviewPanel(
                status = status,
                output = diagnostics.outputRoute,
                codec = codec,
                buffer = bufferSeconds,
                lastCommand = lastCommand,
            )
            SignalPathPanel(
                output = diagnostics.outputRoute,
                codec = codec,
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
private fun SignalOverviewPanel(
    status: EchoPlaybackStatus,
    output: String,
    codec: String,
    buffer: String,
    lastCommand: String,
) {
    EchoPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("链路总览", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        if (status.isPlaying) "正在输出稳定音频流" else "等待播放，链路保持就绪",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                ) {
                    Text(
                        playbackStateLabel(status.state),
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            SignalBars(active = status.isPlaying)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                EchoMetricTile("输出", output, Modifier.weight(1.25f), detail = "通道")
                EchoMetricTile("解码", codec, Modifier.weight(1f), detail = "引擎")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                EchoMetricTile("缓冲", buffer, Modifier.weight(1f), detail = "预读")
                EchoMetricTile("命令", lastCommand, Modifier.weight(1f), detail = "最近")
            }
        }
    }
}

@Composable
private fun SignalPathPanel(
    output: String,
    codec: String,
    offloadActive: Boolean,
) {
    EchoPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            EchoSectionTitle("链路路径", "从曲库到输出设备")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SignalStage("曲库", "本机", selected = true, Modifier.weight(1f))
                SignalStage("解码", codec, selected = true, Modifier.weight(1f))
                SignalStage("输出", output, selected = true, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                EchoSegmentChip("本机优先", selected = true, Modifier.weight(1f))
                EchoSegmentChip("DSP ${if (offloadActive) "开启" else "关闭"}", selected = offloadActive, Modifier.weight(1f))
                EchoSegmentChip("PC 待接力", selected = false, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SignalStage(
    title: String,
    detail: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Text(detail, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CurrentStreamPanel(
    status: EchoPlaybackStatus,
    lastCommand: String,
    requestToken: Long,
) {
    EchoPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
    EchoPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EchoSectionTitle("健康", diagnostics.lastError?.message ?: "未记录掉音")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                EchoSegmentChip("稳定", selected = diagnostics.lastError == null, Modifier.weight(1f))
                EchoSegmentChip("本机", selected = true, Modifier.weight(1f))
                EchoSegmentChip("接力", selected = false, Modifier.weight(1f))
            }
            EchoPlaceholderLine(if (diagnostics.lastError == null) "解码回退记录为空" else "查看解码回退")
            EchoPlaceholderLine("PC 链路质量待测")
        }
    }
}

@Composable
private fun SignalBars(active: Boolean) {
    val heights = listOf(18.dp, 30.dp, 24.dp, 42.dp, 28.dp, 48.dp, 34.dp, 22.dp, 38.dp, 26.dp, 44.dp, 20.dp)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            heights.forEachIndexed { index, height ->
                Surface(
                    modifier = Modifier
                        .width(5.dp)
                        .height(if (active || index % 2 == 0) height else height * 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    color = if (active && index % 3 == 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (active) 0.46f else 0.26f)
                    },
                    content = {},
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
private fun AmbientPlanet(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(86.dp), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.size(78.dp),
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.34f),
            content = {},
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .height(10.dp),
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFFFD18C).copy(alpha = 0.48f),
            content = {},
        )
    }
}

@Composable
private fun PageChrome(
    title: String,
    subtitle: String,
    badge: String = "移动端",
    scrollable: Boolean = false,
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
                .statusBarsPadding(),
        ) {
            val horizontalPadding = if (maxWidth >= 720.dp) 28.dp else 16.dp
            val topPadding = if (compactChrome) 8.dp else 14.dp
            val headerGap = if (compactChrome) 6.dp else 8.dp
            val contentGap = if (compactChrome) 8.dp else 12.dp
            val titleStyle = if (compactChrome) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge
            Column(
                modifier = Modifier
                    .widthIn(max = EchoContentMaxWidth)
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .then(contentScroll)
                    .padding(start = horizontalPadding, end = horizontalPadding, top = topPadding, bottom = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("ECHO 移动端", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
                        ) {
                            Text(
                                badge,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        actions()
                    }
                }
                Spacer(Modifier.height(headerGap))
                Text(title, style = titleStyle, fontWeight = FontWeight.Bold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.White.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.30f)),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 56.dp)
                .padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EchoTab.entries.forEach { tab ->
                DockItem(
                    tab = tab,
                    selected = selectedTab == tab.ordinal,
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = if (selected) Color.White.copy(alpha = 0.18f) else Color.Transparent
    val contentColor = if (selected) Color(0xFFFFB5CB) else Color.White.copy(alpha = 0.74f)
    Column(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(tab.icon, contentDescription = tab.label, tint = contentColor, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun MiniPlayer(
    status: EchoPlaybackStatus,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White.copy(alpha = 0.18f),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.34f)),
    ) {
        Column(Modifier.fillMaxWidth()) {
            LinearProgressIndicator(
                progress = { progressFraction(status.positionMs, status.durationMs) },
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFFFB5CB),
                trackColor = Color.White.copy(alpha = 0.16f),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ArtworkTile(status.track?.artworkUri?.toString(), Modifier.size(38.dp), accent = EchoColors.Brass)
                Column(Modifier.weight(1f)) {
                    Text(status.track?.title ?: "ECHO 移动端", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text(status.track?.artist ?: "就绪", maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White.copy(alpha = 0.68f))
                }
                IconButton(onClick = onPlayPause, enabled = status.state != EchoPlaybackState.Idle) {
                    Icon(if (status.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = "播放或暂停", tint = Color.White)
                }
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
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Rounded.SkipPrevious, contentDescription = "上一首", tint = Color.White.copy(alpha = 0.84f))
        }
        Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.92f)) {
            IconButton(onClick = onPlayPause) {
                Icon(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = "播放或暂停",
                    tint = Color(0xFF28345E),
                )
            }
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Rounded.SkipNext, contentDescription = "下一首", tint = Color.White.copy(alpha = 0.84f))
        }
    }
}

@Composable
private fun PlaybackProgress(positionMs: Long, durationMs: Long, light: Boolean = false) {
    val foreground = if (light) Color.White else MaterialTheme.colorScheme.primary
    val secondary = if (light) Color.White.copy(alpha = 0.70f) else MaterialTheme.colorScheme.onSurfaceVariant
    val track = if (light) Color.White.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    Column {
        LinearProgressIndicator(
            progress = { progressFraction(positionMs, durationMs) },
            modifier = Modifier.fillMaxWidth(),
            color = foreground,
            trackColor = track,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(positionMs), color = secondary)
            Text(formatDuration(durationMs), color = secondary)
        }
    }
}

@Composable
private fun ArtworkTile(
    artworkUri: String?,
    modifier: Modifier,
    accent: Color,
    showSignal: Boolean = false,
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, artworkUri) {
        value = withContext(Dispatchers.IO) {
            loadArtworkBitmap(context.contentResolver, artworkUri)
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
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
                    EchoColors.Brass.copy(alpha = 0.92f)
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
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmptyState(message: String) {
    EchoPanel(Modifier.fillMaxWidth()) {
        Text(
            text = message,
            modifier = Modifier.padding(18.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
