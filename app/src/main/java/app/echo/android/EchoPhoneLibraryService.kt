package app.echo.android

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.echo.android.i18n.wrapEchoAppLocaleToMatchApplication

/** Keeps an explicitly enabled LAN sharing session alive while the phone screen is off. */
class EchoPhoneLibraryService : Service() {
    private var sessionId: String? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    override fun attachBaseContext(base: Context) = super.attachBaseContext(base.wrapEchoAppLocaleToMatchApplication())
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == StopAction) {
            (application as? EchoApplication)?.echoLinkSession?.setPhoneLibrarySharingEnabled(false)
            stopSelf()
            return START_NOT_STICKY
        }
        sessionId = intent?.getStringExtra("sessionId")
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(Channel, getString(R.string.phone_library_notification_title), NotificationManager.IMPORTANCE_LOW),
        )
        val stop = PendingIntent.getService(this, 0, Intent(this, javaClass).setAction(StopAction),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, Channel)
            .setSmallIcon(R.drawable.media3_notification_small_icon)
            .setContentTitle(getString(R.string.phone_library_notification_title))
            .setContentText(getString(R.string.phone_library_notification_text))
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.phone_library_notification_stop), stop).build()
        ServiceCompat.startForeground(this, 0xEC02, notification,
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0)
        try {
            acquireSharingLocks()
        } catch (_: SecurityException) {
            releaseSharingLocks()
            (application as? EchoApplication)?.echoLinkSession?.phoneLibrary?.stop()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseSharingLocks()
        (application as? EchoApplication)?.echoLinkSession?.phoneLibrary?.serviceDestroyed(sessionId)
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun acquireSharingLocks() {
        // This phone serves audio itself; the PC's player cannot keep its CPU or Wi-Fi awake.
        if (wakeLock?.isHeld != true) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ECHO:PhoneLibrary").apply {
                    setReferenceCounted(false)
                    acquire()
                }
        }
        // Android 14+ maps HIGH_PERF to a foreground/screen-on-only lock; it cannot
        // keep a screen-off share alive. Keep the legacy lock only on older devices.
        if (Build.VERSION.SDK_INT < 34 && wifiLock?.isHeld != true) {
            val manager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiLock = manager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "ECHO:PhoneLibrary")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun releaseSharingLocks() {
        runCatching { wifiLock?.let { if (it.isHeld) it.release() } }
        wifiLock = null
        runCatching { wakeLock?.let { if (it.isHeld) it.release() } }
        wakeLock = null
    }

    companion object {
        private const val Channel = "echo_phone_library"
        private const val StopAction = "app.echo.android.STOP_PHONE_LIBRARY"
        fun start(context: Context, sessionId: String) = ContextCompat.startForegroundService(context,
            Intent(context, EchoPhoneLibraryService::class.java).putExtra("sessionId", sessionId))
        fun stop(context: Context) { context.stopService(Intent(context, EchoPhoneLibraryService::class.java)) }
    }
}
