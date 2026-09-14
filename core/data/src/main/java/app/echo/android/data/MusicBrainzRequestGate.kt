package app.echo.android.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/** Shared by the application-owned album and artist repositories. */
class MusicBrainzRequestGate {
    private val mutex = Mutex()
    private var nextRequestAt = 0L

    suspend fun <T> run(request: suspend () -> T): T = mutex.withLock {
        val now = TimeUnit.NANOSECONDS.toMillis(System.nanoTime())
        if (nextRequestAt > now) delay(nextRequestAt - now)
        nextRequestAt = TimeUnit.NANOSECONDS.toMillis(System.nanoTime()) + 1100L
        request()
    }
}
