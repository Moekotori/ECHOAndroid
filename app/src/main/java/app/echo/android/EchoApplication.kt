package app.echo.android

import android.app.Application
import app.echo.android.design.EchoArtworkUrlRewriteRegistry
import app.echo.android.playback.EchoRemotePlaybackAuthRegistry

class EchoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        EchoArtworkUrlRewriteRegistry.replace { url ->
            EchoRemotePlaybackAuthRegistry.resolveSubsonicUrl(url)
        }
    }
}
