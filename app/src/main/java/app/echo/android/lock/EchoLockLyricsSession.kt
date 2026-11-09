package app.echo.android.lock

import android.app.Application
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Shares a dismissal between the main window and the notification's lyrics Activity. */
internal class EchoLockLyricsSession(application: Application) {
    private val keyguard = application.getSystemService(KeyguardManager::class.java)
    private val mutableScreen = MutableStateFlow(
        EchoLockLyricsScreen(
            interactive = application.getSystemService(PowerManager::class.java)?.isInteractive == true,
            keyguardLocked = keyguard?.isKeyguardLocked == true,
        ),
    )
    val screen = mutableScreen.asStateFlow()

    init {
        // This receiver has the same lifetime as the application-owned session.
        ContextCompat.registerReceiver(
            application,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val previous = mutableScreen.value
                    mutableScreen.value = when (intent.action) {
                        Intent.ACTION_SCREEN_OFF -> previous.copy(interactive = false, dismissed = false)
                        Intent.ACTION_SCREEN_ON -> previous.copy(
                            interactive = true,
                            keyguardLocked = keyguard?.isKeyguardLocked == true,
                        )
                        Intent.ACTION_USER_PRESENT -> previous.copy(keyguardLocked = false, dismissed = false)
                        else -> return
                    }
                }
            },
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun dismiss() {
        mutableScreen.value = mutableScreen.value.copy(dismissed = true)
    }
}

internal data class EchoLockLyricsScreen(
    val interactive: Boolean,
    val keyguardLocked: Boolean,
    val dismissed: Boolean = false,
)
