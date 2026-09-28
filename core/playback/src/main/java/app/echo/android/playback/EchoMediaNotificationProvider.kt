package app.echo.android.playback

import android.content.Context
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider

@UnstableApi
internal class EchoMediaNotificationProvider(context: Context) : DefaultMediaNotificationProvider(context) {
    override fun getNotificationContentTitle(metadata: MediaMetadata): CharSequence? =
        metadata.radioNowPlayingOrNull()?.title ?: metadata.title

    override fun getNotificationContentText(metadata: MediaMetadata): CharSequence? =
        EchoPlaybackProcessRuntime.notificationLyricLine.value
            ?: metadata.radioNowPlayingOrNull()?.artist
            ?: metadata.artist
}
