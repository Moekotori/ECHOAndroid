package app.echo.android

import app.echo.android.i18n.refreshEchoAppLocale

import app.echo.android.model.library.LibraryScanOptions
import androidx.compose.runtime.saveable.rememberSaveable
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest
import android.graphics.Color as AndroidColor
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.echo.android.data.LocalLibrarySearchResults
import app.echo.android.feature.home.SearchResult
import app.echo.android.feature.home.SearchResultType
import app.echo.android.connect.EchoPairingParser
import app.echo.android.connect.EchoLinkRequestPolicy
import app.echo.android.connect.EchoRemoteClient
import app.echo.android.model.playback.EchoLinkPlaybackUri
import app.echo.android.design.EchoArtworkRequestHeadersRegistry
import app.echo.android.design.EchoMobileTheme
import app.echo.android.design.EchoMotion
import app.echo.android.design.LocalEchoWidthSizeClass
import app.echo.android.feature.connect.ConnectScreen
import app.echo.android.feature.home.SearchScreen
import app.echo.android.feature.player.PlaybackQueueSheet
import app.echo.android.feature.settings.DiagnosticsScreen
import app.echo.android.feature.settings.ErrorLogScreen
import app.echo.android.feature.settings.SettingsScreen
import app.echo.android.model.error.EchoErrorLog
import app.echo.android.model.error.EchoErrorRecord
import app.echo.android.model.error.EchoErrorSource
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
import app.echo.android.design.echoFontFamilyForMode
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Notifications
import android.provider.Settings
import android.net.Uri as AndroidUri
import app.echo.android.R
import kotlin.math.absoluteValue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

