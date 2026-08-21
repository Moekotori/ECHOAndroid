package app.echo.android.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@UnstableApi
class EchoPlaybackService : MediaLibraryService() {
    private var mediaSession: MediaLibrarySession? = null
    private var player: ExoPlayer? = null
    private var sessionCallback: EchoPlaybackLibrarySessionCallback? = null
    private var sessionRestorer: EchoPlaybackSessionRestorer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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

    override fun onCreate() {
        super.onCreate()
        val exoPlayer = ExoPlayer.Builder(this)
            .setRenderersFactory(
                EchoRenderersFactory(
                    this,
                    EchoPlaybackProcessRuntime.equalizerController().processor,
                ),
            )
            .setMediaSourceFactory(DefaultMediaSourceFactory(echoPlaybackDataSourceFactory(this)))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(PREVIOUS_RESTART_THRESHOLD_MS)
            .setSeekForwardIncrementMs(SEEK_FORWARD_INCREMENT_MS)
            .setMaxSeekToPreviousPositionMs(PREVIOUS_RESTART_THRESHOLD_MS)
            .build()
            .also {
                EchoPlaybackProcessRuntime.enginePolicy(this).attachTo(it)
                it.setSkipSilenceEnabled(EchoPlaybackRuntimeOptionsStore.options.value.skipSilenceEnabled)
                it.addListener(playerListener)
            }

        player = exoPlayer
        serviceScope.launch {
            EchoPlaybackRuntimeOptionsStore.options
                .map { it.skipSilenceEnabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    player?.setSkipSilenceEnabled(enabled)
                }
        }
        val restorer = EchoPlaybackSessionRestorer(
            scope = serviceScope,
            store = EchoPlaybackProcessRuntime::sessionStore,
            player = { player },
            enginePolicy = { EchoPlaybackProcessRuntime.enginePolicyOrNull() },
        )
        sessionRestorer = restorer
        val callback = EchoPlaybackLibrarySessionCallback(
            context = this,
            scope = serviceScope,
            catalog = EchoPlaybackProcessRuntime::catalog,
            player = { player },
            session = { mediaSession },
            restorer = restorer,
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
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build(),
        )
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
