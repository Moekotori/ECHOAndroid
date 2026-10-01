package app.echo.android.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.echo.android.i18n.wrapEchoAppLocaleToMatchApplication
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import app.echo.android.model.lyrics.EchoLyricDisplaySnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@UnstableApi
class EchoPlaybackService : MediaLibraryService() {
    private var mediaSession: MediaLibrarySession? = null
    private var player: ExoPlayer? = null
    private var nextUpQueue: NextUpQueueController? = null
    private var abLoop: EchoAbLoopController? = null
    private var trackTransitions: EchoTrackTransitionController? = null
    private var smartTransitions: EchoSmartTransitionController? = null
    private var sessionCallback: EchoPlaybackLibrarySessionCallback? = null
    private var sessionRestorer: EchoPlaybackSessionRestorer? = null
    private var statusLyricsOverlay: EchoStatusLyricsOverlay? = null
    private var notificationLyrics: EchoNotificationLyricController? = null
    private var audioRoutePlayback: EchoAudioRoutePlaybackController? = null
    private var surfaceProgressJob: Job? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base.wrapEchoAppLocaleToMatchApplication())
    }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (!events.containsAny(
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                    Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_PLAYBACK_PARAMETERS_CHANGED,
                    Player.EVENT_POSITION_DISCONTINUITY,
                )
            ) {
                return
            }
            EchoPlaybackProcessRuntime.publishSurface(player.toPlaybackSurfaceSnapshot())
            updateSurfaceProgress(player)
            if (events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                )
            ) {
                sessionCallback?.onPlayerSurfaceChanged(player)
            }
            if (events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_PLAYBACK_PARAMETERS_CHANGED,
                    Player.EVENT_POSITION_DISCONTINUITY,
                )
            ) {
                sessionRestorer?.persistFromPlayer(
                    persistBecauseOfSeek = events.contains(Player.EVENT_POSITION_DISCONTINUITY),
                )
            }
        }
    }

    private fun updateSurfaceProgress(player: Player) {
        if (!player.isPlaying) {
            surfaceProgressJob?.cancel()
            surfaceProgressJob = null
            return
        }
        if (surfaceProgressJob?.isActive == true) return
        surfaceProgressJob = serviceScope.launch {
            while (isActive && player.isPlaying) {
                delay(1_000L)
                if (player.isPlaying) {
                    EchoPlaybackProcessRuntime.publishSurface(player.toPlaybackSurfaceSnapshot())
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val smartMixer = EchoPlaybackProcessRuntime.smartTransitionMixer()
        val exoPlayer = ExoPlayer.Builder(this)
            .setRenderersFactory(
                EchoRenderersFactory(
                    this,
                    EchoPlaybackProcessRuntime.equalizerController().processor,
                    smartMixer,
                    EchoPlaybackProcessRuntime.channelBalanceController().processor,
                ),
            )
            .setLoadControl(EchoPlaybackLoadControl())
            .setMediaSourceFactory(
                EchoRadioMediaSourceFactory(this),
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(false)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(PREVIOUS_RESTART_THRESHOLD_MS)
            .setSeekForwardIncrementMs(SEEK_FORWARD_INCREMENT_MS)
            .setMaxSeekToPreviousPositionMs(PREVIOUS_RESTART_THRESHOLD_MS)
            .build()
            .also {
                nextUpQueue = NextUpQueueController(it)
                EchoPlaybackProcessRuntime.enginePolicy(this).attachTo(it)
                it.addListener(EchoRadioPlaybackBinding(it))
                it.addListener(playerListener)
            }

        player = exoPlayer
        abLoop = EchoAbLoopController(exoPlayer, serviceScope)
        audioRoutePlayback = EchoAudioRoutePlaybackController(this, exoPlayer, serviceScope).also { it.start() }
        trackTransitions = EchoTrackTransitionController(exoPlayer, serviceScope, EchoPlaybackProcessRuntime::setTrackFadeGain)
        val decoder = EchoSmartTransitionDecoder(this)
        smartTransitions = EchoSmartTransitionController(
            player = exoPlayer,
            scope = serviceScope,
            mixer = smartMixer,
            analyzer = EchoSmartTransitionAnalyzer(
                decoder = decoder,
                cache = EchoSmartTransitionCache(java.io.File(cacheDir, "echo-smart-transition")),
            ),
            decoder = decoder,
        )
        val restorer = EchoPlaybackSessionRestorer(
            scope = serviceScope,
            store = EchoPlaybackProcessRuntime::sessionStore,
            player = { player },
            enginePolicy = { EchoPlaybackProcessRuntime.enginePolicyOrNull() },
        )
        sessionRestorer = restorer
        EchoPlaybackProcessRuntime.setRemoteAuthReadyListener(restorer::playIfRemoteAuthReady)
        val callback = EchoPlaybackLibrarySessionCallback(
            context = this,
            scope = serviceScope,
            catalog = EchoPlaybackProcessRuntime::catalog,
            player = { player },
            session = { mediaSession },
            restorer = restorer,
            nextUpQueue = { nextUpQueue },
            abLoop = { abLoop },
        )
        sessionCallback = callback
        val buttons = callback.currentButtons(exoPlayer)
        mediaSession = MediaLibrarySession.Builder(this, exoPlayer, callback)
            .setId("echo-mobile-main-session")
            .setBitmapLoader(
                EchoNotificationBitmapLoader(
                    context = this,
                    delegate = DataSourceBitmapLoader.Builder(this)
                        .setDataSourceFactory(echoPlaybackDataSourceFactory(this))
                        .build(),
                ),
            )
            .setCustomLayout(buttons)
            .setMediaButtonPreferences(buttons)
            .also { builder ->
                createLaunchPendingIntent()?.let(builder::setSessionActivity)
            }
            .build()
        EchoPlaybackProcessRuntime.publishSurface(exoPlayer.toPlaybackSurfaceSnapshot())
        updateSurfaceProgress(exoPlayer)
        setMediaNotificationProvider(EchoMediaNotificationProvider(this))
        statusLyricsOverlay = EchoStatusLyricsOverlay(this, serviceScope)
        notificationLyrics = EchoNotificationLyricController(
            player = exoPlayer,
            scope = serviceScope,
            onLine = { line ->
                if (EchoPlaybackProcessRuntime.setNotificationLyricLine(line) &&
                    (EchoPlaybackProcessRuntime.lyricsOptions.value.notificationEnabled ||
                        EchoPlaybackProcessRuntime.lyricsOptions.value.systemStatusBarEnabled)) {
                    mediaSession?.let { session -> onUpdateNotification(session, false) }
                }
            },
            onSnapshot = { snapshot ->
                val previous = EchoPlaybackProcessRuntime.lyricDisplaySnapshot.value
                EchoPlaybackProcessRuntime.setLyricDisplaySnapshot(snapshot)
                if (previous.isPlaying != snapshot.isPlaying &&
                    EchoPlaybackProcessRuntime.lyricsOptions.value.systemStatusBarEnabled) {
                    mediaSession?.let { onUpdateNotification(it, false) }
                }
            },
        )
        serviceScope.launch {
            EchoPlaybackProcessRuntime.lyricsOptions
                .map { Triple(it.notificationEnabled, it.systemStatusBarEnabled, it.statusHideTranslation) }
                .distinctUntilChanged()
                .collect { mediaSession?.let { onUpdateNotification(it, false) } }
        }
        serviceScope.launch {
            combine(
                EchoPlaybackProcessRuntime.notificationLyrics,
                EchoPlaybackProcessRuntime.displayLyrics,
            ) { document, lyrics -> document to lyrics }
                .collect { (document, lyrics) ->
                    notificationLyrics?.setDocument(document, lyrics)
                }
        }
        serviceScope.launch {
            restorer.restore(userRequestedPlay = false)
            player?.let { live ->
                EchoPlaybackProcessRuntime.publishSurface(live.toPlaybackSurfaceSnapshot())
                callback.onPlayerSurfaceChanged(live)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaSession.takeIf {
            EchoMediaSessionControllerGate.isAllowed(
                context = this,
                controllerInfo = controllerInfo,
                session = it,
            )
        }

    override fun onDestroy() {
        abLoop?.close()
        abLoop = null
        trackTransitions?.close()
        trackTransitions = null
        smartTransitions?.close()
        smartTransitions = null
        statusLyricsOverlay?.close()
        statusLyricsOverlay = null
        notificationLyrics?.close()
        notificationLyrics = null
        audioRoutePlayback?.stop()
        audioRoutePlayback = null
        EchoPlaybackProcessRuntime.setNotificationLyrics(null)
        EchoPlaybackProcessRuntime.setDisplayLyrics(null)
        EchoPlaybackProcessRuntime.setLyricDisplaySnapshot(EchoLyricDisplaySnapshot())
        EchoPlaybackProcessRuntime.setRemoteAuthReadyListener(null)
        sessionRestorer?.persistFromPlayer(force = true)
        player?.removeListener(playerListener)
        EchoPlaybackProcessRuntime.enginePolicyOrNull()?.detach()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        sessionCallback = null
        sessionRestorer = null
        player = null
        nextUpQueue = null
        serviceScope.cancel()
        super.onDestroy()
    }
}

private fun Context.createLaunchPendingIntent(): PendingIntent? {
    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        ?.apply {
            addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
            )
        }
        ?: return null
    return PendingIntent.getActivity(
        this,
        EchoPlaybackLaunchRequestCode,
        launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

private const val EchoPlaybackLaunchRequestCode = 2101
private const val PREVIOUS_RESTART_THRESHOLD_MS = 3_000L
private const val SEEK_FORWARD_INCREMENT_MS = 10_000L
