package app.echo.android

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import androidx.media3.common.util.UnstableApi
import app.echo.android.data.EchoErrorCrashFile
import app.echo.android.data.EchoErrorLogRepository
import app.echo.android.data.EchoLibraryDatabase
import app.echo.android.data.EchoRadioStore
import app.echo.android.data.EchoSettingsStore
import app.echo.android.data.ListeningHistoryRepository
import app.echo.android.data.readEchoStartupThemeSnapshot
import app.echo.android.i18n.initializeEchoAppLocale
import app.echo.android.i18n.wrapEchoAppLocale
import app.echo.android.model.error.EchoErrorLog
import app.echo.android.model.error.EchoErrorProcessInfo
import app.echo.android.model.playback.EchoReplayGainMode
import app.echo.android.model.playback.EchoShuffleOptions
import app.echo.android.model.playback.EchoTrackTransitionOptions
import app.echo.android.model.settings.EchoLyricsOptions
import app.echo.android.design.EchoArtworkImageLoader
import app.echo.android.design.EchoArtworkUrlRewriteRegistry
import coil.ImageLoader
import coil.ImageLoaderFactory
import java.io.File
import app.echo.android.library.EchoLibraryPlaybackCatalog
import app.echo.android.playback.EchoPlaybackCachePolicy
import app.echo.android.playback.EchoPlaybackProcessRuntime
import app.echo.android.playback.EchoRemotePlaybackAuthRegistry
import app.echo.android.playback.EchoPlaybackRuntimeOptionsStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@UnstableApi
class EchoApplication : Application(), ImageLoaderFactory, app.echo.android.playback.EchoAudioPipelineOwner {
    override val audioPipelineState = app.echo.android.playback.EchoAudioPipelineState()
    internal val radioSession by lazy { app.echo.android.radio.EchoRadioSession(EchoRadioStore(this)) }
    internal val podcasts by lazy {
        app.echo.android.data.PodcastRepository(app.echo.android.data.PodcastStore(this),
            app.echo.android.data.PodcastHttpClient(BuildConfig.VERSION_NAME))
    }
    private val musicBrainzGate by lazy { app.echo.android.data.MusicBrainzRequestGate() }
    @Volatile
    private var setlistFmApiKey: String? = null
    private val subsonicReferenceInfo by lazy {
        app.echo.android.data.SubsonicReferenceInformation(EchoLibraryDatabase.create(this), this) { EchoSubsonicEndpointRef.get() }
    }
    val artistOnlineInfo by lazy {
        app.echo.android.data.SubsonicArtistOnlineInfoLoader(app.echo.android.data.ArtistOnlineInfoRepository(
            File(cacheDir, "artist-online-info"),
            BuildConfig.VERSION_NAME,
            musicBrainzGate,
            setlistApiKey = {
                setlistFmApiKey?.takeIf { it.isNotBlank() }
                    ?: SetlistFmApiConfig.API_KEY.takeIf { it.isNotBlank() }
            },
        ), subsonicReferenceInfo)
    }
    val albumOnlineInfo by lazy {
        app.echo.android.data.SubsonicAlbumOnlineInfoLoader(
            app.echo.android.data.AlbumOnlineInfoRepository(File(cacheDir, "album-online-info"), BuildConfig.VERSION_NAME, musicBrainzGate), subsonicReferenceInfo)
    }
    val echoLinkSession by lazy { EchoLinkSession(this) }
    val listening by lazy { app.echo.android.listening.EchoListeningRuntime(this) }
    internal val lyricsSession: EchoNowPlayingLyricsSession by lazy { EchoNowPlayingLyricsSession(this) }
    internal val scrobbleSession by lazy {
        EchoScrobbleSession(this, EchoPlaybackProcessRuntime.scope, EchoPlaybackProcessRuntime.surface)
    }
    val listeningHistory by lazy { ListeningHistoryRepository(EchoLibraryDatabase.create(this)) }
    private val listeningHistoryRecorder by lazy {
        ListeningHistoryRecorder(EchoPlaybackProcessRuntime.scope, listeningHistory)
    }
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base.wrapEchoAppLocale(base.readEchoStartupThemeSnapshot().appLanguage))
    }

    internal val usbVolumeKeys by lazy { UsbVolumeKeys(this) }
    val diagnosticCapture by lazy { app.echo.android.data.EchoDiagnosticRecorder(this, EchoPlaybackProcessRuntime.scope, BuildConfig.VERSION_NAME) }

    override fun onCreate() {
        super.onCreate()
        app.echo.android.podcasts.startPodcastProgressRecorder(EchoPlaybackProcessRuntime.scope, podcasts.store)
        radioSession.start(EchoPlaybackProcessRuntime.scope, EchoPlaybackProcessRuntime.surface)
        configureEchoBridgeNotificationArtwork()
        initializeEchoAppLocale(readEchoStartupThemeSnapshot().appLanguage)
        EchoSubsonicListen.configure(this)
        installUncaughtExceptionHandler()
        EchoErrorLog.setProcessInfo(
            EchoErrorProcessInfo("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"),
        )
        EchoErrorLogRepository.create(this)
        EchoPlaybackCachePolicy.bindDeviceConstraints(this)
        EchoPlaybackProcessRuntime.scope.launch {
            app.echo.android.data.cleanupLegacyOfflineMusic(this@EchoApplication)
        }
        EchoPlaybackProcessRuntime.setStreamResolver { mediaId, uri ->
            val id = app.echo.android.model.playback.EchoLinkPlaybackUri.trackId(mediaId, uri)
            if (id == null) uri else echoLinkSession.client.resolvePhoneStreamUrl(id, previousStreamUrl = uri)
        }
        echoLinkSession
        EchoArtworkUrlRewriteRegistry.replace { url ->
            EchoRemotePlaybackAuthRegistry.resolveJellyfinUrl(
                EchoRemotePlaybackAuthRegistry.resolveSubsonicUrl(url),
            )
        }
        EchoPlaybackProcessRuntime.setCatalog(
            EchoLibraryPlaybackCatalog(
                database = EchoLibraryDatabase.create(this),
                context = this,
                loadRadioStations = radioSession.store::load,
            ),
        )
        val settingsStore = EchoSettingsStore(this)
        bindPlaybackDspSettings(settingsStore, this)
        EchoPlaybackProcessRuntime.setSessionStore(EchoSettingsPlaybackSessionStore(settingsStore) { ids ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                ids.distinct().chunked(256).flatMap { EchoLibraryDatabase.create(this@EchoApplication).hiddenTrackDao().hiddenIds(it) }.toSet()
            }
        })
        lyricsSession.start(settingsStore)
        scrobbleSession.start(settingsStore.appSettings)
        listeningHistoryRecorder.start(EchoPlaybackProcessRuntime.surface)
        app.echo.android.lyrics.overlay.EchoFloatingLyricsController(this, settingsStore).start()
        // Playback preferences and remote stream signing remain live when only the
        // service/media buttons are running (no ViewModel). One collector avoids
        // re-decoding the whole settings snapshot once per preference.
        EchoPlaybackProcessRuntime.scope.launch {
            var lastLyricsOptions: EchoLyricsOptions? = null
            var setlistKeyInitialized = false
            var lastSetlistKey: String? = null
            var lastShuffleOptions: EchoShuffleOptions? = null
            var lastTrackTransitions: EchoTrackTransitionOptions? = null
            var lastReplayGain: Triple<Boolean, Float, EchoReplayGainMode>? = null
            var lastAudioRoutePolicy: Pair<Boolean, Boolean>? = null
            var lastRemoteCredentials: RemotePlaybackCredentialSnapshot? = null
            settingsStore.appSettings.collect { settings ->
                if (settings.lyricsOptions != lastLyricsOptions) {
                    lastLyricsOptions = settings.lyricsOptions
                    EchoPlaybackProcessRuntime.setLyricsOptions(settings.lyricsOptions)
                }
                if (!setlistKeyInitialized || settings.setlistFmApiKey != lastSetlistKey) {
                    setlistKeyInitialized = true
                    lastSetlistKey = settings.setlistFmApiKey
                    setlistFmApiKey = settings.setlistFmApiKey
                }
                if (settings.shuffleOptions != lastShuffleOptions) {
                    lastShuffleOptions = settings.shuffleOptions
                    EchoPlaybackRuntimeOptionsStore.setShuffleOptions(settings.shuffleOptions)
                }
                if (settings.trackTransitions != lastTrackTransitions) {
                    lastTrackTransitions = settings.trackTransitions
                    EchoPlaybackRuntimeOptionsStore.setTrackTransitions(settings.trackTransitions)
                }
                val replayGain = Triple(
                    settings.replayGainEnabled,
                    settings.replayGainPreampDb,
                    EchoReplayGainMode.fromId(settings.replayGainMode),
                )
                if (replayGain != lastReplayGain) {
                    lastReplayGain = replayGain
                    EchoPlaybackProcessRuntime.setReplayGain(replayGain.first, replayGain.second)
                    EchoPlaybackProcessRuntime.setReplayGainMode(replayGain.third)
                }
                val audioRoutePolicy = settings.pauseOnAudioDisconnect to settings.resumeOnAudioReconnect
                if (audioRoutePolicy != lastAudioRoutePolicy) {
                    lastAudioRoutePolicy = audioRoutePolicy
                    EchoPlaybackProcessRuntime.setAudioRoutePlaybackPolicy(
                        audioRoutePolicy.first,
                        audioRoutePolicy.second,
                    )
                }
                val remoteCredentials = settings.remotePlaybackCredentialSnapshot()
                if (remoteCredentials != lastRemoteCredentials) {
                    lastRemoteCredentials = remoteCredentials
                    applyRemotePlaybackCredentials(settings, allowClearIfEmpty = true)
                    EchoSubsonicListen.onCredentialsReady()
                    EchoPlaybackProcessRuntime.notifyRemoteAuthReady()
                }
            }
        }
        EchoPlaybackProcessRuntime.scope.launch {
            settingsStore.appSettings.map { app.echo.android.playback.EchoPlaybackRuntimeOptions(
                audioFocusEnabled = it.audioFocusEnabled,
                audioInterruptionPolicy = app.echo.android.model.playback.EchoAudioInterruptionPolicy.fromId(it.audioInterruptionPolicy),
                sleepTimerFinishTrack = it.sleepTimerFinishTrack, stopOnTaskRemoved = it.stopOnTaskRemoved) }
                .distinctUntilChanged().collect { options ->
                    app.echo.android.playback.EchoPlaybackRuntimeOptionsStore.setListeningOptions(
                        options.audioInterruptionPolicy, options.sleepTimerFinishTrack, options.stopOnTaskRemoved, options.audioFocusEnabled)
                }
        }
        EchoSubsonicListen.startFromSurface()
        EchoPlaybackSurfaces.bind(this)
        echoLinkSession.systemCast.start()
    }

    override fun newImageLoader(): ImageLoader = EchoArtworkImageLoader.newImageLoader(this) {
        add(EchoBridgeArtworkFetcher.Factory())
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        EchoArtworkImageLoader.trimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND) {
            EchoPlaybackCachePolicy.bindDeviceConstraints(this)
        }
    }

    private fun installUncaughtExceptionHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                EchoErrorCrashFile.write(
                    file = EchoErrorCrashFile.pendingFile(this),
                    throwable = error,
                    threadName = thread.name,
                    appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
