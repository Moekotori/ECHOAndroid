package app.echo.android

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import app.echo.android.connect.EchoRemoteClient
import app.echo.android.design.EchoContentMaxWidth
import app.echo.android.design.EchoGlassBackground
import app.echo.android.design.EchoGlassBorder
import app.echo.android.design.EchoMobileTheme
import app.echo.android.feature.connect.ConnectScreen
import app.echo.android.feature.home.HomeScreen
import app.echo.android.feature.library.LibraryScreen
import app.echo.android.feature.player.MiniPlayer
import app.echo.android.feature.settings.DiagnosticsScreen
import app.echo.android.model.connect.EchoRemoteCommand
import app.echo.android.model.connect.EchoRemoteEndpoint
import app.echo.android.model.connect.EchoRemotePlaybackState
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.ArtistSummary
import app.echo.android.model.library.LibraryStats

private val DockMotionEasing = CubicBezierEasing(0.16f, 1f, 0.30f, 1f)

@Composable
fun EchoAppRoot(viewModel: EchoAndroidViewModel) {
    val context = LocalContext.current
    val permission = remember { audioPermissionName() }
    var hasAudioPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasAudioPermission = granted
        if (granted) viewModel.refreshLibrary()
    }
    val folderScanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::refreshLibraryFolder)
    }

    val remoteClient = remember { EchoRemoteClient() }
    val remoteStatus by remoteClient.status.collectAsStateWithLifecycle()
    val playbackStatus by viewModel.playbackStatus.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val libraryStats by viewModel.libraryStats.collectAsStateWithLifecycle(LibraryStats())
    val homeRecommendedTracks by viewModel.recommendedTracks.collectAsStateWithLifecycle(emptyList())
    val tracks = viewModel.tracks.collectAsLazyPagingItems()
    val albums = viewModel.albums.collectAsLazyPagingItems()
    val artists = viewModel.artists.collectAsLazyPagingItems()
    val homeRecentAlbums = albums.itemSnapshotList.items.take(12)
    var selectedAlbum by remember { mutableStateOf<AlbumSummary?>(null) }
    var selectedArtist by remember { mutableStateOf<ArtistSummary?>(null) }
    val selectedAlbumKey = selectedAlbum?.albumKey
    val selectedArtistKey = selectedArtist?.artistKey
    val albumDetailTracks = selectedAlbumKey?.let { albumKey ->
        remember(albumKey) { viewModel.albumTrackPaging(albumKey) }.collectAsLazyPagingItems()
    }
    val artistDetailTracks = selectedArtistKey?.let { artistKey ->
        remember(artistKey) { viewModel.artistTrackPaging(artistKey) }.collectAsLazyPagingItems()
    }
    var selectedTab by remember { mutableIntStateOf(EchoTab.Now.ordinal) }
    var bottomDockExpanded by remember { mutableStateOf(true) }
    val hideDockOnScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y < -8f) {
                    bottomDockExpanded = false
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(hasAudioPermission) {
        if (hasAudioPermission && scanState.lastScanCount == null && !scanState.isScanning) {
            viewModel.refreshLibrary()
        }
    }

    EchoMobileTheme(darkTheme = isSystemInDarkTheme()) {
        Box(Modifier.fillMaxSize()) {
            EchoGlassBackground(Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(hideDockOnScroll),
            ) {
                key(selectedTab) {
                    when (EchoTab.entries[selectedTab]) {
                        EchoTab.Library -> LibraryScreen(
                                hasPermission = hasAudioPermission,
                                scanState = scanState,
                                tracks = tracks,
                                albums = albums,
                                artists = artists,
                                selectedAlbum = selectedAlbum,
                                selectedArtist = selectedArtist,
                                albumDetailTracks = albumDetailTracks,
                                artistDetailTracks = artistDetailTracks,
                                onRequestPermission = { permissionLauncher.launch(permission) },
                                onScanFolder = { folderScanLauncher.launch(null) },
                                onScanAll = viewModel::refreshLibrary,
                                onCancelScan = viewModel::cancelScan,
                                onPlayTrack = { track -> viewModel.playTrackFromLibrary(track.id) },
                                onPlayAlbum = { album -> viewModel.playAlbum(album.albumKey) },
                                onPlayArtist = { artist -> viewModel.playArtist(artist.artistKey) },
                                onOpenAlbum = { album ->
                                    selectedArtist = null
                                    selectedAlbum = album
                                },
                                onOpenArtist = { artist ->
                                    selectedAlbum = null
                                    selectedArtist = artist
                                },
                                onCloseDetail = {
                                    selectedAlbum = null
                                    selectedArtist = null
                                },
                            )

                        EchoTab.Now -> HomeScreen(
                                status = playbackStatus,
                                trackCount = libraryStats.trackCount,
                                albumCount = libraryStats.albumCount,
                                artistCount = libraryStats.artistCount,
                                recentAlbums = homeRecentAlbums,
                                recommendedTracks = homeRecommendedTracks,
                                onPlayPause = viewModel::playPause,
                                onNext = viewModel::skipNext,
                                onPrevious = viewModel::skipPrevious,
                                onCycleRepeatMode = viewModel::cycleRepeatMode,
                                onToggleShuffle = viewModel::toggleShuffle,
                                onRefreshRecommendations = viewModel::refreshLibrary,
                                onPlayRecommendation = viewModel::playQueue,
                                onOpenAlbum = { album ->
                                    selectedArtist = null
                                    selectedAlbum = album
                                    selectedTab = EchoTab.Library.ordinal
                                },
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
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AnimatedContent(
                        targetState = bottomDockExpanded,
                        transitionSpec = {
                            val enter = fadeIn(tween(durationMillis = 220, delayMillis = 70, easing = DockMotionEasing)) +
                                slideInVertically(tween(durationMillis = 460, easing = DockMotionEasing)) { height -> height / 3 } +
                                scaleIn(
                                    initialScale = 0.96f,
                                    animationSpec = tween(durationMillis = 460, easing = DockMotionEasing),
                                )
                            val exit = fadeOut(tween(durationMillis = 150, easing = DockMotionEasing)) +
                                slideOutVertically(tween(durationMillis = 260, easing = DockMotionEasing)) { height -> height / 5 } +
                                scaleOut(
                                    targetScale = 0.985f,
                                    animationSpec = tween(durationMillis = 260, easing = DockMotionEasing),
                                )
                            enter togetherWith exit
                        },
                        label = "bottom-controls-transition",
                    ) {
                        if (it) {
                            ExpandedBottomControls(
                                status = playbackStatus,
                                selectedTab = selectedTab,
                                onPlayPause = viewModel::playPause,
                                onSelectTab = { selectedTab = it },
                                modifier = Modifier
                                    .widthIn(max = EchoContentMaxWidth)
                                    .fillMaxWidth(),
                            )
                        } else {
                            CompactBottomControls(
                                status = playbackStatus,
                                onPlayPause = viewModel::playPause,
                                onShowDock = { bottomDockExpanded = true },
                                onOpenQueue = {},
                                modifier = Modifier
                                    .widthIn(max = EchoContentMaxWidth)
                                    .fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandedBottomControls(
    status: app.echo.android.model.playback.EchoPlaybackStatus,
    selectedTab: Int,
    onPlayPause: () -> Unit,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        MiniPlayer(
            status = status,
            onPlayPause = onPlayPause,
            modifier = Modifier.fillMaxWidth(),
        )
        BottomDock(
            selectedTab = selectedTab,
            onLightSurface = true,
            onSelectTab = onSelectTab,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CompactBottomControls(
    status: app.echo.android.model.playback.EchoPlaybackStatus,
    onPlayPause: () -> Unit,
    onShowDock: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundDockButton(
            icon = Icons.Rounded.KeyboardArrowUp,
            description = "显示底栏",
            onClick = onShowDock,
        )
        MiniPlayer(
            status = status,
            onPlayPause = onPlayPause,
            modifier = Modifier.weight(1f),
        )
        RoundDockButton(
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
            description = "播放队列",
            onClick = onOpenQueue,
        )
    }
}

@Composable
private fun RoundDockButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(62.dp)
            .shadow(
                elevation = 9.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.05f),
                spotColor = Color.Black.copy(alpha = 0.09f),
            )
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.92f))
            .border(BorderStroke(1.dp, EchoGlassBorder), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = Color(0xFFFF2D55),
            modifier = Modifier.size(31.dp),
        )
    }
}
