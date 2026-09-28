package app.echo.android.listening

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

internal object EchoListeningPlaybackBridge {
    @Volatile var stop: (() -> Unit)? = null
}

internal class EchoListeningAndroidNotifier(
    context: Context,
) : EchoListeningNotifier {
    private val app = context.applicationContext
    private var shown = false

    override fun show(title: String, artist: String) {
        val intent = Intent(app, EchoListeningPlaybackService::class.java)
            .putExtra(EchoListeningPlaybackService.EXTRA_TITLE, title)
            .putExtra(EchoListeningPlaybackService.EXTRA_ARTIST, artist)
        try {
            ContextCompat.startForegroundService(app, intent)
            shown = true
        } catch (_: Exception) {
            // A background start can be rejected. Playback in the open app still runs.
        }
    }

    override fun hide() {
        if (!shown) return
        shown = false
        app.stopService(Intent(app, EchoListeningPlaybackService::class.java))
    }
}

internal class EchoListeningPlaybackService : Service() {
    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            EchoListeningPlaybackBridge.stop?.invoke()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty()
            .ifBlank { getString(R.string.listening_notification_title) }
        val artist = intent?.getStringExtra(EXTRA_ARTIST).orEmpty()
        val notification = notification(title, artist)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    private fun notification(title: String, artist: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.listening_notification_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
        val stop = PendingIntent.getService(
            this,
            0,
            Intent(this, EchoListeningPlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            PendingIntent.getActivity(
                this,
                1,
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(applicationInfo.icon)
            .setContentTitle(title)
            .setContentText(artist.ifBlank { getString(R.string.listening_notification_title) })
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.listening_notification_stop), stop)
            .build()
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_ARTIST = "artist"
        private const val ACTION_STOP = "app.echo.android.listening.STOP"
        private const val CHANNEL_ID = "listening"
        private const val NOTIFICATION_ID = 0xEC11
    }
}
