package app.echo.android

import app.echo.android.i18n.refreshEchoAppLocale
import app.echo.android.feature.listening.ListeningScreen
import app.echo.android.feature.home.ListeningStatsScreen
import app.echo.android.model.playback.EchoPlaybackState
import app.echo.android.plugin.EchoPluginsOverlay
import androidx.compose.ui.res.stringResource

import app.echo.android.model.library.LibraryScanOptions
import app.echo.android.model.playback.EchoOutputDeviceKind
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
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
import app.echo.android.connect.EchoDlnaException
import app.echo.android.connect.EchoPairingParser
import app.echo.android.connect.EchoLinkCastFormat
import app.echo.android.connect.EchoLinkCastPlan
import app.echo.android.connect.EchoLinkCastPolicy
import app.echo.android.connect.EchoLinkCastSourceTrack
import app.echo.android.connect.EchoLinkDiscoveryPolicy
import app.echo.android.connect.EchoLinkRequestPolicy
import app.echo.android.model.connect.EchoLanRenderer
import app.echo.android.model.connect.EchoLanRendererKind
import app.echo.android.model.playback.EchoLinkPlaybackUri
import app.echo.android.model.playback.EchoPlaybackDiagnostics
import app.echo.android.model.playback.EchoTrackRef
import app.echo.android.design.EchoArtworkRequestHeadersRegistry
import app.echo.android.design.EchoMobileTheme
import app.echo.android.design.echoStartupWindowColor
import app.echo.android.model.settings.EchoColorTheme
import app.echo.android.design.EchoPageOverlay
import app.echo.android.design.echoPageUnderlay
import app.echo.android.design.LocalEchoWidthSizeClass
import app.echo.android.design.echoLocaleSwitchLayer
import app.echo.android.design.runEchoLocaleSwitch
import app.echo.android.feature.connect.ConnectScreen
import app.echo.android.feature.home.SearchScreen
import app.echo.android.feature.player.LockLyricsScene
import app.echo.android.feature.player.PlaybackQueueSheet
import app.echo.android.ui.playback.EchoCastSheetHost
import app.echo.android.lock.EchoLockLyricsPolicy
import app.echo.android.lock.isEchoKeyguardLocked
import app.echo.android.feature.settings.DiagnosticsScreen
import app.echo.android.feature.settings.ErrorLogScreen
import app.echo.android.feature.settings.SettingsScreen
import app.echo.android.model.error.EchoErrorLog
import app.echo.android.model.error.EchoErrorRecord
import app.echo.android.model.error.EchoErrorSource
import app.echo.android.ui.home.EchoHomePage
import app.echo.android.ui.library.EchoLibraryPage
import app.echo.android.playback.EchoPlaybackProcessRuntime
import app.echo.android.ui.playback.EchoNowPlayingHost
import app.echo.android.ui.shell.echoPlayerDepth
import app.echo.android.ui.shell.echoSheetDepth
import app.echo.android.design.EchoPlayerTransitionRoot
import app.echo.android.design.EchoExpandedPlayer
import app.echo.android.design.rememberEchoBackProgress
import app.echo.android.ui.shell.EchoOverlayBackHandler
import app.echo.android.ui.shell.EchoBottomDockHost
import app.echo.android.ui.shell.EchoAdaptiveShell
import app.echo.android.ui.shell.EchoPagerPage
import app.echo.android.ui.shell.dockTab
import app.echo.android.ui.shell.pagerPage
import app.echo.android.design.rememberSilkPagerFlingBehavior
import app.echo.android.ui.shell.outerPagerUserScrollEnabled
import app.echo.android.ui.shell.rememberHomeSafePagerNestedScroll
import app.echo.android.ui.shell.routeMotionSpec
import app.echo.android.ui.shell.dockNavigationMotionSpec
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

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
    val updater: EchoUpdateViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    EchoUpdateHost(updater)
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
    var hasOverlayPermission by remember {
        mutableStateOf(android.provider.Settings.canDrawOverlays(context))
    }
    val bluetoothPermName = remember { bluetoothConnectPermissionName() }
    var hasBluetoothConnectPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, bluetoothPermName) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasBluetoothConnectPermission = granted
        if (granted) viewModel.refreshOutputRoute()
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
    val startupBackgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { selectedUri ->
            if (persistReadPermission(selectedUri)) {
                viewModel.setStartupBackground(selectedUri)
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
    val backupExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        uri?.let(viewModel::exportBackup)
    }
    val backupImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(viewModel::importBackup)
    }
    val migrationPreview by viewModel.libraryMigration.preview.collectAsStateWithLifecycle()
    val migrationBusy by viewModel.libraryMigration.busy.collectAsStateWithLifecycle()
    migrationPreview?.let { preview -> app.echo.android.feature.settings.BackupMigrationPreview(preview, migrationBusy,
        onApply = viewModel::applyMigrationBackup, onDismiss = viewModel.libraryMigration::dismiss) }
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

    val echoLinkSession = (context.applicationContext as EchoApplication).echoLinkSession
    val remoteClient = echoLinkSession.client
    val echoLinkPlaybackRouter = echoLinkSession.playbackRouter
    val remoteMode by echoLinkPlaybackRouter.remoteMode.collectAsStateWithLifecycle()
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
    val playbackStatus by viewModel.playbackStatus.collectAsStateWithLifecycle()
    LaunchedEffect(
        playbackStatus.diagnostics.outputDeviceKind,
        playbackStatus.diagnostics.outputDeviceName,
        playbackStatus.diagnostics.usbDeviceName,
        playbackStatus.diagnostics.usbExclusiveStreaming,
    ) {
        viewModel.applyOutputDspIfNeeded(
            playbackStatus.diagnostics.outputDeviceKind,
            playbackStatus.diagnostics.usbDeviceName ?: playbackStatus.diagnostics.outputDeviceName,
        )
    }
    val playbackQueue by viewModel.playbackQueue.collectAsStateWithLifecycle()
    var castingToPc by remember { mutableStateOf(false) }
    var pendingCast by remember { mutableStateOf<Pair<String, String>?>(null) }
    var sendingCastAddress by remember { mutableStateOf<String?>(null) }
    var openCastTabNonce by remember { mutableIntStateOf(0) }
    var openPcTabNonce by remember { mutableIntStateOf(0) }
    var openPcQueueNonce by remember { mutableIntStateOf(0) }
    var castSetupError by remember { mutableStateOf<String?>(null) }
    val castSessionActive by echoLinkSession.castActive.collectAsStateWithLifecycle()
    val castSessionName by echoLinkSession.castTargetName.collectAsStateWithLifecycle()
    val dlnaRenderer by echoLinkSession.dlnaRenderer.collectAsStateWithLifecycle()
    val lanCast by echoLinkSession.castPlayback.collectAsStateWithLifecycle()
    // 投送时播放页和迷你播放器显示远端设备的曲目与播放状态；进度走单独的 flow。
    val shellPlaybackStatus = remember(playbackStatus, lanCast) {
        lanCast?.let { playbackStatus.withLanCast(it, echoLinkSession.castPosition.value.positionMs) } ?: playbackStatus
    }
    val shellPositionFlow = if (lanCast != null) echoLinkSession.castPosition else viewModel.playbackPosition
    fun routedPlayPause() {
        if (dlnaRenderer != null) echoLinkSession.dlnaPlayPause() else viewModel.playPause()
    }
    fun routedSkipNext() {
        if (dlnaRenderer != null) echoLinkSession.dlnaSkip(1) else viewModel.skipNext()
    }
    fun routedSkipPrevious() {
        if (dlnaRenderer != null) echoLinkSession.dlnaSkip(-1) else viewModel.skipPrevious()
    }
    fun routedSeek(positionMs: Long) {
        if (dlnaRenderer != null) echoLinkSession.dlnaSeek(positionMs) else viewModel.seekTo(positionMs)
    }
    LaunchedEffect(remoteStatus.connectionState) {
        if (remoteStatus.connectionState == EchoRemoteConnectionState.Connected) {
            viewModel.notifyEchoLinkConnected()
        } else if (remoteStatus.connectionState == EchoRemoteConnectionState.Disconnected && dlnaRenderer == null) {
            sendingCastAddress = null
        }
    }
    val phoneCastPlan = remember(playbackQueue) {
        EchoLinkCastPolicy.plan(
            tracks = playbackQueue.items.map { it.toCastSource() },
            startIndex = playbackQueue.currentIndex,
        )
    }
    val phoneCastFormatLabel = remember(playbackStatus.track, playbackStatus.diagnostics) {
        val track = playbackStatus.track ?: return@remember null
        EchoLinkCastFormat.formatLabel(
            EchoLinkCastFormat.fromTrack(
                uri = track.uri,
                sampleRateHz = playbackStatus.diagnostics.sampleRateHz ?: track.sampleRateHz,
                bitDepth = playbackStatus.diagnostics.bitDepth,
                channelCount = playbackStatus.diagnostics.channelCount,
                codec = playbackStatus.diagnostics.codec,
            ),
        )
    }
    val phoneCastLossless = remember(playbackStatus.track, playbackStatus.diagnostics) {
        val track = playbackStatus.track ?: return@remember false
        EchoLinkCastFormat.fromTrack(
            uri = track.uri,
            sampleRateHz = playbackStatus.diagnostics.sampleRateHz ?: track.sampleRateHz,
            bitDepth = playbackStatus.diagnostics.bitDepth,
            channelCount = playbackStatus.diagnostics.channelCount,
            codec = playbackStatus.diagnostics.codec,
        ).lossless == true
    }
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle(viewModel.initialAppSettings)
    val systemPowerSaveMode = rememberSystemPowerSaveMode()
    val effectivePerformanceMode = remember(appSettings.performanceMode, systemPowerSaveMode) {
        EchoPerformanceMode.fromId(appSettings.performanceMode).resolve(systemPowerSaveMode)
    }
    val lyricSnapshot by EchoPlaybackProcessRuntime.lyricDisplaySnapshot.collectAsStateWithLifecycle()
    var screenInteractive by remember {
        mutableStateOf(context.getSystemService(PowerManager::class.java)?.isInteractive != false)
    }
    var keyguardLocked by remember { mutableStateOf(context.isEchoKeyguardLocked()) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> screenInteractive = false
                    Intent.ACTION_SCREEN_ON -> {
                        screenInteractive = true
                        keyguardLocked = context.isEchoKeyguardLocked()
                    }
                    Intent.ACTION_USER_PRESENT -> keyguardLocked = false
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    var appVisible by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    appVisible = true
                    viewModel.onLibraryForeground()
                }
                Lifecycle.Event.ON_RESUME -> {
                    hasAudioPermission =
                        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                    hasNotifPermission = notifPermName == null ||
                        ContextCompat.checkSelfPermission(context, notifPermName) == PackageManager.PERMISSION_GRANTED
                    hasOverlayPermission = android.provider.Settings.canDrawOverlays(context)
                }
                Lifecycle.Event.ON_STOP -> appVisible = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            viewModel.onLibraryForeground()
        }
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

    fun finishPhoneCast(pauseTrackId: String?) {
        castingToPc = false
        echoLinkSession.markCastStarted(remoteClient.status.value.endpoint?.name)
        if (pauseTrackId != null) {
            val live = viewModel.playbackStatus.value
            if (live.track?.id == pauseTrackId && live.isPlaying) viewModel.pause()
        }
    }

    fun stopPhoneCast() {
        castingToPc = false
        pendingCast = null
        sendingCastAddress = null
        echoLinkSession.stopCastPlayback()
    }

    fun dlnaErrorMessage(error: Throwable): String {
        val code = (error as? EchoDlnaException)?.message
        val res = when (code) {
            "unsupported_format" -> R.string.echo_link_cast_tv_unsupported_format
            "dsd_not_supported" -> R.string.echo_link_cast_tv_dsd
            "no_avtransport" -> R.string.echo_link_cast_tv_no_control
            "chromecast_unsupported" -> R.string.echo_link_cast_chromecast_unsupported
            "no_lan" -> R.string.echo_link_cast_no_lan
            else -> R.string.echo_link_cast_tv_failed
        }
        return context.getString(res)
    }

    fun performDlnaCast(renderer: EchoLanRenderer) {
        if (castingToPc) return
        sendingCastAddress = renderer.host
        castSetupError = null
        val queue = viewModel.playbackQueue.value
        val phoneId = viewModel.playbackStatus.value.track?.id
        val positionMs = viewModel.playbackPosition.value.positionMs
        when (
            val plan = EchoLinkCastPolicy.plan(
                tracks = queue.items.mapIndexed { index, item ->
                    item.toCastSource(
                        diagnostics = viewModel.playbackStatus.value.diagnostics.takeIf {
                            index == queue.currentIndex
                        },
                    )
                },
                startIndex = queue.currentIndex,
            )
        ) {
            is EchoLinkCastPlan.Blocked -> return
            is EchoLinkCastPlan.HandoffPcLibrary -> {
                sendingCastAddress = null
                castSetupError = context.getString(R.string.echo_link_cast_pc_library_not_tv)
            }
            is EchoLinkCastPlan.LocalHttp -> {
                castingToPc = true
                val onFailure: (Throwable) -> Unit = { error ->
                    castingToPc = false
                    sendingCastAddress = null
                    castSetupError = dlnaErrorMessage(error)
                }
                val onSuccess: () -> Unit = {
                    castingToPc = false
                    if (phoneId != null) {
                        val live = viewModel.playbackStatus.value
                        if (live.track?.id == phoneId && live.isPlaying) viewModel.pause()
                    }
                }
                if (renderer.kind == EchoLanRendererKind.Chromecast) {
                    echoLinkSession.startChromecastCast(
                        renderer = renderer,
                        tracks = plan.tracks,
                        startIndex = plan.startIndex,
                        positionMs = positionMs,
                        onFailure = onFailure,
                        onSuccess = onSuccess,
                    )
                } else {
                    echoLinkSession.startDlnaCast(
                        renderer = renderer,
                        tracks = plan.tracks,
                        startIndex = plan.startIndex,
                        positionMs = positionMs,
                        onFailure = onFailure,
                        onSuccess = onSuccess,
                    )
                }
            }
        }
    }

    fun performPhoneCast() {
        if (castingToPc) return
        if (sendingCastAddress == null) {
            sendingCastAddress = remoteClient.status.value.endpoint?.let { endpoint ->
                EchoLinkCastPolicy.advertisedBaseUrl(endpoint.host, endpoint.port)
                    .removePrefix("http://")
                    .removePrefix("https://")
            }
        }
        val queue = viewModel.playbackQueue.value
        val phoneId = viewModel.playbackStatus.value.track?.id
        val positionMs = viewModel.playbackPosition.value.positionMs
        when (
            val plan = EchoLinkCastPolicy.plan(
                tracks = queue.items.mapIndexed { index, item ->
                    item.toCastSource(
                        diagnostics = viewModel.playbackStatus.value.diagnostics.takeIf {
                            index == queue.currentIndex
                        },
                    )
                },
                startIndex = queue.currentIndex,
            )
        ) {
            is EchoLinkCastPlan.Blocked -> return
            is EchoLinkCastPlan.HandoffPcLibrary -> {
                echoLinkSession.stopLocalCast()
                castingToPc = true
                remoteClient.handoffPhoneQueueToPc(
                    tracks = plan.tracks,
                    startIndex = plan.startIndex,
                    positionMs = positionMs,
                    onFailure = { castingToPc = false },
                    onSuccess = { finishPhoneCast(phoneId) },
                )
            }
            is EchoLinkCastPlan.LocalHttp -> {
                castingToPc = true
                val items = echoLinkSession.publishLocalCast(plan.tracks)
                if (items == null) {
                    castingToPc = false
                    echoLinkSession.stopLocalCast()
                    castSetupError = context.getString(R.string.echo_link_cast_no_lan)
                    return
                }
                castSetupError = null
                remoteClient.castRemoteQueueToPc(
                    items = items,
                    startIndex = plan.startIndex,
                    positionMs = positionMs,
                    onFailure = {
                        castingToPc = false
                        echoLinkSession.stopLocalCast()
                    },
                    onSuccess = { finishPhoneCast(phoneId) },
                )
            }
        }
    }

    fun requestPhoneCast(address: String, token: String) {
        sendingCastAddress = address
        castSetupError = null
        val endpoint = remoteClient.status.value.endpoint
        val parsed = EchoLinkDiscoveryPolicy.parseLanHostPort(address)
        val alreadyConnected = remoteClient.status.value.connectionState == EchoRemoteConnectionState.Connected &&
            endpoint != null &&
            parsed != null &&
            parsed.first.equals(endpoint.host, ignoreCase = true) &&
            parsed.second == endpoint.port
        if (alreadyConnected) {
            performPhoneCast()
        } else {
            pendingCast = address to token
            connectEchoLinkAddress(address, token)
        }
    }

    LaunchedEffect(remoteStatus.connectionState, remoteStatus.endpoint?.host, remoteStatus.endpoint?.port, pendingCast) {
        val pending = pendingCast ?: return@LaunchedEffect
        when (remoteStatus.connectionState) {
            EchoRemoteConnectionState.Connected -> {
                val endpoint = remoteStatus.endpoint ?: return@LaunchedEffect
                val parsed = EchoLinkDiscoveryPolicy.parseLanHostPort(pending.first) ?: return@LaunchedEffect
                if (parsed.first.equals(endpoint.host, ignoreCase = true) && parsed.second == endpoint.port) {
                    pendingCast = null
                    performPhoneCast()
                }
            }
            EchoRemoteConnectionState.Error -> pendingCast = null
            else -> Unit
        }
    }

    val lastFmApiKey = appSettings.lastFmApiKey?.takeIf { it.isNotBlank() }
        ?: LastFmApiConfig.API_KEY.takeIf { it.isNotBlank() }
    val lastFmSharedSecret = appSettings.lastFmSharedSecret?.takeIf { it.isNotBlank() }
        ?: LastFmApiConfig.SHARED_SECRET.takeIf { it.isNotBlank() }
    var selectedAlbum by rememberSaveable(stateSaver = AlbumNavigationSaver) { mutableStateOf<AlbumSummary?>(null) }
    var selectedArtist by rememberSaveable(stateSaver = ArtistNavigationSaver) { mutableStateOf<ArtistSummary?>(null) }
    var selectedGenre by rememberSaveable(stateSaver = GenreNavigationSaver) { mutableStateOf<app.echo.android.model.library.GenreSummary?>(null) }
    var selectedFolder by rememberSaveable(stateSaver = FolderNavigationSaver) { mutableStateOf<FolderSummary?>(null) }
    var selectedPlaylist by rememberSaveable(stateSaver = PlaylistNavigationSaver) { mutableStateOf<EchoPlaylist?>(null) }
    var detailReturnPage by rememberSaveable { mutableStateOf<EchoPagerPage?>(null) }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var listeningVisible by rememberSaveable { mutableStateOf(false) }
    var listeningStatsVisible by rememberSaveable { mutableStateOf(false) }
    var playbackHistoryVisible by rememberSaveable { mutableStateOf(false) }
    var addMusicVisible by rememberSaveable { mutableStateOf(false) }
    var listeningDraft by rememberSaveable { mutableStateOf("") }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var errorLogVisible by rememberSaveable { mutableStateOf(false) }
    var pluginsVisible by rememberSaveable { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableIntStateOf(EchoTab.Now.ordinal) }
    var bottomDockExpanded by rememberSaveable { mutableStateOf(true) }
    var bottomDockHeightPx by remember { mutableIntStateOf(0) }
    val bottomDockInset = with(LocalDensity.current) { bottomDockHeightPx.toDp() }
    var nowPlayingExpanded by rememberSaveable { mutableStateOf(false) }
    var nowPlayingDragProgress by remember { mutableFloatStateOf(0f) }
    val nowPlayingBack = rememberEchoBackProgress(effectivePerformanceMode.isLightweight)
    fun expandNowPlaying() {
        nowPlayingBack.restore()
        nowPlayingExpanded = true
    }
    var lyricsLaunchToken by rememberSaveable { mutableIntStateOf(0) }
    var queueSheetVisible by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(remoteMode) {
        if (remoteMode) {
            nowPlayingExpanded = false
            queueSheetVisible = false
        }
    }
    var castSheetVisible by rememberSaveable { mutableStateOf(false) }
    var queueDragProgress by remember { mutableFloatStateOf(0f) }
    val queueBack = rememberEchoBackProgress(effectivePerformanceMode.isLightweight)
    LaunchedEffect(queueSheetVisible) {
        if (queueSheetVisible) queueBack.restore()
    }
    val openLyricsRequest by EchoLaunchActions.openLyrics.collectAsStateWithLifecycle()
    LaunchedEffect(openLyricsRequest) {
        if (openLyricsRequest) {
            expandNowPlaying()
            lyricsLaunchToken += 1
            viewModel.setShowLyricsControlDeck(true)
            EchoLaunchActions.consumeOpenLyrics()
        }
    }
    val listening = (context.applicationContext as EchoApplication).listening
    val listeningState by listening.controller.state.collectAsStateWithLifecycle()
    DisposableEffect(listening, viewModel) {
        listening.outputAllowed = { !viewModel.usbExclusiveOutputActive() }
        onDispose { listening.outputAllowed = { true } }
    }
    var roomHeard by remember { mutableStateOf(false) }
    var localHeard by remember { mutableStateOf(false) }
    LaunchedEffect(listeningState.audio, listeningState.yieldedToLocal, playbackStatus.state) {
        val hearingRoom = !listeningState.yieldedToLocal &&
            (listeningState.audio == app.echo.android.model.listening.EchoListeningAudio.Buffering ||
                listeningState.audio == app.echo.android.model.listening.EchoListeningAudio.Receiving)
        val local = playbackStatus.state == EchoPlaybackState.Playing ||
            playbackStatus.state == EchoPlaybackState.Loading ||
            playbackStatus.state == EchoPlaybackState.Buffering
        when {
            hearingRoom && local && !roomHeard && localHeard -> viewModel.pause()
            hearingRoom && local && roomHeard && !localHeard -> listening.controller.noteLocalPlayback(true)
            hearingRoom && local -> Unit
            else -> listening.controller.noteLocalPlayback(local)
        }
        roomHeard = hearingRoom
        localHeard = local
    }
    val listeningInvite by EchoLaunchActions.listeningInvite.collectAsStateWithLifecycle()
    LaunchedEffect(listeningInvite) {
        val code = listeningInvite ?: return@LaunchedEffect
        EchoLaunchActions.consumeListeningInvite()
        listeningDraft = code
        listeningVisible = true
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
    val openCastRequest by EchoLaunchActions.openCast.collectAsStateWithLifecycle()
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

    LaunchedEffect(darkTheme, appSettings.colorTheme, appSettings.customColors, effectivePerformanceMode.prefersHighRefreshRate) {
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
        activity?.window?.decorView?.setBackgroundColor(
            echoStartupWindowColor(
                EchoColorTheme.fromId(appSettings.colorTheme),
                darkTheme,
                appSettings.customColors,
            ),
        )
    }

    // 四个主页面横向滑动切换，与底部 dock 双向联动
    val tabPagerState = rememberPagerState(
        initialPage = EchoPagerPage.Now.ordinal,
        pageCount = { EchoPagerPage.entries.size },
    )
    val appScope = rememberCoroutineScope()
    val routeNavigationJob = remember { arrayOfNulls<Job>(1) }
    val localeSwitchProgress = remember { Animatable(1f) }
    var pendingAppLanguage by remember { mutableStateOf<String?>(null) }
    val localeSwitchLayer = pendingAppLanguage != null
    fun changeAppLanguage(language: String) {
        if (language == appSettings.appLanguage && pendingAppLanguage == null) return
        pendingAppLanguage = language
    }
    LaunchedEffect(pendingAppLanguage) {
        val language = pendingAppLanguage ?: return@LaunchedEffect
        localeSwitchProgress.runEchoLocaleSwitch(effectivePerformanceMode.isLightweight) {
            viewModel.setAppLanguage(language)
            permissionActivity?.refreshEchoAppLocale(language)
        }
        if (pendingAppLanguage == language) {
            pendingAppLanguage = null
            localeSwitchProgress.snapTo(1f)
        }
    }
    fun needsPagerSettle(targetPage: Int): Boolean =
        tabPagerState.settledPage != targetPage ||
            tabPagerState.currentPage != targetPage ||
            tabPagerState.currentPageOffsetFraction.absoluteValue > 0.001f
    fun navigateToPage(page: EchoPagerPage) {
        val targetPage = page.ordinal
        if (routeNavigationJob[0]?.isActive == true && tabPagerState.targetPage == targetPage) return
        page.dockTab?.let { selectedTab = it.ordinal }
        routeNavigationJob[0]?.cancel()
        routeNavigationJob[0] = appScope.launch {
            if (needsPagerSettle(targetPage)) {
                tabPagerState.animateScrollToPage(
                    page = targetPage,
                    animationSpec = dockNavigationMotionSpec(tabPagerState.currentPage, targetPage, effectivePerformanceMode),
                )
            }
        }
    }
    fun selectDockTab(tab: EchoTab) = navigateToPage(tab.pagerPage)
    fun selectEchoLinkMode(remote: Boolean) {
        if (remoteMode == remote) return
        echoLinkPlaybackRouter.selectRemoteMode(remote)
        nowPlayingExpanded = false
        queueSheetVisible = false
        openPcQueueNonce = 0
        viewModel.setLibrarySelectedSource("pc_echo")
    }
    fun openPcLibrary() {
        viewModel.setLibrarySelectedSource("pc_echo")
        if (remoteClient.library.value.tracks.isEmpty()) remoteClient.refreshLibrary()
        selectDockTab(EchoTab.Library)
    }
    fun openPcControls(queue: Boolean = false) {
        openPcTabNonce += 1
        if (queue) openPcQueueNonce += 1
        selectDockTab(EchoTab.Connect)
    }
    fun onNowPlayingCast() {
        castSheetVisible = true
    }
    LaunchedEffect(openCastRequest) {
        if (openCastRequest) {
            castSheetVisible = false
            nowPlayingExpanded = false
            queueSheetVisible = false
            openCastTabNonce += 1
            selectDockTab(EchoTab.Connect)
            EchoLaunchActions.consumeOpenCast()
        }
    }
    LaunchedEffect(openLibraryRequest) {
        if (openLibraryRequest) {
            castSheetVisible = false
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
        if (selectedAlbum != null && selectedArtist != null) {
            selectedAlbum = null
            return
        }
        val returnPage = detailReturnPage ?: EchoPagerPage.Library
        detailReturnPage = null
        if (returnPage == EchoPagerPage.Library) {
            clearLibraryDetail()
            return
        }
        returnPage.dockTab?.let { selectedTab = it.ordinal }
        val closingDetailKeys = listOf(
            selectedAlbum?.albumKey, selectedArtist?.artistKey, selectedGenre?.genreKey,
            selectedFolder?.folderKey, selectedPlaylist?.id,
        )
        routeNavigationJob[0]?.cancel()
        routeNavigationJob[0] = appScope.launch {
            val targetPage = returnPage.ordinal
            if (needsPagerSettle(targetPage)) {
                tabPagerState.animateScrollToPage(
                    page = targetPage,
                    animationSpec = routeMotionSpec(tabPagerState.currentPage, targetPage, effectivePerformanceMode),
                )
            }
            // A cancelled return must not clear a detail opened by the next navigation.
            val currentDetailKeys = listOf(
                selectedAlbum?.albumKey, selectedArtist?.artistKey, selectedGenre?.genreKey,
                selectedFolder?.folderKey, selectedPlaylist?.id,
            )
            if (currentDetailKeys == closingDetailKeys) {
                clearLibraryDetail()
            }
        }
    }
    LaunchedEffect(tabPagerState.settledPage, tabPagerState.isScrollInProgress) {
        if (!tabPagerState.isScrollInProgress) {
            EchoPagerPage.entries[tabPagerState.settledPage].dockTab?.let { settledTab ->
                if (settledTab.ordinal != selectedTab) selectedTab = settledTab.ordinal
            }
        }
    }

    LaunchedEffect(remoteStatus.connectionState, appSettings.echoLinkPreferLinkedLibrary, remoteMode) {
        if (
            remoteStatus.connectionState == EchoRemoteConnectionState.Connected &&
            appSettings.echoLinkPreferLinkedLibrary &&
            !remoteMode &&
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

    EchoOverlayBackHandler(enabled = listeningVisible && !pluginsVisible) {
        listeningVisible = false
    }
    EchoOverlayBackHandler(enabled = playbackHistoryVisible && !listeningStatsVisible && !pluginsVisible) {
        playbackHistoryVisible = false
    }
    EchoOverlayBackHandler(enabled = listeningStatsVisible && !pluginsVisible) {
        listeningStatsVisible = false
    }
    EchoOverlayBackHandler(enabled = searchVisible && !listeningVisible && !pluginsVisible) {
        searchVisible = false
    }
    EchoOverlayBackHandler(enabled = errorLogVisible && !pluginsVisible) {
        errorLogVisible = false
    }
    EchoOverlayBackHandler(
        enabled = queueSheetVisible && !pluginsVisible,
        onProgress = queueBack::update,
        onCancel = { queueBack.restore() },
        onDismiss = { queueSheetVisible = false },
    )
    EchoOverlayBackHandler(
        enabled = nowPlayingExpanded && !queueSheetVisible && !castSheetVisible && !pluginsVisible,
        onProgress = nowPlayingBack::update,
        onCancel = { nowPlayingBack.restore() },
        onDismiss = { nowPlayingExpanded = false },
    )
    EchoOverlayBackHandler(enabled = addMusicVisible && !pluginsVisible) {
        addMusicVisible = false
    }
    val shellOverlayOpen = searchVisible || listeningVisible || listeningStatsVisible || playbackHistoryVisible || errorLogVisible || queueSheetVisible || castSheetVisible || nowPlayingExpanded || pluginsVisible || addMusicVisible
    val connectPageSettled = appVisible && screenInteractive && !shellOverlayOpen &&
        tabPagerState.settledPage == EchoPagerPage.Connect.ordinal
    // One owner for discovery: closing either surface must not stop the other.
    val discoverCastDevices = appVisible && screenInteractive && (connectPageSettled || castSheetVisible)
    DisposableEffect(viewModel, discoverCastDevices) {
        if (discoverCastDevices) viewModel.startEchoLinkDiscovery()
        onDispose { if (discoverCastDevices) viewModel.stopEchoLinkDiscovery() }
    }
    EchoOverlayBackHandler(enabled = !shellOverlayOpen && libraryDetailOpen) {
        closeLibraryDetail()
    }
    EchoOverlayBackHandler(
        enabled = !shellOverlayOpen &&
            !libraryDetailOpen &&
            tabPagerState.currentPage == EchoPagerPage.Settings.ordinal,
    ) {
        selectDockTab(EchoTab.Now)
    }

    val customBackgroundActive = appSettings.customBackgroundMode != EchoBackgroundMode.Default &&
        !appSettings.customBackgroundUri.isNullOrBlank() &&
        !(effectivePerformanceMode.isLightweight && appSettings.customBackgroundMode == EchoBackgroundMode.Video)
    EchoMobileTheme(
        darkTheme = darkTheme,
        dynamicColor = appSettings.dynamicColorEnabled,
        colorTheme = EchoColorTheme.fromId(appSettings.colorTheme),
        customColors = appSettings.customColors,
        playbackHapticsEnabled = appSettings.playbackHapticsEnabled,
        fontFamily = uiFontFamily,
        fontScale = appSettings.uiFontScale,
        densityScale = appSettings.uiDensityScale,
        effectivePerformanceMode = effectivePerformanceMode,
        customBackgroundActive = customBackgroundActive,
    ) {
        EchoPlayerTransitionRoot(
            expanded = nowPlayingExpanded,
            // The current record-sleeve cover is square; the compact dock keeps rounded corners.
            expandedArtworkCornerRadius = if (appSettings.playerPageStyle == "classic") 24.dp else 0.dp,
            modifier = Modifier
                .fillMaxSize()
                .echoLocaleSwitchLayer(
                    progress = localeSwitchProgress,
                    lightweight = effectivePerformanceMode.isLightweight,
                    active = localeSwitchLayer,
                ),
        ) {
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
            EchoAdaptiveShell(
                selectedTab = selectedTab,
                selectedTabProgress = {
                    (tabPagerState.currentPage + tabPagerState.currentPageOffsetFraction - EchoPagerPage.Now.ordinal)
                        .coerceIn(0f, EchoTab.entries.lastIndex.toFloat())
                },
                onSelectTab = { selectDockTab(EchoTab.entries[it]) },
                modifier = Modifier.fillMaxSize()
                    .echoPageUnderlay(searchVisible || errorLogVisible || listeningStatsVisible || playbackHistoryVisible || listeningVisible || pluginsVisible || addMusicVisible)
                    .echoPlayerDepth(nowPlayingExpanded) { maxOf(nowPlayingBack.value, nowPlayingDragProgress) }
                    .echoSheetDepth(queueSheetVisible) { maxOf(queueBack.value, queueDragProgress) },
            ) { sideNavigation ->
                val tabPagerFling = rememberSilkPagerFlingBehavior(tabPagerState)
                val enteringInnerTabPage =
                    (selectedTab == EchoTab.Connect.ordinal || selectedTab == EchoTab.Diagnostics.ordinal) &&
                        tabPagerState.targetPage == EchoTab.entries[selectedTab].pagerPage.ordinal
                val innerTabPageSettled = enteringInnerTabPage ||
                    tabPagerState.settledPage == EchoPagerPage.Connect.ordinal ||
                    tabPagerState.settledPage == EchoPagerPage.Diagnostics.ordinal
                val tabPagerNestedScroll = rememberHomeSafePagerNestedScroll(tabPagerState, innerTabPageSettled)
                HorizontalPager(
                    state = tabPagerState,
                    userScrollEnabled = outerPagerUserScrollEnabled(
                        libraryDetailOpen = libraryDetailOpen,
                        prefersLibrarySplit = LocalEchoWidthSizeClass.current.prefersLibrarySplit,
                        settledPage = tabPagerState.settledPage,
                        targetPage = tabPagerState.targetPage,
                        scrollInProgress = tabPagerState.isScrollInProgress,
                        innerTabPageSettled = innerTabPageSettled,
                    ),
                    beyondViewportPageCount = if (effectivePerformanceMode.isLightweight) 0 else 1,
                    flingBehavior = tabPagerFling,
                    pageNestedScrollConnection = tabPagerNestedScroll,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (EchoPagerPage.entries[page]) {
                            EchoPagerPage.Library -> EchoLibraryPage(
                                viewModel = viewModel,
                                remoteClient = remoteClient,
                                playbackRouter = echoLinkPlaybackRouter,
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
                                onAddMusic = { addMusicVisible = true },
                                onImportLyricsForTrack = { track ->
                                    lyricsImportTrackId = track.id
                                    lyricsImportLauncher.launch(LyricsDocumentMimeTypes)
                                },
                                onPickTrackArtwork = { track ->
                                    artworkImportTrackId = track.id
                                    artworkImportLauncher.launch(ArtworkDocumentMimeTypes)
                                },
                                onOpenAlbum = { album ->
                                    if (selectedArtist == null) detailReturnPage = EchoPagerPage.Library
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
                                onOpenPlaylist = { playlist ->
                                    detailReturnPage = EchoPagerPage.Now
                                    selectedAlbum = null; selectedArtist = null; selectedGenre = null; selectedFolder = null
                                    selectedPlaylist = playlist
                                    selectDockTab(EchoTab.Library)
                                },
                                homeLayout = appSettings.homeLayout,
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
                                onOpenListeningStats = { listeningStatsVisible = true },
                                onOpenPlaybackHistory = { playbackHistoryVisible = true },
                                onResumePlayback = {
                                    if (!playbackStatus.isPlaying) routedPlayPause()
                                    expandNowPlaying()
                                },
                            )

                            EchoPagerPage.Settings -> {
                            val libraryStats by viewModel.libraryStats.collectAsStateWithLifecycle(LibraryStats())
                            val libraryScanProgress by viewModel.scanState.collectAsStateWithLifecycle()
                            val lastFmState by viewModel.lastFmState.collectAsStateWithLifecycle()
                            val listenBrainzState by viewModel.listenBrainzState.collectAsStateWithLifecycle()
                            val usbExclusiveTestResult by viewModel.usbExclusiveTestResult.collectAsStateWithLifecycle()
                            val errorLogCount by viewModel.errorLogCount.collectAsStateWithLifecycle(0)
                            val backupNotice by viewModel.backupNotice.collectAsStateWithLifecycle()
                            val offlineUsedBytes by remember(viewModel) { viewModel.observeOfflineUsedBytes() }
                                .collectAsStateWithLifecycle(0L)
                            SettingsScreen(
                                importedFontFamily = importedFontFamily,
                                isPageVisible = tabPagerState.currentPage == EchoPagerPage.Settings.ordinal,
                                isActive = tabPagerState.currentPage == EchoPagerPage.Settings.ordinal &&
                                    !nowPlayingExpanded && !searchVisible && !errorLogVisible && !queueSheetVisible && !pluginsVisible,
                                status = playbackStatus,
                                trackCount = libraryStats.trackCount,
                                albumCount = libraryStats.albumCount,
                                artistCount = libraryStats.artistCount,
                                libraryDurationMs = libraryStats.durationMs,
                                librarySizeBytes = libraryStats.totalSizeBytes,
                                libraryScanning = libraryScanProgress.isScanning,
                                onLoadLibraryHealth = viewModel::libraryHealthStats,
                                onInspectLibraryLyrics = viewModel::inspectLibraryLyrics,
                                appVersionLabel = BuildConfig.VERSION_NAME,
                                updateContent = {
                                    app.echo.android.feature.settings.SettingsUpdateRow {
                                        updater.open()
                                    }
                                },
                                dynamicArtworkEnabled = appSettings.dynamicArtworkEnabled,
                                compactModeEnabled = appSettings.compactModeEnabled,
                                playbackHapticsEnabled = appSettings.playbackHapticsEnabled,
                                performanceMode = appSettings.performanceMode,
                                effectivePerformanceMode = effectivePerformanceMode.id,
                                trackAudioInfoTagsVisible = appSettings.trackAudioInfoTagsVisible,
                                watchedFolderRescanEnabled = appSettings.watchedFolderRescanEnabled,
                                offlineWifiOnly = appSettings.offlineWifiOnly,
                                offlineUsedBytes = offlineUsedBytes,
                                pcHandoffEnabled = appSettings.pcHandoffEnabled,
                                onlineLyricsEnabled = appSettings.onlineLyricsEnabled,
                                lockScreenLyricsEnabled = appSettings.lockScreenLyricsEnabled,
                                lyricsOptions = appSettings.lyricsOptions,
                                onLyricsOptionsChange = viewModel::setLyricsOptions,
                                onOpenLyricsInterface = {
                                    expandNowPlaying()
                                    lyricsLaunchToken += 1
                                    viewModel.setShowLyricsControlDeck(true)
                                },
                                floatingLyrics = appSettings.floatingLyrics,
                                floatingLyricsPermissionGranted = hasOverlayPermission,
                                onFloatingLyricsChange = viewModel::setFloatingLyrics,
                                onRequestFloatingLyricsPermission = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(
                                                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                AndroidUri.parse("package:${context.packageName}"),
                                            ),
                                        )
                                    }
                                },
                                usbExclusiveEnabled = appSettings.usbExclusiveEnabled,
                                usbBitPerfectEnabled = appSettings.usbBitPerfectEnabled,
                                trackTransitions = appSettings.trackTransitions,
                                usbExclusiveAutoRequestOnStartup = appSettings.usbExclusiveAutoRequestOnStartup,
                                pauseOnAudioDisconnect = appSettings.pauseOnAudioDisconnect,
                                resumeOnAudioReconnect = appSettings.resumeOnAudioReconnect,
                                replayGainEnabled = appSettings.replayGainEnabled,
                                replayGainMode = appSettings.replayGainMode,
                                replayGainPreampDb = appSettings.replayGainPreampDb,
                                usbExclusiveTestResult = usbExclusiveTestResult,
                                customBackgroundMode = appSettings.customBackgroundMode,
                                customBackgroundUri = appSettings.customBackgroundUri,
                                startupBackgroundUri = appSettings.startupBackgroundUri,
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
                                lastFmStatusLabel = lastFmState.lastMessageArg?.let {
                                    stringResource(lastFmState.lastMessageRes, it)
                                } ?: stringResource(lastFmState.lastMessageRes),
                                lastFmErrorLabel = lastFmState.lastError,
                                lastFmWebAuthPending = lastFmState.webAuthPending,
                                lastFmApiKeyLocked = LastFmApiConfig.HAS_API_KEY,
                                lastFmSharedSecretLocked = LastFmApiConfig.HAS_SHARED_SECRET,
                                listenBrainzEnabled = appSettings.listenBrainzEnabled,
                                listenBrainzToken = appSettings.listenBrainzToken,
                                listenBrainzStatusLabel = listenBrainzState.lastMessageArg?.let {
                                    stringResource(listenBrainzState.lastMessageRes, it)
                                } ?: stringResource(listenBrainzState.lastMessageRes),
                                listenBrainzErrorLabel = listenBrainzState.lastError,
                                setlistFmApiKey = appSettings.setlistFmApiKey?.takeIf { it.isNotBlank() }
                                    ?: SetlistFmApiConfig.API_KEY.takeIf { it.isNotBlank() },
                                setlistFmApiKeyLocked = SetlistFmApiConfig.HAS_API_KEY,
                                onDynamicArtworkEnabledChange = viewModel::setDynamicArtworkEnabled,
                                onCompactModeEnabledChange = viewModel::setCompactModeEnabled,
                                onPlaybackHapticsEnabledChange = viewModel::setPlaybackHapticsEnabled,
                                onPerformanceModeChange = viewModel::setPerformanceMode,
                                onTrackAudioInfoTagsVisibleChange = viewModel::setTrackAudioInfoTagsVisible,
                                onWatchedFolderRescanEnabledChange = viewModel::setWatchedFolderRescanEnabled,
                                onOfflineWifiOnlyChange = viewModel::setOfflineWifiOnly,
                                onPcHandoffEnabledChange = viewModel::setPcHandoffEnabled,
                                onOnlineLyricsEnabledChange = viewModel::setOnlineLyricsEnabled,
                                onLockScreenLyricsEnabledChange = viewModel::setLockScreenLyricsEnabled,
                                onUsbExclusiveEnabledChange = viewModel::setUsbExclusiveEnabled,
                                onUsbBitPerfectEnabledChange = viewModel::setUsbBitPerfectEnabled,
                                onTrackTransitionsChange = viewModel::setTrackTransitions,
                                onUsbExclusiveAutoRequestOnStartupChange = viewModel::setUsbExclusiveAutoRequestOnStartup,
                                onPauseOnAudioDisconnectChange = viewModel::setPauseOnAudioDisconnect,
                                onResumeOnAudioReconnectChange = viewModel::setResumeOnAudioReconnect,
                                onReplayGainChange = viewModel::setReplayGain,
                                onReplayGainModeChange = viewModel::setReplayGainMode,
                                onTestUsbExclusiveDriver = viewModel::testUsbExclusiveDriver,
                                onPickImageBackground = { backgroundImageLauncher.launch(arrayOf("image/*")) },
                                onPickStartupBackground = { startupBackgroundLauncher.launch(arrayOf("image/*")) },
                                onClearStartupBackground = { viewModel.setStartupBackground(null) },
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
                                onAppLanguageChange = ::changeAppLanguage,
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
                                onSaveSetlistFmApiKey = viewModel::setSetlistFmApiKey,
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
                                onClearLocalLibraryIndex = viewModel::clearLocalLibraryIndex,
                                onCleanupLocalLibrary = {
                                    val result = viewModel.cleanupLocalLibrary()
                                    result.missingRemoved to result.duplicatesRemoved
                                },
                                errorLogCount = errorLogCount,
                                onOpenErrorLog = { errorLogVisible = true },
                                onOpenPlugins = { pluginsVisible = true },
                                backupNotice = backupNotice,
                                onExportBackup = { backupExportLauncher.launch("echo-migration.zip") },
                                onImportBackup = {
                                    backupImportLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                },
                            )
                            }

                            EchoPagerPage.Connect -> {
                            val remoteScanState by viewModel.remoteScanState.collectAsStateWithLifecycle()
                            val pcLibrary by remoteClient.library.collectAsStateWithLifecycle()
                            val discoveryState by viewModel.echoLinkDiscoveryState.collectAsStateWithLifecycle()
                            val echoLinkLanDevices by viewModel.echoLinkLanDevices.collectAsStateWithLifecycle()
                            val lanRenderers by viewModel.lanRenderers.collectAsStateWithLifecycle()
                            val lanRendererState by viewModel.lanRendererDiscoveryState.collectAsStateWithLifecycle()
                            ConnectScreen(
                                remoteMode = remoteMode,
                                onRemoteModeChange = ::selectEchoLinkMode,
                                onOpenPcLibrary = ::openPcLibrary,
                                openPcTabNonce = openPcTabNonce,
                                openPcQueueNonce = openPcQueueNonce,
                                onPcQueueOpened = { openPcQueueNonce = 0 },
                                librarySyncActions = app.echo.android.ui.connect.rememberLibrarySyncActions(viewModel, remoteClient),
                                remoteState = remoteStatus.connectionState,
                                pcTitle = remoteStatus.endpoint?.name ?: "PC ECHO",
                                trackTitle = remoteStatus.playback.track?.title.orEmpty(),
                                trackArtist = remoteStatus.playback.track?.artist.orEmpty(),
                                trackArtworkUrl = remoteStatus.playback.track?.artworkUrl,
                                isPlaying = remoteStatus.playback.state == EchoRemotePlaybackState.Playing,
                                remoteError = remoteStatus.error ?: castSetupError,
                                savedPcAddress = appSettings.echoLinkPcAddress,
                                savedPcToken = appSettings.echoLinkPcToken,
                                autoReconnectEnabled = appSettings.echoLinkAutoReconnectEnabled,
                                linkedLibraryDefault = appSettings.echoLinkPreferLinkedLibrary,
                                positionMs = remoteStatus.playback.positionMs,
                                durationMs = remoteStatus.playback.durationMs,
                                volume = remoteStatus.playback.volume,
                                volumeControlEnabled = remoteStatus.playback.volumeControlEnabled,
                                volumeLockedReason = remoteStatus.playback.volumeLockedReason,
                                outputMode = remoteStatus.playback.outputMode,
                                currentTrackId = remoteStatus.playback.queue.currentTrackId
                                    ?: remoteStatus.playback.track?.id,
                                queueItems = remoteStatus.playback.queue.items,
                                remoteLibrary = pcLibrary,
                                onSearchPcLibrary = remoteClient::refreshLibrary,
                                onLoadMorePcLibrary = remoteClient::loadMoreTracks,
                                remoteControlsActive = connectPageSettled,
                                subsonicServerUrl = appSettings.subsonicServerUrl,
                                subsonicUsername = appSettings.subsonicUsername,
                                subsonicPassword = appSettings.subsonicPassword,
                                webDavServerUrl = appSettings.webDavServerUrl,
                                webDavUsername = appSettings.webDavUsername,
                                webDavPassword = appSettings.webDavPassword,
                                smbServerUrl = appSettings.smbServerUrl,
                                smbUsername = appSettings.smbUsername,
                                smbPassword = appSettings.smbPassword,
                                onSyncSmbLibrary = viewModel::syncSmbLibrary,
                                onSaveSmbCredentials = viewModel::saveSmbCredentials,
                                onClearSmbCredentials = viewModel::clearSmbCredentials,
                                jellyfinServerUrl = appSettings.jellyfinServerUrl,
                                jellyfinUsername = appSettings.jellyfinUsername,
                                jellyfinPassword = appSettings.jellyfinPassword,
                                savedPcs = appSettings.echoLinkSavedPcs,
                                remoteScanState = remoteScanState,
                                onConnectPc = ::connectEchoLinkAddress,
                                onPlayPause = { remoteClient.send(EchoRemoteCommand.PlayPause) },
                                onPrevious = { remoteClient.send(EchoRemoteCommand.Previous) },
                                onNext = { remoteClient.send(EchoRemoteCommand.Next) },
                                onStop = { remoteClient.send(EchoRemoteCommand.Stop) },
                                onSeek = { positionMs -> remoteClient.send(EchoRemoteCommand.SeekTo(positionMs)) },
                                onVolume = { volume -> remoteClient.send(EchoRemoteCommand.SetVolume(volume)) },
                                onPlayQueueItem = { trackId ->
                                    remoteClient.send(EchoRemoteCommand.PlayTrackOnPc(trackId))
                                },
                                onHandoffPhoneToPc = if (appSettings.pcHandoffEnabled &&
                                    playbackStatus.track != null &&
                                    phoneCastPlan !is EchoLinkCastPlan.Blocked
                                ) {
                                    { performPhoneCast() }
                                } else null,
                                phoneTrackTitle = playbackStatus.track?.title,
                                phoneTrackArtist = playbackStatus.track?.artist,
                                phoneTrackArtworkUrl = playbackStatus.track?.artworkUri,
                                phoneTrackFormat = phoneCastFormatLabel,
                                phoneTrackLossless = phoneCastLossless,
                                castBlockedReason = (phoneCastPlan as? EchoLinkCastPlan.Blocked)?.reason,
                                casting = castingToPc,
                                castSessionActive = castSessionActive,
                                castSessionName = castSessionName,
                                sendingAddress = sendingCastAddress,
                                connectedLanAddress = remoteStatus.endpoint?.let { endpoint ->
                                    EchoLinkCastPolicy.advertisedBaseUrl(endpoint.host, endpoint.port)
                                        .removePrefix("http://")
                                        .removePrefix("https://")
                                },
                                onCastToAddress = ::requestPhoneCast,
                                onCastToConnected = if (
                                    remoteStatus.connectionState == EchoRemoteConnectionState.Connected &&
                                    phoneCastPlan !is EchoLinkCastPlan.Blocked
                                ) {
                                    { performPhoneCast() }
                                } else null,
                                onStopCast = ::stopPhoneCast,
                                lanRenderers = lanRenderers,
                                lanRendererState = lanRendererState,
                                activeRendererId = dlnaRenderer?.id,
                                onCastToRenderer = ::performDlnaCast,
                                onSwipeToLibrary = { navigateToPage(EchoPagerPage.Library) },
                                onSwipeToDiagnostics = { navigateToPage(EchoPagerPage.Diagnostics) },
                                openCastTabNonce = openCastTabNonce,
                                castQueueCount = EchoLinkCastPolicy.trackCount(phoneCastPlan),
                                showDsdWarning = EchoLinkCastPolicy.isDsd(
                                    playbackStatus.diagnostics.codec
                                        ?: EchoLinkCastFormat.fromTrack(playbackStatus.track?.uri.orEmpty()).codec,
                                ),
                                onDisconnect = remoteClient::disconnect,
                                onForgetPc = {
                                    echoLinkPlaybackRouter.selectRemoteMode(false)
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
                                onOpenListening = { listeningVisible = true },
                            )
                            }

                            EchoPagerPage.Diagnostics -> {
                                val equalizerState by viewModel.equalizerState.collectAsStateWithLifecycle()
                                val channelBalanceState by viewModel.channelBalanceState.collectAsStateWithLifecycle()
                                val opraState by viewModel.opraState.collectAsStateWithLifecycle()
                                val replayGainScan by viewModel.replayGainScanState.collectAsStateWithLifecycle()
                                DiagnosticsScreen(
                                    status = playbackStatus,
                                    positionFlow = viewModel.playbackPosition,
                                    equalizerState = equalizerState,
                                    dspSettings = appSettings.dsp,
                                    replayGainScan = replayGainScan,
                                    onDspSettings = viewModel::setDspSettings,
                                    onReplayGain = viewModel::setReplayGain,
                                    onReplayGainMode = viewModel::setReplayGainMode,
                                    onReplayGainScan = viewModel::scanReplayGainForCurrentTrack,
                                    onParametricChange = viewModel::setParametricFilters,
                                    channelBalanceState = channelBalanceState,
                                    opraState = opraState,
                                    onEqualizerEnabledChange = viewModel::setEqualizerEnabled,
                                    onEqualizerPresetSelected = viewModel::setEqualizerPreset,
                                    onEqualizerBandGainChange = viewModel::setEqualizerBandGain,
                                    onEqualizerReset = viewModel::resetEqualizer,
                                    onEqualizerPreampChange = viewModel::setEqualizerPreamp,
                                    onChannelBalanceChange = viewModel::setChannelBalance,
                                    onChannelBalanceReset = viewModel::resetChannelBalance,
                                    onOpraBrandSelected = viewModel::browseOpraBrand,
                                    onOpraQueryChange = viewModel::updateOpraQuery,
                                    onOpraSearch = { viewModel.searchOpraHeadphoneCorrections(refresh = false) },
                                    onOpraRefresh = { viewModel.searchOpraHeadphoneCorrections(refresh = true) },
                                    onOpraPresetSelected = viewModel::selectOpraPreset,
                                    onOpraApplySelected = viewModel::applySelectedOpraPreset,
                                    userPresets = appSettings.equalizerUserPresets,
                                    activeUserPresetId = appSettings.equalizerActiveUserPresetId,
                                    opraLastQuery = appSettings.opraLastQuery,
                                    onSaveUserPreset = viewModel::saveCurrentEqualizerPreset,
                                    onUpdateUserPreset = viewModel::updateCurrentEqualizerUserPreset,
                                    onApplyUserPreset = viewModel::applyEqualizerUserPreset,
                                    onRenameUserPreset = viewModel::renameEqualizerUserPreset,
                                    onDeleteUserPreset = viewModel::deleteEqualizerUserPreset,
                                    onImportShareCode = viewModel::importEqualizerShareCode,
                                    onBindPresetToOutput = viewModel::bindActiveEqualizerPresetToOutput,
                                    onUnbindPresetFromOutput = viewModel::unbindEqualizerPresetFromOutput,
                                    outputDeviceLabel = playbackStatus.diagnostics.usbDeviceName
                                        ?: playbackStatus.diagnostics.outputDeviceName,
                                    outputPresetBound = appSettings.equalizerDevicePresetIds.containsKey(
                                        app.echo.android.model.playback.EchoOutputDspPolicy.deviceKey(
                                            EchoOutputDeviceKind.fromId(playbackStatus.diagnostics.outputDeviceKind),
                                            playbackStatus.diagnostics.usbDeviceName
                                                ?: playbackStatus.diagnostics.outputDeviceName,
                                        ),
                                    ),
                                    onToggleOpraFavorite = viewModel::toggleStarredOpraPreset,
                                    bluetoothCodecNeedsPermission = !hasBluetoothConnectPermission &&
                                        playbackStatus.diagnostics.outputDeviceKind == EchoOutputDeviceKind.Bluetooth.id,
                                    onRequestBluetoothCodecPermission = {
                                        if (!hasBluetoothConnectPermission) {
                                            bluetoothPermissionLauncher.launch(bluetoothPermName)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                EchoBottomDockHost(
                    abovePlayer = if (remoteMode || remoteStatus.connectionState == EchoRemoteConnectionState.Connected) {
                        {
                            app.echo.android.design.EchoLinkModeSwitch(
                                remoteMode = remoteMode,
                                onRemoteModeChange = ::selectEchoLinkMode,
                                modifier = Modifier.widthIn(max = app.echo.android.design.LocalEchoContentMaxWidth.current)
                                    .fillMaxWidth().padding(horizontal = 16.dp),
                                showDescription = false,
                            )
                        }
                    } else null,
                    playerOverride = if (remoteMode) {
                        {
                            app.echo.android.feature.connect.EchoLinkRemoteMiniPlayer(
                                playback = remoteStatus.playback,
                                connected = remoteStatus.connectionState == EchoRemoteConnectionState.Connected,
                                pcTitle = remoteStatus.endpoint?.name ?: "PC ECHO",
                                onExpand = { openPcControls() },
                                onPlayPause = { remoteClient.send(EchoRemoteCommand.PlayPause) },
                                onNext = { remoteClient.send(EchoRemoteCommand.Next) },
                                onOpenQueue = { openPcControls(queue = true) },
                                modifier = Modifier.widthIn(max = app.echo.android.design.LocalEchoContentMaxWidth.current),
                            )
                        }
                    } else null,
                    animationsVisible = !shellOverlayOpen,
                    showNavigation = !sideNavigation,
                    viewModel = viewModel,
                    pagerState = tabPagerState,
                    playbackStatus = shellPlaybackStatus,
                    positionFlow = shellPositionFlow,
                    darkTheme = darkTheme,
                    selectedTab = selectedTab,
                    bottomDockExpanded = bottomDockExpanded,
                    effectivePerformanceMode = effectivePerformanceMode,
                    onPlayPause = ::routedPlayPause,
                    onHideDock = { bottomDockExpanded = false },
                    onShowDock = { bottomDockExpanded = true },
                    onSelectTab = { selectDockTab(EchoTab.entries[it]) },
                    onExpand = { expandNowPlaying() },
                    onOpenQueue = { queueSheetVisible = true },
                    onNext = ::routedSkipNext,
                    onPrevious = ::routedSkipPrevious,
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .onSizeChanged { bottomDockHeightPx = it.height },
                )
            }

            EchoExpandedPlayer(
                visible = nowPlayingExpanded,
                onHidden = { nowPlayingBack.reset() },
                modifier = Modifier.echoSheetDepth(queueSheetVisible) { maxOf(queueBack.value, queueDragProgress) },
            ) {
                EchoNowPlayingHost(
                    viewModel = viewModel,
                    playbackStatus = shellPlaybackStatus,
                    positionFlow = shellPositionFlow,
                    appSettings = appSettings,
                    lyricsFontFamily = lyricsFontFamily,
                    onDismiss = { nowPlayingExpanded = false },
                    predictiveBackProgress = { nowPlayingBack.value },
                    presentationExpanded = nowPlayingExpanded,
                    onDragProgress = { nowPlayingDragProgress = it },
                    onOpenQueue = { queueSheetVisible = true },
                    onCast = ::onNowPlayingCast,
                    castActive = castSessionActive,
                    onPlayPause = ::routedPlayPause,
                    onNext = ::routedSkipNext,
                    onPrevious = ::routedSkipPrevious,
                    onSeek = ::routedSeek,
                    onImportLyrics = {
                        lyricsImportTrackId = playbackStatus.track?.id
                        lyricsImportLauncher.launch(LyricsDocumentMimeTypes)
                    },
                    onImportLyricsFont = {
                        fontImportTarget = FontImportTarget.Lyrics
                        fontImportLauncher.launch(FontDocumentMimeTypes)
                    },
                    openLyricsRequestId = lyricsLaunchToken,
                    onOpenArtist = { artist ->
                        nowPlayingExpanded = false
                        queueSheetVisible = false
                        if (detailReturnPage == null) {
                            detailReturnPage = EchoPagerPage.entries[tabPagerState.settledPage]
                        }
                        selectedAlbum = null
                        selectedGenre = null
                        selectedFolder = null
                        selectedPlaylist = null
                        selectedArtist = artist
                        selectDockTab(EchoTab.Library)
                    },
                )
            }
            if (castSheetVisible) {
                EchoCastSheetHost(
                    viewModel = viewModel,
                    appSettings = appSettings,
                    playbackStatus = playbackStatus,
                    remoteStatus = remoteStatus,
                    castPlan = phoneCastPlan,
                    trackFormat = phoneCastFormatLabel,
                    trackLossless = phoneCastLossless,
                    casting = castingToPc || pendingCast != null,
                    castSessionActive = castSessionActive,
                    castSessionName = castSessionName,
                    sendingAddress = sendingCastAddress,
                    castSetupError = castSetupError,
                    activeRendererId = dlnaRenderer?.id,
                    onCastToAddress = ::requestPhoneCast,
                    onCastToConnected = ::performPhoneCast,
                    onCastToRenderer = ::performDlnaCast,
                    onStopCast = ::stopPhoneCast,
                    onDismiss = { castSheetVisible = false },
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
                    predictiveBackProgress = { queueBack.value },
                    onHidden = { queueBack.reset() },
                    status = playbackStatus,
                    queueState = playbackQueue,
                    onDismiss = { queueSheetVisible = false },
                    onDragProgress = { queueDragProgress = it },
                    onPlayItem = viewModel::playQueueItem,
                    onRemoveItem = viewModel::removeQueueItem,
                    onMoveItem = viewModel::moveQueueItem,
                    onClearQueue = viewModel::clearQueue,
                    onClearNextUp = viewModel::clearNextUp,
                    onCycleRepeatMode = viewModel::cycleRepeatMode,
                    onToggleShuffle = viewModel::toggleShuffle,
                    onPinQueueOffline = viewModel::pinCurrentQueueOffline,
                    modifier = Modifier.fillMaxSize(),
            )
            EchoPageOverlay(visible = searchVisible, onHidden = { searchQuery = "" }) {
                app.echo.android.ui.home.EchoUnifiedSearchHost(viewModel, remoteClient, echoLinkPlaybackRouter, searchQuery, searchVisible,
                    "${remoteStatus.endpoint?.id}:${remoteStatus.connectionState}",
                    onQuery = { searchQuery = it }, onClose = { searchVisible = false },
                    onAlbum = { album ->
                        searchVisible = false; detailReturnPage = EchoPagerPage.Now
                        selectedArtist = null; selectedFolder = null; selectedPlaylist = null; selectedGenre = null
                        selectedAlbum = album; selectDockTab(EchoTab.Library)
                    }, onArtist = { artist ->
                        searchVisible = false; detailReturnPage = EchoPagerPage.Now
                        selectedAlbum = null; selectedFolder = null; selectedPlaylist = null; selectedGenre = null
                        selectedArtist = artist; selectDockTab(EchoTab.Library)
                    }, onPlaylist = { playlist ->
                        searchVisible = false; detailReturnPage = EchoPagerPage.Now
                        selectedAlbum = null; selectedArtist = null; selectedFolder = null; selectedGenre = null
                        selectedPlaylist = playlist; selectDockTab(EchoTab.Library)
                    })
            }
            EchoPageOverlay(visible = addMusicVisible) {
                app.echo.android.ui.library.EchoAddMusicPage(
                    viewModel = viewModel,
                    settings = appSettings,
                    onBack = { addMusicVisible = false },
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
                )
            }
            EchoPageOverlay(visible = playbackHistoryVisible) {
                app.echo.android.ui.home.EchoPlaybackHistoryPage(
                    controller = viewModel.playbackHistory,
                    onStats = { listeningStatsVisible = true },
                    onBack = { playbackHistoryVisible = false },
                )
            }
            EchoPageOverlay(visible = listeningStatsVisible) {
                val listeningStats by viewModel.listeningStats.collectAsStateWithLifecycle()
                ListeningStatsScreen(
                    stats = listeningStats,
                    onLoad = viewModel::loadListeningStats,
                    onOpenTrack = { trackId ->
                        listeningStatsVisible = false
                        viewModel.playTrackFromLibrary(trackId)
                    },
                    onBack = { listeningStatsVisible = false },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            EchoPageOverlay(visible = listeningVisible) {
                ListeningScreen(
                    state = listeningState,
                    initialInput = listeningDraft,
                    defaultName = app.echo.android.listening.EchoListeningCodes.sanitizeName(android.os.Build.MODEL) ?: "ECHO",
                    onBack = { listeningVisible = false },
                    onConnect = { input, name, serverPassword ->
                        appScope.launch {
                            listening.controller.connect(input, name, serverPassword)
                        }
                    },
                    onRefresh = { appScope.launch { listening.controller.refreshRooms() } },
                    onJoin = { roomId, password ->
                        appScope.launch { listening.controller.join(roomId, password, invitation = null) }
                    },
                    onLeave = { appScope.launch { listening.controller.leave() } },
                    onDisconnect = { listening.controller.disconnect() },
                    onChat = { text -> appScope.launch { listening.controller.sendChat(text) } },
                    onVolume = listening.controller::setVolume,
                    onDismissPassword = listening.controller::dismissPassword,
                    onResumeAudio = {
                        if (playbackStatus.isPlaying ||
                            playbackStatus.state == EchoPlaybackState.Loading ||
                            playbackStatus.state == EchoPlaybackState.Buffering
                        ) {
                            viewModel.pause()
                        }
                        listening.controller.resumeAfterLocalPlayback()
                    },
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                )
            }
            EchoPageOverlay(
                visible = errorLogVisible,
            ) {
                val errorLogRecords by produceState(
                    initialValue = emptyList<EchoErrorRecord>(),
                    key1 = errorLogVisible,
                ) {
                    if (errorLogVisible) {
                        viewModel.errorLogRecords
                            .catch { emit(emptyList()) }
                            .collect { value = it }
                    }
                }
                ErrorLogScreen(
                    records = errorLogRecords,
                    onClear = viewModel::clearErrorLog,
                    onDelete = viewModel::deleteErrorLog,
                    onBack = { errorLogVisible = false },
                )
            }
            EchoPluginsOverlay(
                visible = pluginsVisible,
                playback = viewModel,
                onVisibleChange = { pluginsVisible = it },
            )
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
            val showLockLyrics = EchoLockLyricsPolicy.shouldShowOverLock(
                enabled = appSettings.lockScreenLyricsEnabled,
                isPlaying = playbackStatus.isPlaying,
                hasCurrentLine = lyricSnapshot.current != null,
                screenInteractive = screenInteractive,
                keyguardLocked = keyguardLocked,
            )
            LaunchedEffect(
                appSettings.lockScreenLyricsEnabled,
                playbackStatus.isPlaying,
                lyricSnapshot.current?.text,
            ) {
                activity?.setShowWhenLocked(
                    EchoLockLyricsPolicy.shouldKeepShowWhenLocked(
                        enabled = appSettings.lockScreenLyricsEnabled,
                        isPlaying = playbackStatus.isPlaying,
                        hasCurrentLine = lyricSnapshot.current != null,
                    ),
                )
            }
            if (showLockLyrics) {
                LockLyricsScene(
                    title = playbackStatus.track?.title.orEmpty(),
                    artist = playbackStatus.track?.artist.orEmpty(),
                    artworkUri = playbackStatus.track?.artworkUri,
                    snapshot = lyricSnapshot,
                    wordHighlightEnabled = appSettings.lyricsWordHighlightEnabled,
                    estimatedWordHighlightEnabled = appSettings.lyricsEstimatedWordHighlightEnabled,
                    modifier = Modifier.fillMaxSize(),
                )
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

private fun EchoTrackRef.toCastSource(
    diagnostics: EchoPlaybackDiagnostics? = null,
): EchoLinkCastSourceTrack = EchoLinkCastSourceTrack(
    id = id,
    uri = uri,
    title = title,
    artist = artist,
    album = album,
    artworkUri = artworkUri,
    durationMs = durationMs,
    sourceId = sourceId,
    sampleRateHz = diagnostics?.sampleRateHz?.takeIf { it > 0 } ?: sampleRateHz,
    bitDepth = diagnostics?.bitDepth?.takeIf { it > 0 },
    channelCount = diagnostics?.channelCount?.takeIf { it > 0 },
    codec = diagnostics?.codec?.takeIf { it.isNotBlank() },
)

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
