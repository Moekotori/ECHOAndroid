package app.echo.android

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import app.echo.android.connect.EchoBridgeArtwork
import app.echo.android.playback.EchoNotificationArtwork
import app.echo.android.playback.EchoPlaybackArtwork
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.Callable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

@UnstableApi
internal fun configureEchoBridgeNotificationArtwork() {
    EchoNotificationArtwork.loader = { uri ->
        if (!EchoBridgeArtwork.handles(uri.toString())) null
        else DataSourceBitmapLoader.DEFAULT_EXECUTOR_SERVICE.get().submit(Callable {
            val image = runBlocking {
                withTimeout(8_000) { EchoBridgeArtwork.read(uri.toString(), EchoPlaybackArtwork.NotificationMaxEdgePx) }
            } ?: throw IOException("Bridge artwork is unavailable")
            EchoPlaybackArtwork.decodeCapped(ByteArrayInputStream(image.bytes), EchoPlaybackArtwork.NotificationMaxEdgePx,
                256 * 1024) ?: throw IOException("Bridge artwork could not be decoded")
        })
    }
}