private val LyricsDocumentMimeTypes = arrayOf("text/*", "application/xml", "application/octet-stream", "*/*")
private val ArtworkDocumentMimeTypes = arrayOf("image/*", "application/octet-stream", "*/*")
private val FontDocumentMimeTypes = arrayOf("font/*", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream", "*/*")

private enum class FontImportTarget {
    Ui,
    Lyrics,
}

@Suppress("SpellCheckingInspection")
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun EchoAppRoot(viewModel: EchoAndroidViewModel) {
    val context = LocalContext.current
    val lyricsActionError by viewModel.lyricsManagementError.collectAsStateWithLifecycle()
    LaunchedEffect(lyricsActionError) {
        lyricsActionError?.let { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show() }
    }
    val permissionActivity = remember(context) { context.findActivity() }
    val prefs = remember(context) { context.getSharedPreferences("echo_prefs", Context.MODE_PRIVATE) }
    val permission = remember { audioPermissionName() }
    var audioPermissionRequested by remember {
        mutableStateOf(prefs.getBoolean(ECHO_AUDIO_PERMISSION_REQUESTED_KEY, false))
    }
    var hasAudioPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
    }
    var pendingScanOptions by rememberSaveable { mutableStateOf(LibraryScanOptions()) }
    var scanAllAfterPermission by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasAudioPermission = granted
        if (granted && scanAllAfterPermission) viewModel.refreshLibrary(pendingScanOptions)
        scanAllAfterPermission = false
    }
    val notifPermName = remember { notificationPermissionName() }
    var notificationPermissionRequested by remember {
        mutableStateOf(prefs.getBoolean(ECHO_NOTIFICATION_PERMISSION_REQUESTED_KEY, false))
    }
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
    var showPermissionDialog by remember {
        mutableStateOf(!prefs.getBoolean(ECHO_PERMISSION_DIALOG_SHOWN_KEY, false))
    }
    fun dismissPermissionDialog() {
        showPermissionDialog = false
        prefs.edit { putBoolean(ECHO_PERMISSION_DIALOG_SHOWN_KEY, true) }
    }
    fun persistReadPermission(uri: AndroidUri, write: Boolean = false): Boolean {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            if (write) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0
        return runCatching {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        }.isSuccess || (
            write &&
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }.isSuccess
            )
    }
    val folderScanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { treeUri ->
            persistReadPermission(treeUri, write = true)
            viewModel.refreshLibraryFolder(treeUri, pendingScanOptions)
        }
    }
    val backgroundImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { selectedUri ->
            if (persistReadPermission(selectedUri)) {
                viewModel.setCustomBackground(EchoBackgroundMode.Image, selectedUri)
            } else {
                android.widget.Toast.makeText(context, R.string.background_permission_error, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
    val backgroundVideoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { selectedUri ->
            if (persistReadPermission(selectedUri)) {
                viewModel.setCustomBackground(EchoBackgroundMode.Video, selectedUri)
            } else {
                android.widget.Toast.makeText(context, R.string.background_permission_error, android.widget.Toast.LENGTH_LONG).show()
            }
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

    val pendingEmbeddedTagWrite by viewModel.pendingEmbeddedTagWrite.collectAsStateWithLifecycle()
    val embeddedTagWriteMessage by viewModel.embeddedTagWriteMessage.collectAsStateWithLifecycle()
    val mediaStoreTagWriteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.onEmbeddedTagWriteAccessResult(result.resultCode == Activity.RESULT_OK)
    }
    val storageTagWriteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onEmbeddedTagWriteAccessResult(granted)
    }
    var launchedTagWriteRequestId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(pendingEmbeddedTagWrite) {
        val pending = pendingEmbeddedTagWrite ?: return@LaunchedEffect
        if (launchedTagWriteRequestId == pending.requestId) return@LaunchedEffect
        launchedTagWriteRequestId = pending.requestId
        if (pending.needsStoragePermission) {
            val permission = writeStoragePermissionName()
            if (permission == null) {
                viewModel.onEmbeddedTagWriteAccessResult(false)
            } else {
                storageTagWriteLauncher.launch(permission)
            }
            return@LaunchedEffect
        }
        val sender = pending.intentSender ?: runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                MediaStore.createWriteRequest(
                    context.contentResolver,
                    listOf(AndroidUri.parse(pending.contentUri)),
                ).intentSender
            } else {
                null
            }
        }.getOrNull()
        if (sender == null) {
            viewModel.onEmbeddedTagWriteAccessResult(false)
        } else {
            mediaStoreTagWriteLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }
    LaunchedEffect(embeddedTagWriteMessage) {
        val message = embeddedTagWriteMessage ?: return@LaunchedEffect
        val text = when (message) {
            EmbeddedTagWriteUserMessage.Written -> context.getString(R.string.tag_write_saved_to_file)
            EmbeddedTagWriteUserMessage.IndexOnly -> context.getString(R.string.tag_write_saved_index_only)
            EmbeddedTagWriteUserMessage.UnsupportedFormat -> context.getString(R.string.tag_write_unsupported_format)
            EmbeddedTagWriteUserMessage.Failed -> context.getString(R.string.tag_write_failed)
        }
        android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show()
        viewModel.consumeEmbeddedTagWriteMessage()
    }

    val remoteClient = (context.applicationContext as EchoApplication).echoLinkSession.client
    LaunchedEffect(remoteClient) {
        viewModel.setEchoLinkPlaybackResolver { ref ->
            val trackId = EchoLinkPlaybackUri.trackId(ref.id, ref.uri) ?: return@setEchoLinkPlaybackResolver ref
            val streamUrl = remoteClient.resolvePhoneStreamUrl(trackId) ?: return@setEchoLinkPlaybackResolver ref
            ref.copy(uri = streamUrl)
        }
        viewModel.setEchoLinkLyricsFetcher { mediaId ->
            val trackId = EchoLinkPlaybackUri.trackIdFromMediaId(mediaId) ?: return@setEchoLinkLyricsFetcher null
            remoteClient.fetchLyrics(trackId)
        }
    }
    val remoteStatus by remoteClient.status.collectAsStateWithLifecycle()
    LaunchedEffect(remoteStatus.connectionState) {
        if (remoteStatus.connectionState == EchoRemoteConnectionState.Connected) {
            viewModel.notifyEchoLinkConnected()
        }
    }
    val playbackStatus by viewModel.playbackStatus.collectAsStateWithLifecycle()
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
                Lifecycle.Event.ON_RESUME -> {
                    hasAudioPermission =
                        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                    hasNotifPermission = notifPermName == null ||
                        ContextCompat.checkSelfPermission(context, notifPermName) == PackageManager.PERMISSION_GRANTED
                }
                Lifecycle.Event.ON_STOP -> appVisible = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(remoteClient, appVisible) {
        remoteClient.setForeground(appVisible)
        onDispose { remoteClient.setForeground(false) }
    }
    LaunchedEffect(effectivePerformanceMode) {
        viewModel.setEffectivePerformanceMode(effectivePerformanceMode)
    }
    fun connectEchoLinkEndpoint(endpoint: EchoRemoteEndpoint) {
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
            remoteClient.connectManual(
                address = address,
                token = token,
                refreshLibraryOnConnect = appSettings.echoLinkPreferLinkedLibrary,
            )
        }
    }

    val lastFmApiKey = appSettings.lastFmApiKey?.takeIf { it.isNotBlank() }
        ?: LastFmApiConfig.API_KEY.takeIf { it.isNotBlank() }
    val lastFmSharedSecret = appSettings.lastFmSharedSecret?.takeIf { it.isNotBlank() }
        ?: LastFmApiConfig.SHARED_SECRET.takeIf { it.isNotBlank() }
    var selectedAlbum by remember { mutableStateOf<AlbumSummary?>(null) }
    var selectedArtist by remember { mutableStateOf<ArtistSummary?>(null) }
    var selectedGenre by remember { mutableStateOf<app.echo.android.model.library.GenreSummary?>(null) }
    var selectedFolder by remember { mutableStateOf<FolderSummary?>(null) }
    var selectedPlaylist by remember { mutableStateOf<EchoPlaylist?>(null) }
    var detailReturnPage by remember { mutableStateOf<EchoPagerPage?>(null) }
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var errorLogVisible by rememberSaveable { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(EchoTab.Now.ordinal) }
    var bottomDockExpanded by remember { mutableStateOf(true) }
    var bottomDockHeightPx by remember { mutableIntStateOf(0) }
    val bottomDockInset = with(LocalDensity.current) { bottomDockHeightPx.toDp() }
    var nowPlayingExpanded by remember { mutableStateOf(false) }
    var nowPlayingBackProgress by remember { mutableFloatStateOf(0f) }
    val nowPlayingBackRecoveryJob = remember { arrayOfNulls<Job>(1) }
    // 在设置 expanded=true 的同一帧归零返回进度,避免重开首帧带着残留位移渲染
    fun expandNowPlaying() {
        nowPlayingBackRecoveryJob[0]?.cancel()
        nowPlayingBackProgress = 0f
        nowPlayingExpanded = true
    }
    var lyricsLaunchToken by remember { mutableIntStateOf(0) }
    var queueSheetVisible by remember { mutableStateOf(false) }
    val openLyricsRequest by EchoLaunchActions.openLyrics.collectAsStateWithLifecycle()
    LaunchedEffect(openLyricsRequest) {
        if (openLyricsRequest) {
            expandNowPlaying()
            lyricsLaunchToken += 1
            viewModel.setShowLyricsControlDeck(true)
            EchoLaunchActions.consumeOpenLyrics()
        }
    }
    val incomingAudioUris by EchoLaunchActions.incomingAudioUris.collectAsStateWithLifecycle()
    LaunchedEffect(incomingAudioUris) {
        if (incomingAudioUris.isNotEmpty()) {
            viewModel.playIncomingAudio(incomingAudioUris)
            expandNowPlaying()
            EchoLaunchActions.consumeIncomingAudio()
        }
    }
    val playLastRequest by EchoLaunchActions.playLast.collectAsStateWithLifecycle()
    LaunchedEffect(playLastRequest) {
        if (playLastRequest) {
            viewModel.playLastSavedSession()
            EchoLaunchActions.consumePlayLast()
        }
    }
    val openLibraryRequest by EchoLaunchActions.openLibrary.collectAsStateWithLifecycle()
    val libraryDetailOpen = selectedAlbum != null || selectedArtist != null || selectedGenre != null || selectedFolder != null || selectedPlaylist != null
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
                tabPagerState.animateScrollToPage(
                    page = targetPage,
                    animationSpec = routeMotionSpec(tabPagerState.currentPage, targetPage, effectivePerformanceMode),
                )
            }
        }
    }
    fun selectDockTab(tab: EchoTab) = navigateToPage(tab.pagerPage)
    LaunchedEffect(openLibraryRequest) {
        if (openLibraryRequest) {
            nowPlayingExpanded = false
            queueSheetVisible = false
            searchVisible = false
            selectDockTab(EchoTab.Library)
            EchoLaunchActions.consumeOpenLibrary()
        }
    }
    fun clearLibraryDetail() {
        selectedAlbum = null
        selectedArtist = null
        selectedGenre = null
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
                    tabPagerState.animateScrollToPage(
                        page = targetPage,
                        animationSpec = routeMotionSpec(tabPagerState.currentPage, targetPage, effectivePerformanceMode),
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
    LaunchedEffect(tabPagerState.isScrollInProgress, tabPagerState.currentPage) {
        if (!tabPagerState.isScrollInProgress && tabPagerState.currentPageOffsetFraction.absoluteValue > 0.001f) {
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

    LaunchedEffect(remoteStatus.connectionState, appSettings.echoLinkPreferLinkedLibrary) {
        if (
            remoteStatus.connectionState == EchoRemoteConnectionState.Connected &&
            appSettings.echoLinkPreferLinkedLibrary &&
            tabPagerState.currentPage == EchoPagerPage.Connect.ordinal
        ) {
            selectDockTab(EchoTab.Library)
        }
    }


    LaunchedEffect(remoteStatus.endpoint, remoteStatus.connectionState) {
        val endpoint = remoteStatus.endpoint
        EchoArtworkRequestHeadersRegistry.replaceEchoLinkAuthorization(
            baseUrl = endpoint?.let { "${it.scheme}://${it.host}:${it.port}" },
            token = endpoint?.token,
        )
    }

    EchoOverlayBackHandler(enabled = searchVisible) {
        searchVisible = false
        searchQuery = ""
    }
    EchoOverlayBackHandler(enabled = errorLogVisible) {
        errorLogVisible = false
    }
    EchoOverlayBackHandler(enabled = queueSheetVisible) { queueSheetVisible = false }
    EchoOverlayBackHandler(
        enabled = nowPlayingExpanded && !queueSheetVisible,
        onProgress = {
            nowPlayingBackRecoveryJob[0]?.cancel()
            nowPlayingBackProgress = it
        },
        onCancel = {
            // 取消返回手势时弹簧回弹,而非瞬间跳回原位
            nowPlayingBackRecoveryJob[0]?.cancel()
            nowPlayingBackRecoveryJob[0] = appScope.launch {
                animate(
                    initialValue = nowPlayingBackProgress,
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = 420f,
                    ),
                ) { value, _ -> nowPlayingBackProgress = value }
            }
        },
        onDismiss = { nowPlayingExpanded = false },
    )
    EchoOverlayBackHandler(enabled = !nowPlayingExpanded && libraryDetailOpen) {
        closeLibraryDetail()
    }
    EchoOverlayBackHandler(
        enabled = !nowPlayingExpanded && tabPagerState.currentPage == EchoPagerPage.Settings.ordinal,
    ) {
        selectDockTab(EchoTab.Now)
    }

    EchoMobileTheme(
        darkTheme = darkTheme,
        dynamicColor = appSettings.dynamicColorEnabled,
        playbackHapticsEnabled = appSettings.playbackHapticsEnabled,
        fontFamily = uiFontFamily,
        fontScale = appSettings.uiFontScale,
        densityScale = appSettings.uiDensityScale,
        effectivePerformanceMode = effectivePerformanceMode,
    ) {
        Box(Modifier.fillMaxSize()) {
            EchoCustomBackground(
                settings = appSettings,
                modifier = Modifier.fillMaxSize(),
                onLoadError = { failedUri ->
                    if (appSettings.customBackgroundUri == failedUri) {
                        android.widget.Toast.makeText(context, R.string.background_load_error, android.widget.Toast.LENGTH_LONG).show()
                        EchoErrorLog.record(
                            EchoErrorSource.Other,
                            context.getString(R.string.background_load_error),
                            detail = failedUri,
                        )
                        viewModel.setCustomBackground(EchoBackgroundMode.Default, null)
                    }
                },
            )
            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                HorizontalPager(
                    state = tabPagerState,
                    userScrollEnabled = !libraryDetailOpen ||
                        LocalEchoWidthSizeClass.current.prefersLibrarySplit,
                    beyondViewportPageCount = if (effectivePerformanceMode.isLightweight) 0 else 1,
                    flingBehavior = PagerDefaults.flingBehavior(
                        state = tabPagerState,
                        snapAnimationSpec = routeMotionSpec(
                            fromPage = tabPagerState.currentPage,
                            toPage = tabPagerState.currentPage,
                            effectivePerformanceMode = effectivePerformanceMode,
                        ),
                    ),
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
                                selectedGenre = selectedGenre,
                                selectedFolder = selectedFolder,
                                selectedPlaylist = selectedPlaylist,
                                onRequestPermission = { permissionLauncher.launch(permission) },
                                onScanFolder = { options ->
                                    pendingScanOptions = options
                                    folderScanLauncher.launch(null)
                                },
                                onScanAll = { options ->
                                    pendingScanOptions = options
                                    if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                                        viewModel.refreshLibrary(options)
                                    } else {
                                        scanAllAfterPermission = true
                                        permissionLauncher.launch(permission)
                                    }
                                },
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
                                    selectedGenre = null
                                    selectedFolder = null
                                    selectedPlaylist = null
                                    selectedAlbum = album
                                },
                                onOpenArtist = { artist ->
                                    detailReturnPage = EchoPagerPage.Library
                                    selectedAlbum = null
                                    selectedGenre = null
                                    selectedFolder = null
                                    selectedPlaylist = null
                                    selectedArtist = artist
                                },
                                onOpenGenre = { genre ->
                                    detailReturnPage = EchoPagerPage.Library
                                    selectedAlbum = null
                                    selectedArtist = null
                                    selectedFolder = null
                                    selectedPlaylist = null
                                    selectedGenre = genre
                                },
                                onOpenFolder = { folder ->
                                    detailReturnPage = EchoPagerPage.Library
                                    selectedAlbum = null
                                    selectedArtist = null
                                    selectedGenre = null
                                    selectedPlaylist = null
                                    selectedFolder = folder
                                },
                                onOpenPlaylist = { playlist ->
                                    detailReturnPage = EchoPagerPage.Library
                                    selectedAlbum = null
                                    selectedArtist = null
                                    selectedGenre = null
                                    selectedFolder = null
                                    selectedPlaylist = playlist
                                },
                                onCloseDetail = { closeLibraryDetail() },
                                onOpenConnect = { selectDockTab(EchoTab.Connect) },
                            )

                            EchoPagerPage.Now -> EchoHomePage(
                                bottomInset = bottomDockInset,
                                viewModel = viewModel,
                                playbackStatus = playbackStatus,
                                onOpenAlbum = { album ->
                                    detailReturnPage = EchoPagerPage.Now
                                    selectedArtist = null
                                    selectedGenre = null
                                    selectedFolder = null
                                    selectedPlaylist = null
                                    selectedAlbum = album
                                    selectDockTab(EchoTab.Library)
                                },
                                onOpenArtist = { artist ->
                                    detailReturnPage = EchoPagerPage.Now
                                    selectedAlbum = null
                                    selectedGenre = null
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
                            val lastFmState by viewModel.lastFmState.collectAsStateWithLifecycle()
                            val listenBrainzState by viewModel.listenBrainzState.collectAsStateWithLifecycle()
                            val usbExclusiveTestResult by viewModel.usbExclusiveTestResult.collectAsStateWithLifecycle()
                            val errorLogCount by viewModel.errorLogCount.collectAsStateWithLifecycle(0)
                            SettingsScreen(
                                isActive = tabPagerState.currentPage == EchoPagerPage.Settings.ordinal &&
                                    !nowPlayingExpanded && !searchVisible && !errorLogVisible && !queueSheetVisible,
                                status = playbackStatus,
                                trackCount = libraryStats.trackCount,
                                albumCount = libraryStats.albumCount,
                                artistCount = libraryStats.artistCount,
                                appVersionLabel = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                dynamicArtworkEnabled = appSettings.dynamicArtworkEnabled,
                                compactModeEnabled = appSettings.compactModeEnabled,
                                dynamicColorEnabled = appSettings.dynamicColorEnabled,
                                playbackHapticsEnabled = appSettings.playbackHapticsEnabled,
                                performanceMode = appSettings.performanceMode,
                                effectivePerformanceMode = effectivePerformanceMode.id,
                                trackAudioInfoTagsVisible = appSettings.trackAudioInfoTagsVisible,
                                pcHandoffEnabled = appSettings.pcHandoffEnabled,
                                showLyricsControlDeck = appSettings.showLyricsControlDeck,
                                onlineLyricsEnabled = appSettings.onlineLyricsEnabled,
                                usbExclusiveEnabled = appSettings.usbExclusiveEnabled,
                                usbBitPerfectEnabled = appSettings.usbBitPerfectEnabled,
                                trackTransitions = appSettings.trackTransitions,
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
                                appLanguage = appSettings.appLanguage,
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
                                listenBrainzEnabled = appSettings.listenBrainzEnabled,
                                listenBrainzToken = appSettings.listenBrainzToken,
                                listenBrainzStatusLabel = listenBrainzState.lastMessage,
                                listenBrainzErrorLabel = listenBrainzState.lastError,
                                onDynamicArtworkEnabledChange = viewModel::setDynamicArtworkEnabled,
                                onCompactModeEnabledChange = viewModel::setCompactModeEnabled,
                                onDynamicColorEnabledChange = viewModel::setDynamicColorEnabled,
                                onPlaybackHapticsEnabledChange = viewModel::setPlaybackHapticsEnabled,
                                onPerformanceModeChange = viewModel::setPerformanceMode,
                                onTrackAudioInfoTagsVisibleChange = viewModel::setTrackAudioInfoTagsVisible,
                                onPcHandoffEnabledChange = viewModel::setPcHandoffEnabled,
                                onShowLyricsControlDeckChange = viewModel::setShowLyricsControlDeck,
                                onOnlineLyricsEnabledChange = viewModel::setOnlineLyricsEnabled,
                                onUsbExclusiveEnabledChange = viewModel::setUsbExclusiveEnabled,
                                onUsbBitPerfectEnabledChange = viewModel::setUsbBitPerfectEnabled,
                                onTrackTransitionsChange = viewModel::setTrackTransitions,
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
                                onCustomBackgroundStyleChange = viewModel::setCustomBackgroundStyle,
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
                                onAppLanguageChange = { language ->
                                    viewModel.setAppLanguage(language)
                                    permissionActivity?.refreshEchoAppLocale(language)
                                },
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
                                onListenBrainzEnabledChange = viewModel::setListenBrainzEnabled,
                                onSaveListenBrainzToken = viewModel::saveListenBrainzToken,
                                onDisconnectListenBrainz = viewModel::disconnectListenBrainz,
                                notificationPermissionGranted = hasNotifPermission,
                                onRequestNotificationPermission = {
                                    val perm = notifPermName ?: return@SettingsScreen
                                    if (!hasNotifPermission) {
                                        notifPermissionLauncher?.launch(perm)
                                    }
                                },
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
                                errorLogCount = errorLogCount,
                                onOpenErrorLog = { errorLogVisible = true },
                            )
                            }

                            EchoPagerPage.Connect -> {
                            // 只有真正停留在 Connect 页才启动 LAN 发现;
                            // 邻页预组合(beyondViewportPageCount=1)不应常驻 NSD 扫描
                            val connectPageSettled =
                                appVisible && tabPagerState.settledPage == EchoPagerPage.Connect.ordinal
                            DisposableEffect(connectPageSettled) {
                                if (!connectPageSettled) {
                                    return@DisposableEffect onDispose {}
                                }
                                viewModel.startEchoLinkDiscovery()
                                onDispose { viewModel.stopEchoLinkDiscovery() }
                            }
                            val remoteScanState by viewModel.remoteScanState.collectAsStateWithLifecycle()
                            val discoveryState by viewModel.echoLinkDiscoveryState.collectAsStateWithLifecycle()
                            val echoLinkLanDevices by viewModel.echoLinkLanDevices.collectAsStateWithLifecycle()
                            ConnectScreen(
                                remoteState = remoteStatus.connectionState,
                                pcTitle = remoteStatus.endpoint?.name ?: "PC ECHO",
                                trackTitle = remoteStatus.playback.track?.title ?: context.getString(R.string.echo_link_not_connected),
                                trackArtist = remoteStatus.playback.track?.artist ?: context.getString(R.string.echo_link_tap_to_pair),
                                trackArtworkUrl = remoteStatus.playback.track?.artworkUrl,
                                isPlaying = remoteStatus.playback.state == EchoRemotePlaybackState.Playing,
                                remoteError = remoteStatus.error,
                                savedPcAddress = appSettings.echoLinkPcAddress,
                                savedPcToken = appSettings.echoLinkPcToken,
                                autoReconnectEnabled = appSettings.echoLinkAutoReconnectEnabled,
                                linkedLibraryDefault = appSettings.echoLinkPreferLinkedLibrary,
                                positionMs = remoteStatus.playback.positionMs,
                                durationMs = remoteStatus.playback.durationMs,
                                volume = remoteStatus.playback.volume,
                                queueTitles = remoteStatus.playback.queue.items.map { it.title },
                                subsonicServerUrl = appSettings.subsonicServerUrl,
                                subsonicUsername = appSettings.subsonicUsername,
                                subsonicPassword = appSettings.subsonicPassword,
                                webDavServerUrl = appSettings.webDavServerUrl,
                                webDavUsername = appSettings.webDavUsername,
                                webDavPassword = appSettings.webDavPassword,
                                jellyfinServerUrl = appSettings.jellyfinServerUrl,
                                jellyfinUsername = appSettings.jellyfinUsername,
                                jellyfinPassword = appSettings.jellyfinPassword,
                                savedPcs = appSettings.echoLinkSavedPcs,
                                remoteScanState = remoteScanState,
                                onConnectPc = ::connectEchoLinkAddress,
                                onPlayPause = { remoteClient.send(EchoRemoteCommand.PlayPause) },
                                onPrevious = { remoteClient.send(EchoRemoteCommand.Previous) },
                                onNext = { remoteClient.send(EchoRemoteCommand.Next) },
                                onSeek = { positionMs -> remoteClient.send(EchoRemoteCommand.SeekTo(positionMs)) },
                                onVolume = { volume -> remoteClient.send(EchoRemoteCommand.SetVolume(volume)) },
                                onHandoffPhoneToPc = if (appSettings.pcHandoffEnabled &&
                                    playbackStatus.track?.id?.let(EchoLinkPlaybackUri::trackIdFromMediaId) != null) {
                                    {
                                        val phone = viewModel.playbackStatus.value.track
                                        val id = phone?.id?.let(EchoLinkPlaybackUri::trackIdFromMediaId)
                                        val queue = viewModel.playbackQueue.value
                                        val linkedQueue = queue.items.mapNotNull { item ->
                                            val trackId = EchoLinkPlaybackUri.trackIdFromMediaId(item.id)
                                                ?: return@mapNotNull null
                                            app.echo.android.model.connect.EchoRemoteTrack(
                                                id = trackId,
                                                title = item.title,
                                                artist = item.artist,
                                                album = item.album,
                                                artworkUrl = item.artworkUri,
                                                durationMs = item.durationMs,
                                            )
                                        }
                                        if (phone != null && id != null) {
                                            val startIndex = linkedQueue.indexOfFirst { it.id == id }.coerceAtLeast(0)
                                            remoteClient.handoffPhoneQueueToPc(
                                                tracks = linkedQueue.ifEmpty {
                                                    listOf(
                                                        app.echo.android.model.connect.EchoRemoteTrack(
                                                            id = id, title = phone.title, artist = phone.artist,
                                                            album = phone.album, artworkUrl = phone.artworkUri,
                                                            durationMs = phone.durationMs,
                                                        ),
                                                    )
                                                },
                                                startIndex = startIndex,
                                                positionMs = viewModel.playbackPosition.value.positionMs,
                                            ) {
                                                val live = viewModel.playbackStatus.value
                                                if (live.track?.id == phone.id && live.isPlaying) viewModel.pause()
                                            }
                                        }
                                    }
                                } else null,
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
                                onSyncJellyfinLibrary = viewModel::syncJellyfinLibrary,
                                onSaveJellyfinCredentials = { url, user, pass ->
                                    viewModel.saveJellyfinCredentials(url, user, pass)
                                },
                                onClearJellyfinCredentials = viewModel::clearJellyfinCredentials,
                                onForgetSavedPc = { pc ->
                                    val current = appSettings.echoLinkPcAddress
                                        ?.trim()?.trimEnd('/')
                                    if (pc.id == current?.lowercase()) {
                                        remoteClient.disconnect()
                                    }
                                    viewModel.forgetSavedEchoLinkPc(pc.address)
                                },
                                onCancelRemoteSync = viewModel::cancelRemoteSync,
                                discoveredLanDevices = echoLinkLanDevices,
                                discoveryState = discoveryState,
                                onRefreshLanDevices = viewModel::refreshEchoLinkDiscovery,
                            )
                            }

                            EchoPagerPage.Diagnostics -> {
                                val equalizerState by viewModel.equalizerState.collectAsStateWithLifecycle()
                                val opraState by viewModel.opraState.collectAsStateWithLifecycle()
                                DiagnosticsScreen(
                                    status = playbackStatus,
                                    positionFlow = viewModel.playbackPosition,
                                    equalizerState = equalizerState,
                                    opraState = opraState,
                                    onEqualizerEnabledChange = viewModel::setEqualizerEnabled,
                                    onEqualizerPresetSelected = viewModel::setEqualizerPreset,
                                    onEqualizerBandGainChange = viewModel::setEqualizerBandGain,
                                    onEqualizerReset = viewModel::resetEqualizer,
                                    onEqualizerPreampChange = viewModel::setEqualizerPreamp,
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
                    onExpand = { expandNowPlaying() },
                    onOpenQueue = { queueSheetVisible = true },
                    onNext = viewModel::skipNext,
                    onPrevious = viewModel::skipPrevious,
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .onSizeChanged { bottomDockHeightPx = it.height },
                )
            }

            AnimatedVisibility(
                visible = nowPlayingExpanded,
                enter = if (effectivePerformanceMode.isLightweight) {
                    fadeIn(tween(durationMillis = motionDuration(90, effectivePerformanceMode)))
                } else {
                    EchoMotion.nowPlayingEnter(
                        enterMs = motionDuration(520, effectivePerformanceMode),
                        fadeMs = motionDuration(260, effectivePerformanceMode),
                    )
                },
                exit = if (effectivePerformanceMode.isLightweight) {
                    fadeOut(tween(durationMillis = motionDuration(90, effectivePerformanceMode)))
                } else {
                    EchoMotion.nowPlayingExit(
                        exitMs = motionDuration(380, effectivePerformanceMode),
                        fadeMs = motionDuration(200, effectivePerformanceMode),
                    )
                },
            ) {
                EchoNowPlayingHost(
                    viewModel = viewModel,
                    playbackStatus = playbackStatus,
                    appSettings = appSettings,
                    lyricsFontFamily = lyricsFontFamily,
                    onDismiss = { nowPlayingExpanded = false },
                    predictiveBackProgress = { nowPlayingBackProgress },
                    onOpenQueue = { queueSheetVisible = true },
                    onImportLyrics = {
                        lyricsImportTrackId = playbackStatus.track?.id
                        lyricsImportLauncher.launch(LyricsDocumentMimeTypes)
                    },
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
                    openLyricsRequestId = lyricsLaunchToken,
                )
            }
            // 队列 sheet 关闭时不收集队列流,避免曲目切换/队列变更触发根作用域重组;
            // 关闭后保留最后一次快照,退出动画期间内容不跳变
            val playbackQueue by produceState(
                initialValue = viewModel.playbackQueue.value,
                key1 = queueSheetVisible,
            ) {
                if (queueSheetVisible) {
                    viewModel.playbackQueue.collect { value = it }
                }
            }
            PlaybackQueueSheet(
                    visible = queueSheetVisible,
                    status = playbackStatus,
                    queueState = playbackQueue,
                    onDismiss = { queueSheetVisible = false },
                    onPlayItem = viewModel::playQueueItem,
                    onRemoveItem = viewModel::removeQueueItem,
                    onMoveItem = viewModel::moveQueueItem,
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
            AnimatedVisibility(
                visible = searchVisible,
                enter = if (effectivePerformanceMode.isLightweight) {
                    fadeIn(tween(durationMillis = motionDuration(90, effectivePerformanceMode)))
                } else {
                    EchoMotion.overlayEnter(
                        enterMs = motionDuration(EchoMotion.OverlayMs, effectivePerformanceMode),
                        fadeMs = motionDuration(EchoMotion.OverlayFadeMs, effectivePerformanceMode),
                    )
                },
                exit = if (effectivePerformanceMode.isLightweight) {
                    fadeOut(tween(durationMillis = motionDuration(90, effectivePerformanceMode)))
                } else {
                    EchoMotion.overlayExit(
                        exitMs = motionDuration(EchoMotion.OverlayExitMs, effectivePerformanceMode),
                    )
                },
            ) {
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
                val searchResults = remember(localSearchResults) { localSearchResults.toUiResults(context) }
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
                    onPlayNext = { result ->
                        if (result.type == SearchResultType.Track) {
                            viewModel.playNextByTrackId(result.id)
                        }
                    },
                    onEnqueue = { result ->
                        if (result.type == SearchResultType.Track) {
                            viewModel.enqueueByTrackId(result.id)
                        }
                    },
                    onBack = {
                        searchVisible = false
                        searchQuery = ""
                    },
                )
            }
            AnimatedVisibility(
                visible = errorLogVisible,
                enter = if (effectivePerformanceMode.isLightweight) {
                    fadeIn(tween(durationMillis = motionDuration(90, effectivePerformanceMode)))
                } else {
                    EchoMotion.overlayEnter(
                        enterMs = motionDuration(EchoMotion.OverlayMs, effectivePerformanceMode),
                        fadeMs = motionDuration(EchoMotion.OverlayFadeMs, effectivePerformanceMode),
                    )
                },
                exit = if (effectivePerformanceMode.isLightweight) {
                    fadeOut(tween(durationMillis = motionDuration(90, effectivePerformanceMode)))
                } else {
                    EchoMotion.overlayExit(
                        exitMs = motionDuration(EchoMotion.OverlayExitMs, effectivePerformanceMode),
                    )
                },
            ) {
                val errorLogRecords by produceState(
                    initialValue = emptyList<EchoErrorRecord>(),
                    key1 = errorLogVisible,
                ) {
                    if (errorLogVisible) {
                        viewModel.errorLogRecords.collect { value = it }
                    }
                }
                ErrorLogScreen(
                    records = errorLogRecords,
                    onClear = viewModel::clearErrorLog,
                    onDelete = viewModel::deleteErrorLog,
                    onBack = { errorLogVisible = false },
                )
            }
            val permissionEntries = remember(
                permissionActivity,
                audioPermissionRequested,
                hasAudioPermission,
                hasNotifPermission,
                notificationPermissionRequested,
            ) {
                buildList {
                    add(
                        PermissionEntry(
                            permission = audioPermissionName(),
                            label = context.getString(R.string.permission_audio_label),
                            description = context.getString(R.string.permission_audio_description),
                            icon = Icons.Rounded.AudioFile,
                            granted = hasAudioPermission,
                            canRequest = !audioPermissionRequested ||
                                permissionActivity?.let {
                                    ActivityCompat.shouldShowRequestPermissionRationale(it, audioPermissionName())
                                } == true,
                        ),
                    )
                    notifPermName?.let { perm ->
                        add(
                            PermissionEntry(
                                permission = perm,
                                label = context.getString(R.string.permission_notification_label),
                                description = context.getString(R.string.permission_notification_description),
                                icon = Icons.Rounded.Notifications,
                                granted = hasNotifPermission,
                                canRequest = !notificationPermissionRequested ||
                                    permissionActivity?.let {
                                        ActivityCompat.shouldShowRequestPermissionRationale(it, perm)
                                    } == true,
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
                        audioPermissionName() -> {
                            audioPermissionRequested = true
                            prefs.edit { putBoolean(ECHO_AUDIO_PERMISSION_REQUESTED_KEY, true) }
                            permissionLauncher.launch(perm)
                        }

                        notifPermName -> {
                            notificationPermissionRequested = true
                            prefs.edit { putBoolean(ECHO_NOTIFICATION_PERMISSION_REQUESTED_KEY, true) }
                            notifPermissionLauncher?.launch(perm)
                        }
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

private fun LocalHomeSearchResults.toUiResults(resources: Context): List<SearchResult> =
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
                    subtitle = resources.getString(R.string.artist_album_count, artist.albumCount),
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

@Composable
private fun EchoOverlayBackHandler(
    enabled: Boolean,
    onProgress: (Float) -> Unit = {},
    onCancel: () -> Unit = { onProgress(0f) },
    onDismiss: () -> Unit,
) {
    PredictiveBackHandler(enabled = enabled) { progress ->
        try {
            progress.collect { backEvent ->
                onProgress(backEvent.progress)
            }
            onDismiss()
        } catch (_: CancellationException) {
            onCancel()
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}

private const val ECHO_AUDIO_PERMISSION_REQUESTED_KEY = "echo_audio_permission_requested_v1"
private const val ECHO_NOTIFICATION_PERMISSION_REQUESTED_KEY = "echo_notification_permission_requested_v1"
