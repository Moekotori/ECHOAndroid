package app.echo.android

import android.app.Application
import androidx.media3.common.util.UnstableApi
import app.echo.android.data.EchoLibraryDatabase
import app.echo.android.data.EchoSettingsStore
import app.echo.android.design.EchoArtworkUrlRewriteRegistry
import app.echo.android.library.EchoLibraryPlaybackCatalog
import app.echo.android.playback.EchoPlaybackProcessRuntime
import app.echo.android.playback.EchoRemotePlaybackAuthRegistry
import app.echo.android.playback.EchoPlaybackRuntimeOptionsStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@UnstableApi
class EchoApplication : Application() {
    val albumOnlineInfo by lazy {
        app.echo.android.data.AlbumOnlineInfoRepository(java.io.File(cacheDir, "album-online-info"), BuildConfig.VERSION_NAME)
    }
    val echoLinkSession by lazy { EchoLinkSession(this) }

    override fun onCreate() {
        super.onCreate()
        EchoPlaybackProcessRuntime.setStreamResolver { mediaId, uri ->
            val id = app.echo.android.model.playback.EchoLinkPlaybackUri.trackId(mediaId, uri)
            if (id == null) uri else echoLinkSession.client.resolvePhoneStreamUrl(id) ?: uri
        }
        echoLinkSession
        EchoArtworkUrlRewriteRegistry.replace { url ->
            EchoRemotePlaybackAuthRegistry.resolveSubsonicUrl(url)
        }
        EchoPlaybackProcessRuntime.setCatalog(
            EchoLibraryPlaybackCatalog(EchoLibraryDatabase.create(this)),
        )
        val settingsStore = EchoSettingsStore(this)
        EchoPlaybackProcessRuntime.setSessionStore(EchoSettingsPlaybackSessionStore(settingsStore))
        // Playback preferences remain live when only the service/media buttons are running.
        EchoPlaybackProcessRuntime.scope.launch {
            settingsStore.appSettings.map { it.trackTransitions }.distinctUntilChanged()
                .collect(EchoPlaybackRuntimeOptionsStore::setTrackTransitions)
        }
        EchoPlaybackSurfaces.bind(this)
    }
}
