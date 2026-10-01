package app.echo.android.playback

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList

@UnstableApi
internal class EchoMediaNotificationProvider(context: Context) : MediaNotification.Provider {
    private val delegate = object : DefaultMediaNotificationProvider(context) {
        override fun getNotificationContentTitle(metadata: MediaMetadata): CharSequence? =
            metadata.radioNowPlayingOrNull()?.title ?: metadata.title

        override fun getNotificationContentText(metadata: MediaMetadata): CharSequence? =
            EchoPlaybackProcessRuntime.notificationLyricLine.value
                .takeIf { EchoPlaybackProcessRuntime.lyricsOptions.value.notificationEnabled }
                ?: metadata.radioNowPlayingOrNull()?.artist ?: metadata.artist
    }

    override fun createNotification(
        mediaSession: MediaSession,
        mediaButtonPreferences: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification = decorate(delegate.createNotification(
        mediaSession, mediaButtonPreferences, actionFactory,
        { onNotificationChangedCallback.onNotificationChanged(decorate(it)) },
    ))

    /** Also decorates asynchronous cover updates; keep all Media3 actions and session metadata. */
    private fun decorate(result: MediaNotification): MediaNotification {
        val options = EchoPlaybackProcessRuntime.lyricsOptions.value
        val text = if (options.systemStatusBarEnabled &&
            EchoStatusLyricPolicy.supportsSystemStatusBar(Build.MANUFACTURER)) {
            EchoStatusLyricPolicy.text(EchoPlaybackProcessRuntime.lyricDisplaySnapshot.value, options.statusHideTranslation)
        } else null
        result.notification.apply {
            tickerText = text
            flags = if (text != null) flags or EchoStatusLyricPolicy.FlymeAlwaysShowTicker
                else flags and EchoStatusLyricPolicy.FlymeAlwaysShowTicker.inv()
        }
        return result
    }

    override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle): Boolean =
        delegate.handleCustomCommand(session, action, extras)

    override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo =
        delegate.notificationChannelInfo
}
