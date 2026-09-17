package app.echo.android.lock

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import app.echo.android.i18n.wrapEchoAppLocaleToMatchApplication
import app.echo.android.playback.EchoPlaybackIntents

@UnstableApi
class EchoLockLyricsActivity : ComponentActivity() {
    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF,
                Intent.ACTION_USER_PRESENT,
                -> finish()
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.wrapEchoAppLocaleToMatchApplication())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(false)
        ContextCompat.registerReceiver(
            this,
            dismissReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        setContent { EchoLockLyricsHost() }
    }

    override fun onResume() {
        super.onResume()
        val interactive = getSystemService(PowerManager::class.java)?.isInteractive != false
        if (!interactive) {
            finish()
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(dismissReceiver) }
        super.onDestroy()
    }

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, EchoLockLyricsActivity::class.java).apply {
                action = EchoPlaybackIntents.ACTION_OPEN_LOCK_LYRICS
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
    }
}

fun Context.isEchoKeyguardLocked(): Boolean {
    val keyguard = getSystemService(KeyguardManager::class.java) ?: return false
    return if (Build.VERSION.SDK_INT >= 22) keyguard.isDeviceLocked || keyguard.isKeyguardLocked
    else keyguard.isKeyguardLocked
}
