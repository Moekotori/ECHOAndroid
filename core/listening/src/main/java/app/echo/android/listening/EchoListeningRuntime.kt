package app.echo.android.listening

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class EchoListeningRuntime(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val appContext = context.applicationContext

    @Volatile var outputAllowed: () -> Boolean = { true }

    val controller: EchoListeningController = EchoListeningController(
        scope = scope,
        transport = OkHttpListeningTransport(),
        sink = EchoListeningAndroidPlayer(appContext),
        notifier = EchoListeningAndroidNotifier(appContext),
        outputAllowed = { outputAllowed() },
    )

    init {
        EchoListeningPlaybackBridge.stop = { controller.suspendForLocalPlayback() }
    }
}
