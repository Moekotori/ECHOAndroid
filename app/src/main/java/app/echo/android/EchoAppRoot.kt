package app.echo.android

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import app.echo.android.connect.EchoRemoteClient
import app.echo.android.design.EchoContentMaxWidth
import app.echo.android.design.EchoGlassPanel
import app.echo.android.design.EchoMobileTheme
import app.echo.android.feature.connect.ConnectScreen
import app.echo.android.feature.home.HomeScreen
import app.echo.android.feature.library.LibraryScreen
import app.echo.android.feature.player.MiniPlayer
import app.echo.android.feature.player.NowPlayingScreen
import app.echo.android.feature.player.PlaybackQueueSheet
import app.echo.android.feature.settings.DiagnosticsScreen
import app.echo.android.feature.settings.SettingsScreen
import app.echo.android.data.EchoAppSettings
import app.echo.android.data.EchoBackgroundMode
import app.echo.android.data.EchoFontFamilyMode
import app.echo.android.data.EchoThemeMode
import app.echo.android.model.connect.EchoRemoteCommand
import app.echo.android.model.connect.EchoRemoteConnectionState
import app.echo.android.model.connect.EchoRemoteEndpoint
import app.echo.android.model.connect.EchoRemotePlaybackState
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.ArtistSummary
import app.echo.android.model.library.FolderSummary
import app.echo.android.model.library.LibraryStats
import app.echo.android.model.playback.PlaybackPositionState
import app.echo.android.design.echoFontFamilyForMode
import java.time.LocalTime
import kotlin.math.absoluteValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val DockMotionEasing = CubicBezierEasing(0.16f, 1f, 0.30f, 1f)
private val RouteMotionEasing = CubicBezierEasing(0.18f, 0.86f, 0.20f, 1f)
private const val RouteMotionBaseDurationMs = 240
private const val RouteMotionDistanceDurationMs = 28
private const val RouteMotionMaxDurationMs = 320
private val LyricsDocumentMimeTypes = arrayOf("text/*", "application/xml", "application/octet-stream", "*/*")
private val FontDocumentMimeTypes = arrayOf("font/*", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream", "*/*")

private enum class FontImportTarget {
    Ui,
    Lyrics,
}

private enum class EchoPagerPage {
    Settings,
    Now,
    Library,
    Connect,
    Diagnostics,
}

private val EchoTab.pagerPage: EchoPagerPage
    get() = when (this) {
        EchoTab.Now -> EchoPagerPage.Now
        EchoTab.Library -> EchoPagerPage.Library
        EchoTab.Connect -> EchoPagerPage.Connect
        EchoTab.Diagnostics -> EchoPagerPage.Diagnostics
    }

private val EchoPagerPage.dockTab: EchoTab?
    get() = when (this) {
        EchoPagerPage.Now -> EchoTab.Now
        EchoPagerPage.Library -> EchoTab.Library
        EchoPagerPage.Connect -> EchoTab.Connect
        EchoPagerPage.Diagnostics -> EchoTab.Diagnostics
        EchoPagerPage.Settings -> null
    }

private fun routeMotionSpec(fromPage: Int, toPage: Int): AnimationSpec<Float> {
    val distance = (toPage - fromPage).absoluteValue.coerceAtLeast(1)
    val duration = (RouteMotionBaseDurationMs + (distance - 1) * RouteMotionDistanceDurationMs)
        .coerceAtMost(RouteMotionMaxDurationMs)
    return tween(durationMillis = duration, easing = RouteMotionEasing)
}

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
    fun persistReadPermission(uri: android.net.Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }
    val backgroundImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { selectedUri ->
            persistReadPermission(selectedUri)
            viewModel.setCustomBackground(EchoBackgroundMode.Image, selectedUri)
        }
    }
    val backgroundVideoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { selectedUri ->
            persistReadPermission(selectedUri)
            viewModel.setCustomBackground(EchoBackgroundMode.Video, selectedUri)
        }
    }
    var fontImportTarget by remember { mutableStateOf<FontImportTarget?>(null) }
    val fontImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { selectedUri ->
            persistReadPermission(selectedUri)
            viewModel.setImportedFontUri(selectedUri)
            when (fontImportTarget) {
                FontImportTarget.Ui -> viewModel.setUiFontFamily(EchoFontFamilyMode.Imported)
                FontImportTarget.Lyrics -> viewModel.setLyricsFontFamily(EchoFontFamilyMode.Imported)
                null -> Unit
            }
        }
        fontImportTarget = null
    }
    val lyricsImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { lyricsUri ->
            persistReadPermission(lyricsUri)
            viewModel.importLyrics(lyricsUri)
        }
    }

    val remoteClient = remember { EchoRemoteClient() }
    val remoteStatus by remoteClient.status.collectAsStateWithLifecycle()
    val playbackStatus by viewModel.playbackStatus.collectAsStateWithLifecycle()
    val playbackQueue by viewModel.playbackQueue.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle(EchoAppSettings())
    val lastFmState by viewModel.lastFmState.collectAsStateWithLifecycle()
    val usbExclusiveTestResult by viewModel.usbExclusiveTestResult.collectAsStateWithLifecycle()
    val discordPresenceSnapshot by viewModel.discordPresenceSnapshot.collectAsStateWithLifecycle(null)
    val lastFmApiKey = appSettings.lastFmApiKey?.takeIf { it.isNotBlank() }
        ?: LastFmApiConfig.apiKey.takeIf { it.isNotBlank() }
    val lastFmSharedSecret = appSettings.lastFmSharedSecret?.takeIf { it.isNotBlank() }
        ?: LastFmApiConfig.sharedSecret.takeIf { it.isNotBlank() }
    val lyricsState by viewModel.lyricsState.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val remoteScanState by viewModel.remoteScanState.collectAsStateWithLifecycle()
    val libraryStats by viewModel.libraryStats.collectAsStateWithLifecycle(LibraryStats())
    val recentPlaybackAlbums by viewModel.recentPlaybackAlbums.collectAsStateWithLifecycle()
    val recentPlaybackArtists by viewModel.recentPlaybackArtists.collectAsStateWithLifecycle()
    val recentPlaybackHeatmap by viewModel.recentPlaybackHeatmap.collectAsStateWithLifecycle()
    val recentlyAddedAlbums by viewModel.recentlyAddedAlbums.collectAsStateWithLifecycle(emptyList())
    val tracks = viewModel.tracks.collectAsLazyPagingItems()
    val albums = viewModel.albums.collectAsLazyPagingItems()
    val remoteAlbums = viewModel.remoteAlbums.collectAsLazyPagingItems()
    val artists = viewModel.artists.collectAsLazyPagingItems()
    val folders = viewModel.folders.collectAsLazyPagingItems()
    val homeAlbumSnapshot = albums.itemSnapshotList.items
    var homeRecommendationSeed by remember { mutableIntStateOf(0) }
    val homeRecommendedAlbums = remember(homeRecommendationSeed, homeAlbumSnapshot.map(AlbumSummary::albumKey)) {
        homeAlbumSnapshot.shuffled().take(8)
    }
    var selectedAlbum by remember { mutableStateOf<AlbumSummary?>(null) }
    var selectedArtist by remember { mutableStateOf<ArtistSummary?>(null) }
    var selectedFolder by remember { mutableStateOf<FolderSummary?>(null) }
    var detailReturnPage by remember { mutableStateOf<EchoPagerPage?>(null) }
    val selectedAlbumKey = selectedAlbum?.albumKey
    val selectedArtistKey = selectedArtist?.artistKey
    val selectedFolderKey = selectedFolder?.folderKey
    val albumDetailTracks = selectedAlbumKey?.let { albumKey ->
        remember(albumKey) { viewModel.albumTrackPaging(albumKey) }.collectAsLazyPagingItems()
    }
    val artistDetailTracks = selectedArtistKey?.let { artistKey ->
        remember(artistKey) { viewModel.artistTrackPaging(artistKey) }.collectAsLazyPagingItems()
    }
    val folderDetailTracks = selectedFolderKey?.let { folderKey ->
        remember(folderKey) { viewModel.folderTrackPaging(folderKey) }.collectAsLazyPagingItems()
    }
    var selectedTab by remember { mutableIntStateOf(EchoTab.Now.ordinal) }
    var bottomDockExpanded by remember { mutableStateOf(true) }
    var nowPlayingExpanded by remember { mutableStateOf(false) }
    var queueSheetVisible by remember { mutableStateOf(false) }
    val libraryDetailOpen = selectedAlbum != null || selectedArtist != null || selectedFolder != null
    val systemDarkTheme = isSystemInDarkTheme()
    var currentMinuteOfDay by remember { mutableIntStateOf(currentMinuteNow()) }
    LaunchedEffect(appSettings.scheduledDarkModeEnabled) {
        if (appSettings.scheduledDarkModeEnabled) {
            while (true) {
                currentMinuteOfDay = currentMinuteNow()
                delay(60_000L)
            }
        } else {
            currentMinuteOfDay = currentMinuteNow()
        }
    }
    val darkTheme = remember(
        systemDarkTheme,
        currentMinuteOfDay,
        appSettings.themeMode,
        appSettings.scheduledDarkModeEnabled,
        appSettings.scheduledDarkStartMinute,
        appSettings.scheduledDarkEndMinute,
    ) {
        resolveDarkTheme(
            systemDarkTheme = systemDarkTheme,
            themeMode = appSettings.themeMode,
            scheduledDarkModeEnabled = appSettings.scheduledDarkModeEnabled,
            scheduledStartMinute = appSettings.scheduledDarkStartMinute,
            scheduledEndMinute = appSettings.scheduledDarkEndMinute,
            currentMinute = currentMinuteOfDay,
        )
    }
    val importedFontFamily = rememberImportedFontFamily(appSettings.importedFontUri)
    val uiFontFamily = echoFontFamilyForMode(appSettings.uiFontFamily, importedFontFamily)
    val lyricsFontFamily = echoFontFamilyForMode(appSettings.lyricsFontFamily, importedFontFamily)
    val activity = context as? ComponentActivity

    SideEffect {
        activity?.enableEdgeToEdge(
            statusBarStyle = if (darkTheme) {
                SystemBarStyle.dark(AndroidColor.TRANSPARENT)
            } else {
                SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
            },
            navigationBarStyle = if (darkTheme) {
                SystemBarStyle.dark(AndroidColor.TRANSPARENT)
            } else {
                SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
            },
        )
    }

    // 四个主页面横向滑动切换，与底部 dock 双向联动
    val tabPagerState = rememberPagerState(
        initialPage = EchoPagerPage.Now.ordinal,
        pageCount = { EchoPagerPage.entries.size },
    )
    val appScope = rememberCoroutineScope()
    val routeNavigationJob = remember { arrayOfNulls<Job>(1) }
    fun navigateToPage(page: EchoPagerPage) {
        val targetPage = page.ordinal
        page.dockTab?.let { selectedTab = it.ordinal }
        routeNavigationJob[0]?.cancel()
        routeNavigationJob[0] = appScope.launch {
            if (tabPagerState.currentPage != targetPage) {
                tabPagerState.animateScrollToPage(
                    page = targetPage,
                    animationSpec = routeMotionSpec(tabPagerState.currentPage, targetPage),
                )
            }
        }
    }
    fun selectDockTab(tab: EchoTab) = navigateToPage(tab.pagerPage)
    fun clearLibraryDetail() {
        selectedAlbum = null
        selectedArtist = null
        selectedFolder = null
    }
    fun closeLibraryDetail() {
        val returnPage = detailReturnPage ?: EchoPagerPage.Library
        detailReturnPage = null
        if (returnPage == EchoPagerPage.Library) {
            clearLibraryDetail()
            return
        }
        returnPage.dockTab?.let { selectedTab = it.ordinal }
        routeNavigationJob[0]?.cancel()
        appScope.launch {
            try {
                val targetPage = returnPage.ordinal
                if (tabPagerState.currentPage != targetPage) {
                    tabPagerState.animateScrollToPage(
                        page = targetPage,
                        animationSpec = routeMotionSpec(tabPagerState.currentPage, targetPage),
                    )
                }
            } finally {
                clearLibraryDetail()
            }
        }
    }
    LaunchedEffect(tabPagerState.settledPage) {
        EchoPagerPage.entries[tabPagerState.settledPage].dockTab?.let { settledTab ->
            if (settledTab.ordinal != selectedTab) selectedTab = settledTab.ordinal
        }
    }

    LaunchedEffect(hasAudioPermission) {
        if (hasAudioPermission && scanState.lastScanCount == null && !scanState.isScanning) {
            viewModel.refreshLibrary()
        }
    }

    LaunchedEffect(discordPresenceSnapshot) {
        remoteClient.publishMobileDiscordPresence(discordPresenceSnapshot)
    }

    BackHandler(enabled = nowPlayingExpanded) { nowPlayingExpanded = false }
    BackHandler(enabled = queueSheetVisible) { queueSheetVisible = false }
    BackHandler(enabled = !nowPlayingExpanded && libraryDetailOpen) {
        closeLibraryDetail()
    }
    BackHandler(enabled = !nowPlayingExpanded && tabPagerState.currentPage == EchoPagerPage.Settings.ordinal) {
        selectDockTab(EchoTab.Now)
    }

    EchoMobileTheme(
        darkTheme = darkTheme,
        fontFamily = uiFontFamily,
        fontScale = appSettings.uiFontScale,
        densityScale = appSettings.uiDensityScale,
    ) {
        Box(Modifier.fillMaxSize()) {
            EchoCustomBackground(settings = appSettings, modifier = Modifier.fillMaxSize())
            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                HorizontalPager(
                    state = tabPagerState,
                    userScrollEnabled = !libraryDetailOpen,
                    beyondViewportPageCount = 0,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (EchoPagerPage.entries[page]) {
                            EchoPagerPage.Library -> LibraryScreen(
                                hasPermission = hasAudioPermission,
                                scanState = scanState,
                                tracks = tracks,
                                albums = albums,
                                remoteAlbums = remoteAlbums,
                                artists = artists,
                                folders = folders,
                                selectedAlbum = selectedAlbum,
                                selectedArtist = selectedArtist,
                                selectedFolder = selectedFolder,
                                albumDetailTracks = albumDetailTracks,
                                artistDetailTracks = artistDetailTracks,
                                folderDetailTracks = folderDetailTracks,
                                onRequestPermission = { permissionLauncher.launch(permission) },
                                onScanFolder = { folderScanLauncher.launch(null) },
                                onScanAll = viewModel::refreshLibrary,
                                onCancelScan = viewModel::cancelScan,
                                onPlayTrack = { track -> viewModel.playTrackFromLibrary(track.id) },
                                onPlayAlbum = { album -> viewModel.playAlbum(album.albumKey) },
                                onShuffleAlbum = { album -> viewModel.shuffleAlbum(album.albumKey) },
                                onPlayArtist = { artist -> viewModel.playArtist(artist.artistKey) },
                                onShuffleArtist = { artist -> viewModel.shuffleArtist(artist.artistKey) },
                                onPlayFolder = { folder -> viewModel.playFolder(folder.folderKey) },
                                onOpenAlbum = { album ->
                                    detailReturnPage = EchoPagerPage.Library
                                    selectedArtist = null
                                    selectedFolder = null
                                    selectedAlbum = album
                                },
                                onOpenArtist = { artist ->
                                    detailReturnPage = EchoPagerPage.Library
                                    selectedAlbum = null
                                    selectedFolder = null
                                    selectedArtist = artist
                                },
                                onOpenFolder = { folder ->
                                    detailReturnPage = EchoPagerPage.Library
                                    selectedAlbum = null
                                    selectedArtist = null
                                    selectedFolder = folder
                                },
                                onCloseDetail = { closeLibraryDetail() },
                            )

                            EchoPagerPage.Now -> HomeScreen(
                                status = playbackStatus,
                                trackCount = libraryStats.trackCount,
                                albumCount = libraryStats.albumCount,
                                artistCount = libraryStats.artistCount,
                                recentPlayedAlbums = recentPlaybackAlbums,
                                recentlyAddedAlbums = recentlyAddedAlbums,
                                recommendedAlbums = homeRecommendedAlbums,
                                topArtists = recentPlaybackArtists,
                                favoriteAlbums = recentPlaybackAlbums.take(4),
                                heatmapDays = recentPlaybackHeatmap,
                                onPlayPause = viewModel::playPause,
                                onNext = viewModel::skipNext,
                                onPrevious = viewModel::skipPrevious,
                                onCycleRepeatMode = viewModel::cycleRepeatMode,
                                onToggleShuffle = viewModel::toggleShuffle,
                                onRefreshRecommendations = {
                                    homeRecommendationSeed += 1
                                    viewModel.refreshLibrary()
                                },
                                onOpenAlbum = { album ->
                                    detailReturnPage = EchoPagerPage.Now
                                    selectedArtist = null
                                    selectedFolder = null
                                    selectedAlbum = album
                                    selectDockTab(EchoTab.Library)
                                },
                                onOpenArtist = { artist ->
                                    detailReturnPage = EchoPagerPage.Now
                                    selectedAlbum = null
                                    selectedFolder = null
                                    selectedArtist = artist
                                    selectDockTab(EchoTab.Library)
                                },
                                onOpenLibrary = { selectDockTab(EchoTab.Library) },
                                onOpenConnect = { selectDockTab(EchoTab.Connect) },
                            )

                            EchoPagerPage.Settings -> SettingsScreen(
                                status = playbackStatus,
                                trackCount = libraryStats.trackCount,
                                albumCount = libraryStats.albumCount,
                                artistCount = libraryStats.artistCount,
                                dynamicArtworkEnabled = appSettings.dynamicArtworkEnabled,
                                compactModeEnabled = appSettings.compactModeEnabled,
                                pcHandoffEnabled = appSettings.pcHandoffEnabled,
                                discordPresenceViaPcEnabled = appSettings.discordPresenceViaPcEnabled,
                                showLyricsControlDeck = appSettings.showLyricsControlDeck,
                                onlineLyricsEnabled = appSettings.onlineLyricsEnabled,
                                usbExclusiveEnabled = appSettings.usbExclusiveEnabled,
                                usbExclusiveAutoRequestOnStartup = appSettings.usbExclusiveAutoRequestOnStartup,
                                usbExclusiveTestResult = usbExclusiveTestResult,
                                customBackgroundMode = appSettings.customBackgroundMode,
                                customBackgroundUri = appSettings.customBackgroundUri,
                                customBackgroundBlur = appSettings.customBackgroundBlur,
                                customBackgroundBrightness = appSettings.customBackgroundBrightness,
                                customBackgroundGlass = appSettings.customBackgroundGlass,
                                uiFontFamily = appSettings.uiFontFamily,
                                uiFontScale = appSettings.uiFontScale,
                                uiDensityScale = appSettings.uiDensityScale,
                                lyricsFontFamily = appSettings.lyricsFontFamily,
                                lyricsFontScale = appSettings.lyricsFontScale,
                                importedFontUri = appSettings.importedFontUri,
                                themeMode = appSettings.themeMode,
                                scheduledDarkModeEnabled = appSettings.scheduledDarkModeEnabled,
                                scheduledDarkStartMinute = appSettings.scheduledDarkStartMinute,
                                scheduledDarkEndMinute = appSettings.scheduledDarkEndMinute,
                                lastFmEnabled = appSettings.lastFmEnabled,
                                lastFmApiKey = lastFmApiKey,
                                lastFmSharedSecret = lastFmSharedSecret,
                                lastFmSessionKey = appSettings.lastFmSessionKey,
                                lastFmStatusLabel = lastFmState.lastMessage,
                                lastFmErrorLabel = lastFmState.lastError,
                                lastFmWebAuthPending = lastFmState.webAuthPending,
                                lastFmApiKeyLocked = LastFmApiConfig.hasApiKey,
                                lastFmSharedSecretLocked = LastFmApiConfig.hasSharedSecret,
                                onDynamicArtworkEnabledChange = viewModel::setDynamicArtworkEnabled,
                                onCompactModeEnabledChange = viewModel::setCompactModeEnabled,
                                onPcHandoffEnabledChange = viewModel::setPcHandoffEnabled,
                                onDiscordPresenceViaPcEnabledChange = viewModel::setDiscordPresenceViaPcEnabled,
                                onShowLyricsControlDeckChange = viewModel::setShowLyricsControlDeck,
                                onOnlineLyricsEnabledChange = viewModel::setOnlineLyricsEnabled,
                                onUsbExclusiveEnabledChange = viewModel::setUsbExclusiveEnabled,
                                onUsbExclusiveAutoRequestOnStartupChange = viewModel::setUsbExclusiveAutoRequestOnStartup,
                                onTestUsbExclusiveDriver = viewModel::testUsbExclusiveDriver,
                                onPickImageBackground = { backgroundImageLauncher.launch(arrayOf("image/*")) },
                                onPickVideoBackground = { backgroundVideoLauncher.launch(arrayOf("video/*")) },
                                onClearCustomBackground = {
                                    viewModel.setCustomBackground(EchoBackgroundMode.Default, null)
                                },
                                onCustomBackgroundBlurChange = viewModel::setCustomBackgroundBlur,
                                onCustomBackgroundBrightnessChange = viewModel::setCustomBackgroundBrightness,
                                onCustomBackgroundGlassChange = viewModel::setCustomBackgroundGlass,
                                onUiFontFamilyChange = viewModel::setUiFontFamily,
                                onUiFontScaleChange = viewModel::setUiFontScale,
                                onUiDensityScaleChange = viewModel::setUiDensityScale,
                                onLyricsFontFamilyChange = viewModel::setLyricsFontFamily,
                                onLyricsFontScaleChange = viewModel::setLyricsFontScale,
                                onImportUiFont = {
                                    fontImportTarget = FontImportTarget.Ui
                                    fontImportLauncher.launch(FontDocumentMimeTypes)
                                },
                                onImportLyricsFont = {
                                    fontImportTarget = FontImportTarget.Lyrics
                                    fontImportLauncher.launch(FontDocumentMimeTypes)
                                },
                                onClearImportedFont = {
                                    viewModel.setImportedFontUri(null)
                                },
                                onThemeModeChange = viewModel::setThemeMode,
                                onScheduledDarkModeEnabledChange = viewModel::setScheduledDarkModeEnabled,
                                onScheduledDarkStartMinuteChange = viewModel::setScheduledDarkStartMinute,
                                onScheduledDarkEndMinuteChange = viewModel::setScheduledDarkEndMinute,
                                onLastFmEnabledChange = viewModel::setLastFmEnabled,
                                onStartLastFmWebAuth = {
                                    viewModel.startLastFmWebAuth { authUrl ->
                                        runCatching {
                                            context.startActivity(
                                                Intent(
                                                    Intent.ACTION_VIEW,
                                                    android.net.Uri.parse(authUrl),
                                                ),
                                            )
                                        }
                                    }
                                },
                                onCompleteLastFmWebAuth = viewModel::completeLastFmWebAuth,
                                onDisconnectLastFm = viewModel::disconnectLastFm,
                                onOpenLastFmApiAccounts = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(
                                                Intent.ACTION_VIEW,
                                                android.net.Uri.parse("https://www.last.fm/api/accounts"),
                                            ),
                                        )
                                    }
                                },
                                onOpenLibrary = { selectDockTab(EchoTab.Library) },
                                onOpenConnect = { selectDockTab(EchoTab.Connect) },
                            )

                            EchoPagerPage.Connect -> ConnectScreen(
                                remoteState = remoteStatus.connectionState,
                                pcTitle = remoteStatus.endpoint?.name ?: "PC ECHO",
                                trackTitle = remoteStatus.playback.track?.title ?: "未连接",
                                trackArtist = remoteStatus.playback.track?.artist ?: "点按配对",
                                isPlaying = remoteStatus.playback.state == EchoRemotePlaybackState.Playing,
                                discordPresenceEnabled = appSettings.discordPresenceViaPcEnabled,
                                discordPresenceReady = remoteStatus.connectionState == EchoRemoteConnectionState.Connected &&
                                    remoteStatus.mobileDiscordPresence?.enabled == true,
                                discordPresenceTrackTitle = remoteStatus.mobileDiscordPresence?.track?.title,
                                subsonicServerUrl = appSettings.subsonicServerUrl,
                                subsonicUsername = appSettings.subsonicUsername,
                                subsonicPassword = appSettings.subsonicPassword,
                                webDavServerUrl = appSettings.webDavServerUrl,
                                webDavUsername = appSettings.webDavUsername,
                                webDavPassword = appSettings.webDavPassword,
                                remoteScanState = remoteScanState,
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
                                onSyncSubsonicLibrary = viewModel::syncSubsonicLibrary,
                                onSaveSubsonicCredentials = viewModel::saveSubsonicCredentials,
                                onClearSubsonicCredentials = viewModel::clearSubsonicCredentials,
                                onSyncWebDavLibrary = viewModel::syncWebDavLibrary,
                                onSaveWebDavCredentials = viewModel::saveWebDavCredentials,
                                onClearWebDavCredentials = viewModel::clearWebDavCredentials,
                                onCancelRemoteSync = viewModel::cancelRemoteSync,
                            )

                            EchoPagerPage.Diagnostics -> {
                                val equalizerState by viewModel.equalizerState.collectAsStateWithLifecycle()
                                val opraState by viewModel.opraState.collectAsStateWithLifecycle()
                                DiagnosticsScreen(
                                    status = playbackStatus,
                                    equalizerState = equalizerState,
                                    opraState = opraState,
                                    onEqualizerEnabledChange = viewModel::setEqualizerEnabled,
                                    onEqualizerPresetSelected = viewModel::setEqualizerPreset,
                                    onEqualizerBandGainChange = viewModel::setEqualizerBandGain,
                                    onEqualizerReset = viewModel::resetEqualizer,
                                    onOpraQueryChange = viewModel::updateOpraQuery,
                                    onOpraSearch = { viewModel.searchOpraHeadphoneCorrections(refresh = false) },
                                    onOpraRefresh = { viewModel.searchOpraHeadphoneCorrections(refresh = true) },
                                    onOpraPresetSelected = viewModel::selectOpraPreset,
                                    onOpraApplySelected = viewModel::applySelectedOpraPreset,
                                )
                            }
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(top = 6.dp),
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
                        val playbackPosition by viewModel.playbackPosition.collectAsStateWithLifecycle()
                        if (it) {
                            ExpandedBottomControls(
                                status = playbackStatus,
                                positionState = playbackPosition,
                                darkTheme = darkTheme,
                                selectedTab = selectedTab,
                                onPlayPause = viewModel::playPause,
                                onHideDock = { bottomDockExpanded = false },
                                onSelectTab = { selectDockTab(EchoTab.entries[it]) },
                                onExpand = { nowPlayingExpanded = true },
                                onNext = viewModel::skipNext,
                                onPrevious = viewModel::skipPrevious,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            CompactBottomControls(
                                status = playbackStatus,
                                positionState = playbackPosition,
                                darkTheme = darkTheme,
                                onPlayPause = viewModel::playPause,
                                onShowDock = { bottomDockExpanded = true },
                                onOpenQueue = { queueSheetVisible = true },
                                onExpand = { nowPlayingExpanded = true },
                                onNext = viewModel::skipNext,
                                onPrevious = viewModel::skipPrevious,
                                modifier = Modifier
                                    .widthIn(max = EchoContentMaxWidth)
                                    .fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = nowPlayingExpanded,
                enter = slideInVertically(tween(durationMillis = 360, easing = DockMotionEasing)) { height -> height } +
                    fadeIn(tween(durationMillis = 220)),
                exit = slideOutVertically(tween(durationMillis = 180, easing = DockMotionEasing)) { height -> height / 10 } +
                    fadeOut(tween(durationMillis = 140)),
            ) {
                val playbackPosition by viewModel.playbackPosition.collectAsStateWithLifecycle()
                NowPlayingScreen(
                    status = playbackStatus,
                    positionState = playbackPosition,
                    lyricsState = lyricsState,
                    showLyricsControlDeck = appSettings.showLyricsControlDeck,
                    lyricsFontFamily = lyricsFontFamily,
                    lyricsFontMode = appSettings.lyricsFontFamily,
                    lyricsFontScale = appSettings.lyricsFontScale,
                    lyricsColorMode = appSettings.lyricsColorMode,
                    lyricsShowTranslation = appSettings.lyricsShowTranslation,
                    lyricsShowRomanization = appSettings.lyricsShowRomanization,
                    lyricsFocusGlowEnabled = appSettings.lyricsFocusGlowEnabled,
                    importedFontUri = appSettings.importedFontUri,
                    onlineLyricsEnabled = appSettings.onlineLyricsEnabled,
                    onDismiss = { nowPlayingExpanded = false },
                    onPlayPause = viewModel::playPause,
                    onNext = viewModel::skipNext,
                    onPrevious = viewModel::skipPrevious,
                    onSeek = viewModel::seekTo,
                    onOpenQueue = { queueSheetVisible = true },
                    onImportLyrics = { lyricsImportLauncher.launch(LyricsDocumentMimeTypes) },
                    onImportLyricsFont = {
                        fontImportTarget = FontImportTarget.Lyrics
                        fontImportLauncher.launch(FontDocumentMimeTypes)
                    },
                    onAdjustLyricsOffset = viewModel::adjustLyricsOffset,
                    onResetLyricsOffset = viewModel::resetLyricsOffset,
                    onLyricsFontFamilyChange = viewModel::setLyricsFontFamily,
                    onLyricsFontScaleChange = viewModel::setLyricsFontScale,
                    onLyricsColorModeChange = viewModel::setLyricsColorMode,
                    onLyricsShowTranslationChange = viewModel::setLyricsShowTranslation,
                    onLyricsShowRomanizationChange = viewModel::setLyricsShowRomanization,
                    onLyricsFocusGlowChange = viewModel::setLyricsFocusGlowEnabled,
                    onShowLyricsControlDeckChange = viewModel::setShowLyricsControlDeck,
                    onOnlineLyricsEnabledChange = viewModel::setOnlineLyricsEnabled,
                    onOpenArtist = {
                        viewModel.openCurrentPlaybackArtist { artist ->
                            detailReturnPage = EchoTab.entries[selectedTab].pagerPage
                            selectedAlbum = null
                            selectedFolder = null
                            selectedArtist = artist
                            selectDockTab(EchoTab.Library)
                            nowPlayingExpanded = false
                        }
                    },
                    onOpenAlbum = {
                        viewModel.openCurrentPlaybackAlbum { album ->
                            detailReturnPage = EchoTab.entries[selectedTab].pagerPage
                            selectedArtist = null
                            selectedFolder = null
                            selectedAlbum = album
                            selectDockTab(EchoTab.Library)
                            nowPlayingExpanded = false
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            PlaybackQueueSheet(
                visible = queueSheetVisible,
                status = playbackStatus,
                queueState = playbackQueue,
                onDismiss = { queueSheetVisible = false },
                onPlayItem = viewModel::playQueueItem,
                onRemoveItem = viewModel::removeQueueItem,
                onClearQueue = viewModel::clearQueue,
                onCycleRepeatMode = viewModel::cycleRepeatMode,
                onToggleShuffle = viewModel::toggleShuffle,
                onOpenLibrary = {
                    queueSheetVisible = false
                    nowPlayingExpanded = false
                    selectDockTab(EchoTab.Library)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ExpandedBottomControls(
    status: app.echo.android.model.playback.EchoPlaybackStatus,
    positionState: PlaybackPositionState,
    darkTheme: Boolean,
    selectedTab: Int,
    onPlayPause: () -> Unit,
    onHideDock: () -> Unit,
    onSelectTab: (Int) -> Unit,
    onExpand: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.background(
            if (darkTheme) {
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        EchoGlassPanel.copy(alpha = 0.44f),
                        EchoGlassPanel.copy(alpha = 0.72f),
                    ),
                )
            } else {
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        Color(0xFFEAF2FF).copy(alpha = 0.76f),
                        Color(0xFFEAF2FF).copy(alpha = 0.96f),
                    ),
                )
            },
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        MiniPlayer(
            status = status,
            positionState = positionState,
            onPlayPause = onPlayPause,
            onHideDock = onHideDock,
            onExpand = onExpand,
            onNext = onNext,
            onPrevious = onPrevious,
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .fillMaxWidth(),
        )
        BottomDock(
            selectedTab = selectedTab,
            onLightSurface = !darkTheme,
            onSelectTab = onSelectTab,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CompactBottomControls(
    status: app.echo.android.model.playback.EchoPlaybackStatus,
    positionState: PlaybackPositionState,
    darkTheme: Boolean,
    onPlayPause: () -> Unit,
    onShowDock: () -> Unit,
    onOpenQueue: () -> Unit,
    onExpand: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(
                if (darkTheme) {
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            EchoGlassPanel.copy(alpha = 0.40f),
                            EchoGlassPanel.copy(alpha = 0.66f),
                        ),
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color(0xFFEAF2FF).copy(alpha = 0.82f),
                        ),
                    )
                },
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        MiniPlayer(
            status = status,
            positionState = positionState,
            onPlayPause = onPlayPause,
            onShowDock = onShowDock,
            onOpenQueue = onOpenQueue,
            onExpand = onExpand,
            onNext = onNext,
            onPrevious = onPrevious,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun currentMinuteNow(): Int {
    val now = LocalTime.now()
    return now.hour * 60 + now.minute
}

private fun resolveDarkTheme(
    systemDarkTheme: Boolean,
    themeMode: String,
    scheduledDarkModeEnabled: Boolean,
    scheduledStartMinute: Int,
    scheduledEndMinute: Int,
    currentMinute: Int,
): Boolean {
    if (scheduledDarkModeEnabled && isMinuteInScheduledWindow(currentMinute, scheduledStartMinute, scheduledEndMinute)) {
        return true
    }
    return when (themeMode) {
        EchoThemeMode.Light -> false
        EchoThemeMode.Dark -> true
        else -> systemDarkTheme
    }
}

private fun isMinuteInScheduledWindow(
    currentMinute: Int,
    startMinute: Int,
    endMinute: Int,
): Boolean {
    val current = currentMinute.coerceIn(0, 23 * 60 + 59)
    val start = startMinute.coerceIn(0, 23 * 60 + 59)
    val end = endMinute.coerceIn(0, 23 * 60 + 59)
    return when {
        start == end -> false
        start < end -> current in start until end
        else -> current >= start || current < end
    }
}
