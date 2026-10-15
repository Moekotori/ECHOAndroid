package app.echo.android.playback

import android.graphics.Bitmap
import android.net.Uri
import com.google.common.util.concurrent.ListenableFuture

/** The application may install an image protocol adapter without adding network dependencies here. */
object EchoNotificationArtwork {
    @Volatile
    var loader: ((Uri) -> ListenableFuture<Bitmap>?)? = null
}
