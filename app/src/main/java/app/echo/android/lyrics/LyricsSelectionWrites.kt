package app.echo.android.lyrics

import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext

/** Versions invalidate outstanding reads/authorizations; the mutex orders durable commits. */
internal class LyricsSelectionWrites {
    private val commits = Mutex()
    private var nextVersion = 0L
    private val versions = object : LinkedHashMap<String, Long>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean = size > 128
    }

    @Synchronized
    fun begin(trackId: String): Long = (++nextVersion).also { versions[trackId] = it }

    @Synchronized
    fun current(trackId: String): Long = versions[trackId] ?: begin(trackId)

    @Synchronized
    fun isCurrent(trackId: String, version: Long): Boolean = versions[trackId] == version

    suspend fun runIfCurrent(trackId: String, version: Long, block: suspend () -> Unit): Boolean =
        commits.withLock {
            coroutineContext.ensureActive()
            if (!isCurrent(trackId, version)) return@withLock false
            block()
            true
        }
}
