package app.echo.android.data

import android.os.Process
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher

internal object LibraryScanDispatchers {
    val Limited: CoroutineDispatcher =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(
                {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                    runnable.run()
                },
                "echo-library-scan",
            ).apply { isDaemon = true }
        }.asCoroutineDispatcher()

    val Remote: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(16)
}
