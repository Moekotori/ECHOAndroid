package app.echo.android

import android.app.Application
import androidx.media3.common.util.UnstableApi
import app.echo.android.data.EchoLibraryDatabase
import app.echo.android.data.EchoSettingsStore
import app.echo.android.design.EchoArtworkUrlRewriteRegistry
import app.echo.android.library.EchoLibraryPlaybackCatalog
import app.echo.android.playback.EchoPlaybackProcessRuntime
import app.echo.android.playback.EchoRemotePlaybackAuthRegistry

@UnstableApi
class EchoApplication : Application() {
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
        EchoPlaybackProcessRuntime.setSessionStore(
            EchoSettingsPlaybackSessionStore(EchoSettingsStore(this)),
        )
        EchoPlaybackSurfaces.bind(this)
    }
}
