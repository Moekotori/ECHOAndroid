package app.echo.android

import android.app.Application
import android.content.ComponentCallbacks2
import androidx.media3.common.util.UnstableApi
import app.echo.android.data.EchoErrorCrashFile
import app.echo.android.data.EchoErrorLogRepository
import app.echo.android.data.EchoLibraryDatabase
import app.echo.android.data.EchoSettingsStore
import app.echo.android.model.error.EchoErrorLog
import app.echo.android.model.error.EchoErrorProcessInfo
import app.echo.android.design.EchoArtworkImageLoader
import app.echo.android.design.EchoArtworkUrlRewriteRegistry
import coil.ImageLoader
import coil.ImageLoaderFactory
import app.echo.android.library.EchoLibraryPlaybackCatalog
import app.echo.android.playback.EchoPlaybackCachePolicy
import app.echo.android.playback.EchoPlaybackProcessRuntime
import app.echo.android.playback.EchoRemotePlaybackAuthRegistry
import app.echo.android.playback.EchoPlaybackRuntimeOptionsStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@UnstableApi
class EchoApplication : Application(), ImageLoaderFactory {
    val albumOnlineInfo by lazy {
        app.echo.android.data.AlbumOnlineInfoRepository(java.io.File(cacheDir, "album-online-info"), BuildConfig.VERSION_NAME)
    }
    val echoLinkSession by lazy { EchoLinkSession(this) }

    override fun onCreate() {
        super.onCreate()
        EchoErrorLog.setProcessInfo(
            EchoErrorProcessInfo("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"),
        )
        EchoErrorLogRepository.create(this)
        EchoPlaybackCachePolicy.bindDeviceConstraints(this)
        installUncaughtExceptionHandler()
        EchoPlaybackProcessRuntime.setStreamResolver { mediaId, uri ->
            val id = app.echo.android.model.playback.EchoLinkPlaybackUri.trackId(mediaId, uri)
            if (id == null) uri else echoLinkSession.client.resolvePhoneStreamUrl(id) ?: uri
        }
        echoLinkSession
        EchoArtworkUrlRewriteRegistry.replace { url ->
            EchoRemotePlaybackAuthRegistry.resolveJellyfinUrl(
                EchoRemotePlaybackAuthRegistry.resolveSubsonicUrl(url),
            )
        }
        EchoPlaybackProcessRuntime.setCatalog(
            EchoLibraryPlaybackCatalog(EchoLibraryDatabase.create(this)),
        )
        val settingsStore = EchoSettingsStore(this)
        EchoPlaybackProcessRuntime.setSessionStore(EchoSettingsPlaybackSessionStore(settingsStore))
        // Playback preferences and remote stream signing remain live when only the
        // service/media buttons are running (no ViewModel).
        EchoPlaybackProcessRuntime.scope.launch {
            settingsStore.appSettings.map { it.trackTransitions }.distinctUntilChanged()
                .collect(EchoPlaybackRuntimeOptionsStore::setTrackTransitions)
        }
        EchoPlaybackProcessRuntime.scope.launch {
            settingsStore.appSettings
                .map {
                    Triple(
                        it.replayGainEnabled,
                        it.replayGainPreampDb,
                        app.echo.android.model.playback.EchoReplayGainMode.fromId(it.replayGainMode),
                    )
                }
                .distinctUntilChanged()
                .collect { (enabled, preampDb, mode) ->
                    EchoPlaybackProcessRuntime.setReplayGain(enabled, preampDb)
                    EchoPlaybackProcessRuntime.setReplayGainMode(mode)
                }
        }
        EchoPlaybackProcessRuntime.scope.launch {
            settingsStore.appSettings
                .map { it.remotePlaybackCredentialSnapshot() to it }
                .distinctUntilChanged { previous, next -> previous.first == next.first }
                .collect { (_, settings) ->
                    applyRemotePlaybackCredentials(settings, allowClearIfEmpty = true)
                    EchoPlaybackProcessRuntime.notifyRemoteAuthReady()
                }
        }
        EchoSubsonicListen.startFromSurface()
        EchoPlaybackSurfaces.bind(this)
    }

    override fun newImageLoader(): ImageLoader = EchoArtworkImageLoader.newImageLoader(this)

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        EchoArtworkImageLoader.trimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
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
