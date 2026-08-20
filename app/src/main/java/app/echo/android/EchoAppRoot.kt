package app.echo.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.PowerManager
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.echo.android.data.LocalLibrarySearchResults
import app.echo.android.feature.home.SearchResult
import app.echo.android.feature.home.SearchResultType
import app.echo.android.connect.EchoPairingParser
import app.echo.android.connect.EchoRemoteClient
import app.echo.android.design.EchoArtworkRequestHeadersRegistry
import app.echo.android.design.EchoMobileTheme
import app.echo.android.feature.connect.ConnectScreen
import app.echo.android.feature.home.SearchScreen
import app.echo.android.feature.player.PlaybackQueueSheet
import app.echo.android.feature.settings.DiagnosticsScreen
import app.echo.android.feature.settings.SettingsScreen
import app.echo.android.ui.discord.EchoDiscordPresenceBridge
import app.echo.android.ui.home.EchoHomePage
import app.echo.android.ui.library.EchoLibraryPage
import app.echo.android.ui.playback.EchoNowPlayingHost
import app.echo.android.ui.shell.EchoBottomDockHost
import app.echo.android.ui.shell.EchoPagerPage
import app.echo.android.ui.shell.dockTab
import app.echo.android.ui.shell.motionDuration
import app.echo.android.ui.shell.pagerPage
import app.echo.android.ui.shell.routeMotionSpec
import app.echo.android.data.EchoBackgroundMode
import app.echo.android.data.EchoFontFamilyMode
import app.echo.android.data.toEchoTrack
import app.echo.android.model.connect.EchoRemoteCommand
import app.echo.android.model.connect.EchoRemoteConnectionState
import app.echo.android.model.connect.EchoRemoteEndpoint
import app.echo.android.model.connect.EchoRemotePlaybackState
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.ArtistSummary
import app.echo.android.model.library.EchoPlaylist
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.FolderSummary
import app.echo.android.model.library.LibraryStats
import app.echo.android.model.settings.EchoEffectivePerformanceMode
import app.echo.android.model.settings.EchoPerformanceMode
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import app.echo.android.design.echoFontFamilyForMode
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Notifications
import android.provider.Settings
import android.net.Uri as AndroidUri
import kotlin.math.absoluteValue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val DockMotionEasing = CubicBezierEasing(0.16f, 1f, 0.30f, 1f)
private val LyricsDocumentMimeTypes = arrayOf("text/*", "application/xml", "application/octet-stream", "*/*")
private val ArtworkDocumentMimeTypes = arrayOf("image/*", "application/octet-stream", "*/*")
private val FontDocumentMimeTypes = arrayOf("font/*", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream", "*/*")

private enum class FontImportTarget {
    Ui,
    Lyrics,
}

@Suppress("SpellCheckingInspection")
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
    val notifPermName = remember { notificationPermissionName() }
    var hasNotifPermission by remember {
        mutableStateOf(
            notifPermName == null || ContextCompat.checkSelfPermission(context, notifPermName) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val notifPermissionLauncher = notifPermName?.let { _ ->
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasNotifPermission = granted
        }
    }
    val prefs = remember(context) { context.getSharedPreferences("echo_prefs", Context.MODE_PRIVATE) }
    var showPermissionDialog by remember {
        mutableStateOf(!prefs.getBoolean(ECHO_PERMISSION_DIALOG_SHOWN_KEY, false))
    }
    fun dismissPermissionDialog() {
        showPermissionDialog = false
        prefs.edit { putBoolean(ECHO_PERMISSION_DIALOG_SHOWN_KEY, true) }
    }
    fun persistReadPermission(uri: AndroidUri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }
    val folderScanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { treeUri ->
            persistReadPermission(treeUri)
            viewModel.refreshLibraryFolder(treeUri)
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
    var lyricsImportTrackId by remember { mutableStateOf<String?>(null) }
    val lyricsImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { lyricsUri ->
            persistReadPermission(lyricsUri)
            lyricsImportTrackId?.let { trackId ->
                viewModel.importLyricsForTrack(trackId, lyricsUri)
            } ?: viewModel.importLyrics(lyricsUri)
        }
        lyricsImportTrackId = null
    }
    var artworkImportTrackId by remember { mutableStateOf<String?>(null) }
    val artworkImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { artworkUri ->
            persistReadPermission(artworkUri)
            artworkImportTrackId?.let { trackId ->
                viewModel.updateTrackArtwork(trackId, artworkUri)
            }
        }
        artworkImportTrackId = null
    }

    val remoteScope = rememberCoroutineScope()
    val remoteClient = remember(remoteScope) { EchoRemoteClient(remoteScope) }
    val remoteStatus by remoteClient.status.collectAsStateWithLifecycle()
    val remoteLibraryState by remoteClient.library.collectAsStateWithLifecycle()
    val playbackStatus by viewModel.playbackStatus.collectAsStateWithLifecycle()
    val playbackQueue by viewModel.playbackQueue.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle(viewModel.initialAppSettings)
    val systemPowerSaveMode = rememberSystemPowerSaveMode()
    val effectivePerformanceMode = remember(appSettings.performanceMode, systemPowerSaveMode) {
        EchoPerformanceMode.fromId(appSettings.performanceMode).resolve(systemPowerSaveMode)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    var appVisible by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> appVisible = true
                Lifecycle.Event.ON_STOP -> appVisible = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(effectivePerformanceMode) {
        viewModel.setEffectivePerformanceMode(effectivePerformanceMode)
    }
    var lastEchoLinkAutoConnectKey by remember { mutableStateOf<String?>(null) }
    val echoLinkSavedKey = remember(appSettings.echoLinkPcAddress, appSettings.echoLinkPcToken) {
        val address = appSettings.echoLinkPcAddress?.takeIf { it.isNotBlank() }
        val token = appSettings.echoLinkPcToken?.takeIf { it.isNotBlank() }
        if (address != null && token != null) "$address|$token" else null
    }
    val echoLinkQrScanner = remember(context) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        GmsBarcodeScanning.getClient(context, options)
    }
    var echoLinkScanMessage by remember { mutableStateOf<String?>(null) }
    var echoLinkFallbackScannerVisible by remember { mutableStateOf(false) }

    fun connectEchoLinkEndpoint(endpoint: EchoRemoteEndpoint) {
        echoLinkScanMessage = null
        echoLinkFallbackScannerVisible = false
        viewModel.saveEchoLinkPcEndpoint(
            address = "${endpoint.scheme}://${endpoint.host}:${endpoint.port}",
            token = endpoint.token,
        )
        remoteClient.connect(
            nextEndpoint = endpoint,
            refreshLibraryOnConnect = appSettings.echoLinkPreferLinkedLibrary,
        )
    }

    fun connectEchoLinkAddress(address: String, token: String) {
        val endpoint = EchoPairingParser.parseManual(address, token)
        if (endpoint != null) {
            connectEchoLinkEndpoint(endpoint)
        } else {
            echoLinkScanMessage = null
            remoteClient.connectManual(
                address = address,
                token = token,
                refreshLibraryOnConnect = appSettings.echoLinkPreferLinkedLibrary,
            )
        }
    }

    fun scanEchoLinkPairingCode() {
        echoLinkScanMessage = null
        echoLinkFallbackScannerVisible = false
        echoLinkQrScanner.startScan()
            .addOnSuccessListener { barcode ->
                val endpoint = barcode.rawValue
                    ?.let(EchoPairingParser::parse)
                if (endpoint != null) {
                    connectEchoLinkEndpoint(endpoint)
                } else {
                    echoLinkScanMessage = "没有识别到 ECHO Link 配对码"
                }
            }
            .addOnCanceledListener {
                echoLinkScanMessage = "已取消扫码"
            }
            .addOnFailureListener { error ->
                echoLinkFallbackScannerVisible = true
                val detail = error.localizedMessage
                    ?.takeIf { it.isNotBlank() }
                    ?: error.message?.takeIf { it.isNotBlank() }
                echoLinkScanMessage = detail?.let { "扫码不可用：$it" } ?: "扫码不可用，请手动输入配对码"
            }
    }

    LaunchedEffect(echoLinkSavedKey, appSettings.echoLinkAutoReconnectEnabled) {
        val address = appSettings.echoLinkPcAddress?.takeIf { it.isNotBlank() }
        val token = appSettings.echoLinkPcToken?.takeIf { it.isNotBlank() }
        if (!appSettings.echoLinkAutoReconnectEnabled) {
            lastEchoLinkAutoConnectKey = null
            return@LaunchedEffect
        }
        if (
            address != null &&
            token != null &&
            echoLinkSavedKey != null &&
            lastEchoLinkAutoConnectKey != echoLinkSavedKey
        ) {
            lastEchoLinkAutoConnectKey = echoLinkSavedKey
            remoteClient.connectManual(
                address = address,
                token = token,
                refreshLibraryOnConnect = appSettings.echoLinkPreferLinkedLibrary,
            )
        }
    }
    val lastFmState by viewModel.lastFmState.collectAsStateWithLifecycle()
    val usbExclusiveTestResult by viewModel.usbExclusiveTestResult.collectAsStateWithLifecycle()
    val lastFmApiKey = appSettings.lastFmApiKey?.takeIf { it.isNotBlank() }
        ?: LastFmApiConfig.API_KEY.takeIf { it.isNotBlank() }
    val lastFmSharedSecret = appSettings.lastFmSharedSecret?.takeIf { it.isNotBlank() }
        ?: LastFmApiConfig.SHARED_SECRET.takeIf { it.isNotBlank() }
    val remoteScanState by viewModel.remoteScanState.collectAsStateWithLifecycle()
    var selectedAlbum by remember { mutableStateOf<AlbumSummary?>(null) }
    var selectedArtist by remember { mutableStateOf<ArtistSummary?>(null) }
    var selectedFolder by remember { mutableStateOf<FolderSummary?>(null) }
    var selectedPlaylist by remember { mutableStateOf<EchoPlaylist?>(null) }
    var detailReturnPage by remember { mutableStateOf<EchoPagerPage?>(null) }
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(EchoTab.Now.ordinal) }
    var bottomDockExpanded by remember { mutableStateOf(true) }
    var nowPlayingExpanded by remember { mutableStateOf(false) }
    var queueSheetVisible by remember { mutableStateOf(false) }
    val libraryDetailOpen = selectedAlbum != null || selectedArtist != null || selectedFolder != null || selectedPlaylist != null
    LaunchedEffect(effectivePerformanceMode, appVisible, nowPlayingExpanded) {
        val visibility = when {
            !appVisible -> PlaybackProgressUiVisibility.Background
            nowPlayingExpanded -> PlaybackProgressUiVisibility.NowPlayingExpanded
            else -> PlaybackProgressUiVisibility.MiniPlayer
        }
        viewModel.setPlaybackProgressUiVisibility(visibility)
    }
    val systemDarkTheme = isSystemInDarkTheme()
    var currentMinuteOfDay by remember { mutableIntStateOf(currentMinuteOfDayNow()) }
    LaunchedEffect(appSettings.scheduledDarkModeEnabled) {
        if (appSettings.scheduledDarkModeEnabled) {
            while (true) {
                currentMinuteOfDay = currentMinuteOfDayNow()
                delay(1.minutes)
            }
        } else {
            currentMinuteOfDay = currentMinuteOfDayNow()
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
        resolveEchoDarkTheme(
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

    LaunchedEffect(darkTheme, effectivePerformanceMode.prefersHighRefreshRate) {
        (activity as? MainActivity)?.setHighRefreshRateRequested(effectivePerformanceMode.prefersHighRefreshRate)
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
    fun needsPagerSettle(targetPage: Int): Boolean =
        tabPagerState.settledPage != targetPage ||
            tabPagerState.currentPage != targetPage ||
            tabPagerState.currentPageOffsetFraction.absoluteValue > 0.001f
    fun navigateToPage(page: EchoPagerPage) {
        val targetPage = page.ordinal
        page.dockTab?.let { selectedTab = it.ordinal }
        routeNavigationJob[0]?.cancel()
        routeNavigationJob[0] = appScope.launch {
            if (needsPagerSettle(targetPage)) {
                if (effectivePerformanceMode.isLightweight) {
                    tabPagerState.scrollToPage(targetPage)
                } else {
                    tabPagerState.animateScrollToPage(
                        page = targetPage,
                        animationSpec = routeMotionSpec(tabPagerState.currentPage, targetPage, effectivePerformanceMode),
                    )
                }
            }
        }
    }
    fun selectDockTab(tab: EchoTab) = navigateToPage(tab.pagerPage)
    fun clearLibraryDetail() {
        selectedAlbum = null
        selectedArtist = null
        selectedFolder = null
        selectedPlaylist = null
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
                if (needsPagerSettle(targetPage)) {
                    if (effectivePerformanceMode.isLightweight) {
                        tabPagerState.scrollToPage(targetPage)
                    } else {
                        tabPagerState.animateScrollToPage(
                            page = targetPage,
                            animationSpec = routeMotionSpec(tabPagerState.currentPage, targetPage, effectivePerformanceMode),
                        )
                    }
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
    LaunchedEffect(tabPagerState.isScrollInProgress, tabPagerState.currentPage) {
        if (!tabPagerState.isScrollInProgress && tabPagerState.currentPageOffsetFraction.absoluteValue > 0.001f) {
            if (effectivePerformanceMode.isLightweight) {
                tabPagerState.scrollToPage(tabPagerState.currentPage)
            } else {
                tabPagerState.animateScrollToPage(
                    page = tabPagerState.currentPage,
                    animationSpec = routeMotionSpec(
                        tabPagerState.settledPage,
                        tabPagerState.currentPage,
                        effectivePerformanceMode,
                    ),
                )
            }
        }
    }

    LaunchedEffect(remoteStatus.connectionState, appSettings.echoLinkPreferLinkedLibrary) {
        if (
            remoteStatus.connectionState == EchoRemoteConnectionState.Connected &&
            appSettings.echoLinkPreferLinkedLibrary &&
            tabPagerState.currentPage == EchoPagerPage.Connect.ordinal
        ) {
            selectDockTab(EchoTab.Library)
        }
    }

    LaunchedEffect(hasAudioPermission) {
        if (hasAudioPermission) {
            viewModel.refreshLibraryIfEmpty()
        }
    }

    EchoDiscordPresenceBridge(
        enabled = appSettings.discordPresenceViaPcEnabled,
        snapshots = viewModel.discordPresenceSnapshot,
        publish = remoteClient::publishMobileDiscordPresence,
    )

    LaunchedEffect(remoteStatus.endpoint, remoteStatus.connectionState) {
        val endpoint = remoteStatus.endpoint
        if (remoteStatus.connectionState == EchoRemoteConnectionState.Connected && endpoint != null) {
            viewModel.saveEchoLinkPcEndpoint(
                address = "${endpoint.scheme}://${endpoint.host}:${endpoint.port}",
                token = endpoint.token,
            )
        }
        EchoArtworkRequestHeadersRegistry.replaceEchoLinkAuthorization(
            baseUrl = endpoint?.let { "${it.scheme}://${it.host}:${it.port}" },
            token = endpoint?.token,
        )
    }

    BackHandler(enabled = searchVisible) {
        searchVisible = false
        searchQuery = ""
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
        effectivePerformanceMode = effectivePerformanceMode,
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
                            EchoPagerPage.Library -> EchoLibraryPage(
                                viewModel = viewModel,
                                remoteClient = remoteClient,
                                remoteStatus = remoteStatus,
                                appSettings = appSettings,
                                hasAudioPermission = hasAudioPermission,
                                selectedAlbum = selectedAlbum,
                                selectedArtist = selectedArtist,
                                selectedFolder = selectedFolder,
                                selectedPlaylist = selectedPlaylist,
                                onRequestPermission = { permissionLauncher.launch(permission) },
                                onScanFolder = { folderScanLauncher.launch(null) },
                                onImportLyricsForTrack = { track ->
                                    lyricsImportTrackId = track.id
                                    lyricsImportLauncher.launch(LyricsDocumentMimeTypes)
                                },
                                onPickTrackArtwork = { track ->
                                    artworkImportTrackId = track.id
                                    artworkImportLauncher.launch(ArtworkDocumentMimeTypes)
                                },
                                onOpenAlbum = { album ->
                                    detailReturnPage = EchoPagerPage.Library
                                    selectedArtist = null
                                    selectedFolder = null
                                    selectedPlaylist = null
                                    selectedAlbum = album
                                },
                                onOpenArtist = { artist ->
                                    detailReturnPage = EchoPagerPage.Library
                                    selectedAlbum = null
                                    selectedFolder = null
                                    selectedPlaylist = null
                                    selectedArtist = artist
                                },
                                onOpenFolder = { folder ->
                                    detailReturnPage = EchoPagerPage.Library
                                    selectedAlbum = null
                                    selectedArtist = null
                                    selectedPlaylist = null
                                    selectedFolder = folder
                                },
                                onOpenPlaylist = { playlist ->
                                    detailReturnPage = EchoPagerPage.Library
                                    selectedAlbum = null
                                    selectedArtist = null
                                    selectedFolder = null
                                    selectedPlaylist = playlist
                                },
                                onCloseDetail = { closeLibraryDetail() },
                            )

                            EchoPagerPage.Now -> EchoHomePage(
                                viewModel = viewModel,
                                playbackStatus = playbackStatus,
                                onOpenAlbum = { album ->
                                    detailReturnPage = EchoPagerPage.Now
                                    selectedArtist = null
                                    selectedFolder = null
                                    selectedPlaylist = null
                                    selectedAlbum = album
                                    selectDockTab(EchoTab.Library)
                                },
                                onOpenArtist = { artist ->
                                    detailReturnPage = EchoPagerPage.Now
                                    selectedAlbum = null
                                    selectedFolder = null
                                    selectedPlaylist = null
                                    selectedArtist = artist
                                    selectDockTab(EchoTab.Library)
                                },
                                onOpenLibrary = { selectDockTab(EchoTab.Library) },
                                onOpenConnect = { selectDockTab(EchoTab.Connect) },
                                onOpenSearch = { searchVisible = true },
                            )

                            EchoPagerPage.Settings -> {
                            val libraryStats by viewModel.libraryStats.collectAsStateWithLifecycle(LibraryStats())
                            SettingsScreen(
                                status = playbackStatus,
                                trackCount = libraryStats.trackCount,
                                albumCount = libraryStats.albumCount,
                                artistCount = libraryStats.artistCount,
                                appVersionLabel = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                dynamicArtworkEnabled = appSettings.dynamicArtworkEnabled,
                                compactModeEnabled = appSettings.compactModeEnabled,
                                performanceMode = appSettings.performanceMode,
                                effectivePerformanceMode = effectivePerformanceMode.id,
                                trackAudioInfoTagsVisible = appSettings.trackAudioInfoTagsVisible,
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
                                customBackgroundScale = appSettings.customBackgroundScale,
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
                                lastFmApiKeyLocked = LastFmApiConfig.HAS_API_KEY,
                                lastFmSharedSecretLocked = LastFmApiConfig.HAS_SHARED_SECRET,
                                onDynamicArtworkEnabledChange = viewModel::setDynamicArtworkEnabled,
                                onCompactModeEnabledChange = viewModel::setCompactModeEnabled,
                                onPerformanceModeChange = viewModel::setPerformanceMode,
                                onTrackAudioInfoTagsVisibleChange = viewModel::setTrackAudioInfoTagsVisible,
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
                                onCustomBackgroundScaleChange = viewModel::setCustomBackgroundScale,
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
                                                    AndroidUri.parse(authUrl),
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
                                                AndroidUri.parse("https://www.last.fm/api/accounts"),
                                            ),
                                        )
                                    }
                                },
                                onOpenLibrary = { selectDockTab(EchoTab.Library) },
                                onOpenConnect = { selectDockTab(EchoTab.Connect) },
                            )
                            }

                            EchoPagerPage.Connect -> ConnectScreen(
                                remoteState = remoteStatus.connectionState,
                                pcTitle = remoteStatus.endpoint?.name ?: "PC ECHO",
                                trackTitle = remoteStatus.playback.track?.title ?: "未连接",
                                trackArtist = remoteStatus.playback.track?.artist ?: "点按配对",
                                trackArtworkUrl = remoteStatus.playback.track?.artworkUrl,
                                isPlaying = remoteStatus.playback.state == EchoRemotePlaybackState.Playing,
                                remoteError = remoteStatus.error,
                                scanMessage = echoLinkScanMessage,
                                savedPcAddress = appSettings.echoLinkPcAddress,
                                savedPcToken = appSettings.echoLinkPcToken,
                                autoReconnectEnabled = appSettings.echoLinkAutoReconnectEnabled,
                                linkedLibraryDefault = appSettings.echoLinkPreferLinkedLibrary,
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
                                onConnectPc = ::connectEchoLinkAddress,
                                onScanPairingCode = ::scanEchoLinkPairingCode,
                                onPlayPause = { remoteClient.send(EchoRemoteCommand.PlayPause) },
                                onPrevious = { remoteClient.send(EchoRemoteCommand.Previous) },
                                onNext = { remoteClient.send(EchoRemoteCommand.Next) },
                                onDisconnect = remoteClient::disconnect,
                                onForgetPc = {
                                    remoteClient.disconnect()
                                    viewModel.clearEchoLinkPcEndpoint()
                                },
                                onAutoReconnectChange = viewModel::setEchoLinkAutoReconnectEnabled,
                                onLinkedLibraryDefaultChange = { enabled ->
                                    viewModel.setEchoLinkPreferLinkedLibrary(enabled)
                                    if (enabled && remoteStatus.connectionState == EchoRemoteConnectionState.Connected) {
                                        remoteClient.refreshLibrary()
                                    }
                                },
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
                EchoBottomDockHost(
                    viewModel = viewModel,
                    pagerState = tabPagerState,
                    playbackStatus = playbackStatus,
                    darkTheme = darkTheme,
                    selectedTab = selectedTab,
                    bottomDockExpanded = bottomDockExpanded,
                    effectivePerformanceMode = effectivePerformanceMode,
                    onPlayPause = viewModel::playPause,
                    onHideDock = { bottomDockExpanded = false },
                    onShowDock = { bottomDockExpanded = true },
                    onSelectTab = { selectDockTab(EchoTab.entries[it]) },
                    onExpand = { nowPlayingExpanded = true },
                    onOpenQueue = { queueSheetVisible = true },
                    onNext = viewModel::skipNext,
                    onPrevious = viewModel::skipPrevious,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }

            AnimatedVisibility(
                visible = nowPlayingExpanded,
                enter = if (effectivePerformanceMode.isLightweight) {
                    fadeIn(tween(durationMillis = motionDuration(90, effectivePerformanceMode)))
                } else {
                    slideInVertically(tween(durationMillis = motionDuration(420, effectivePerformanceMode), easing = DockMotionEasing)) { height -> height } +
                        fadeIn(
                            tween(
                                durationMillis = motionDuration(240, effectivePerformanceMode),
                                delayMillis = if (effectivePerformanceMode.isLightweight) 0 else 40,
                            ),
                        ) +
                        scaleIn(
                            initialScale = 0.985f,
                            animationSpec = tween(durationMillis = motionDuration(420, effectivePerformanceMode), easing = DockMotionEasing),
                        )
                },
                exit = if (effectivePerformanceMode.isLightweight) {
                    fadeOut(tween(durationMillis = motionDuration(90, effectivePerformanceMode)))
                } else {
                    slideOutVertically(tween(durationMillis = motionDuration(360, effectivePerformanceMode), easing = DockMotionEasing)) { height -> height } +
                        fadeOut(tween(durationMillis = motionDuration(220, effectivePerformanceMode), easing = DockMotionEasing)) +
                        scaleOut(
                            targetScale = 0.965f,
                            animationSpec = tween(durationMillis = motionDuration(360, effectivePerformanceMode), easing = DockMotionEasing),
                        )
                },
            ) {
                EchoNowPlayingHost(
                    viewModel = viewModel,
                    playbackStatus = playbackStatus,
                    appSettings = appSettings,
                    lyricsFontFamily = lyricsFontFamily,
                    onDismiss = { nowPlayingExpanded = false },
                    onOpenQueue = { queueSheetVisible = true },
                    onImportLyrics = { lyricsImportLauncher.launch(LyricsDocumentMimeTypes) },
                    onOpenArtist = {
                        viewModel.openCurrentPlaybackArtist { artist ->
                            detailReturnPage = EchoTab.entries[selectedTab].pagerPage
                            selectedAlbum = null
                            selectedFolder = null
                            selectedPlaylist = null
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
                            selectedPlaylist = null
                            selectedAlbum = album
                            selectDockTab(EchoTab.Library)
                            nowPlayingExpanded = false
                        }
                    },
                    onImportLyricsFont = {
                        fontImportTarget = FontImportTarget.Lyrics
                        fontImportLauncher.launch(FontDocumentMimeTypes)
                    },
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
            if (searchVisible) {
                val localSearchResults by produceState(
                    initialValue = LocalHomeSearchResults(),
                    key1 = searchQuery,
                ) {
                    val trimmedQuery = searchQuery.trim()
                    value = if (trimmedQuery.isBlank()) {
                        LocalHomeSearchResults()
                    } else {
                        delay(150.milliseconds)
                        viewModel.searchLocalLibrary(trimmedQuery).toHomeSearchResults()
                    }
                }
                val searchResults = remember(localSearchResults) { localSearchResults.toUiResults() }
                SearchScreen(
                    searchQuery = searchQuery,
                    searchResults = searchResults,
                    onSearchQueryChange = { searchQuery = it },
                    onSearchResultClick = { result ->
                        when (result.type) {
                            SearchResultType.Album -> {
                                localSearchResults.albums.find { it.albumKey == result.id }?.let { album ->
                                    searchVisible = false
                                    searchQuery = ""
                                    detailReturnPage = EchoPagerPage.Now
                                    selectedAlbum = album
                                    selectDockTab(EchoTab.Library)
                                }
                            }
                            SearchResultType.Artist -> {
                                localSearchResults.artists.find { it.artistKey == result.id }?.let { artist ->
                                    searchVisible = false
                                    searchQuery = ""
                                    detailReturnPage = EchoPagerPage.Now
                                    selectedArtist = artist
                                    selectDockTab(EchoTab.Library)
                                }
                            }
                            SearchResultType.Track -> {
                                searchVisible = false
                                searchQuery = ""
                                viewModel.playTrackFromLibrary(result.id)
                            }
                        }
                    },
                    onBack = {
                        searchVisible = false
                        searchQuery = ""
                    },
                )
            }
            EchoLinkQrScannerFallback(
                visible = echoLinkFallbackScannerVisible,
                onResult = { rawValue ->
                    val endpoint = EchoPairingParser.parse(rawValue)
                    if (endpoint != null) {
                        connectEchoLinkEndpoint(endpoint)
                    } else {
                        echoLinkFallbackScannerVisible = false
                        echoLinkScanMessage = "没有识别到 ECHO Link 配对码"
                    }
                },
                onCancel = {
                    echoLinkFallbackScannerVisible = false
                    echoLinkScanMessage = "已取消扫码"
                },
                onError = { message ->
                    echoLinkScanMessage = message
                },
            )

            val permissionEntries = remember(hasAudioPermission, hasNotifPermission) {
                buildList {
                    add(
                        PermissionEntry(
                            permission = audioPermissionName(),
                            label = "音乐存储",
                            description = "扫描并播放本地音乐文件",
                            icon = Icons.Rounded.AudioFile,
                            granted = hasAudioPermission,
                            canRequest = true,
                        ),
                    )
                    notifPermName?.let { perm ->
                        add(
                            PermissionEntry(
                                permission = perm,
                                label = "通知",
                                description = "显示媒体播放控制通知",
                                icon = Icons.Rounded.Notifications,
                                granted = hasNotifPermission,
                                canRequest = true,
                            ),
                        )
                    }
                }
            }
            EchoPermissionDialog(
                visible = showPermissionDialog,
                permissionStatuses = permissionEntries,
                onDismiss = ::dismissPermissionDialog,
                onRequestPermission = { perm ->
                    when (perm) {
                        audioPermissionName() -> permissionLauncher.launch(perm)
                        notifPermName -> notifPermissionLauncher?.launch(perm)
                    }
                },
                onOpenSettings = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = AndroidUri.fromParts("package", context.packageName, null)
                            },
                        )
                    }
                },
            )
        }
    }
}

private data class LocalHomeSearchResults(
    val tracks: List<EchoTrack> = emptyList(),
    val albums: List<AlbumSummary> = emptyList(),
    val artists: List<ArtistSummary> = emptyList(),
)

private fun LocalLibrarySearchResults.toHomeSearchResults(): LocalHomeSearchResults =
    LocalHomeSearchResults(
        tracks = tracks.map { it.toEchoTrack() },
        albums = albums,
        artists = artists,
    )

private fun LocalHomeSearchResults.toUiResults(): List<SearchResult> =
    buildList {
        tracks.forEach { track ->
            add(
                SearchResult(
                    type = SearchResultType.Track,
                    title = track.title,
                    subtitle = listOfNotNull(track.artist.takeIf { it.isNotBlank() }, track.album?.takeIf { it.isNotBlank() })
                        .joinToString(" · "),
                    id = track.id,
                    artworkUri = track.artworkUri,
                ),
            )
        }
        albums.forEach { album ->
            add(
                SearchResult(
                    type = SearchResultType.Album,
                    title = album.title,
                    subtitle = album.albumArtist ?: album.artist ?: "",
                    id = album.albumKey,
                    artworkUri = album.artworkUri,
                ),
            )
        }
        artists.forEach { artist ->
            add(
                SearchResult(
                    type = SearchResultType.Artist,
                    title = artist.name,
                    subtitle = "${artist.albumCount} 张专辑",
                    id = artist.artistKey,
                    artworkUri = artist.artworkUri,
                ),
            )
        }
    }

@Composable
private fun rememberSystemPowerSaveMode(): Boolean {
    val context = LocalContext.current
    val powerManager = remember(context) {
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    }
    var powerSaveMode by remember(powerManager) {
        mutableStateOf(powerManager?.isPowerSaveMode == true)
    }
    DisposableEffect(context, powerManager) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action == PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) {
                    powerSaveMode = powerManager?.isPowerSaveMode == true
                }
            }
        }
        val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }
    return powerSaveMode
}
