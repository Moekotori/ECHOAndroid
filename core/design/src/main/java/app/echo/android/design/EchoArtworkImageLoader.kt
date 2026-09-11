package app.echo.android.design

import android.content.ComponentCallbacks2
import android.content.Context
import app.echo.android.model.settings.EchoEffectivePerformanceMode
import coil.ImageLoader
import coil.memory.MemoryCache

object EchoArtworkImageLoader {
    @Volatile
    private var mode: EchoEffectivePerformanceMode = EchoEffectivePerformanceMode.Balanced
    @Volatile
    private var cache: EchoArtworkMemoryCache? = null

    fun newImageLoader(context: Context): ImageLoader {
        val app = context.applicationContext
        val inner = MemoryCache.Builder(app)
            .maxSizePercent(EchoArtworkMemoryPolicy.HighPerformancePercent)
            .build()
        val bounded = EchoArtworkMemoryCache(inner)
        bounded.setLimitBytes(EchoArtworkMemoryPolicy.sizeBytes(app, mode), clearFirst = false)
        cache = bounded
        return ImageLoader.Builder(app)
            .memoryCache(bounded)
            .build()
    }

    fun setEffectivePerformanceMode(
        context: Context,
        previous: EchoEffectivePerformanceMode,
        next: EchoEffectivePerformanceMode,
    ) {
        mode = next
        val app = context.applicationContext
        cache?.setLimitBytes(
            bytes = EchoArtworkMemoryPolicy.sizeBytes(app, next),
            clearFirst = EchoArtworkMemoryPolicy.shouldClearMemoryCache(previous, next),
        )
    }

    fun trimMemory(level: Int) {
        val memoryCache = cache ?: return
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            memoryCache.clear()
        } else {
            memoryCache.trimMemory(level)
        }
    }
}

internal class EchoArtworkMemoryCache(
    private val inner: MemoryCache,
) : MemoryCache {
    private val lock = Any()
    @Volatile
    private var limitBytes: Int = inner.maxSize

    override val size: Int
        get() = inner.size

    override val maxSize: Int
        get() = limitBytes

    override val keys: Set<MemoryCache.Key>
        get() = inner.keys

    override fun get(key: MemoryCache.Key): MemoryCache.Value? = inner[key]

    override fun set(key: MemoryCache.Key, value: MemoryCache.Value) {
        synchronized(lock) {
            inner[key] = value
            evictOverLimit()
        }
    }

    override fun remove(key: MemoryCache.Key): Boolean = synchronized(lock) { inner.remove(key) }

    override fun clear() = synchronized(lock) { inner.clear() }

    override fun trimMemory(level: Int) {
        synchronized(lock) {
            inner.trimMemory(level)
            evictOverLimit()
        }
    }

    fun setLimitBytes(bytes: Int, clearFirst: Boolean) {
        synchronized(lock) {
            limitBytes = bytes.coerceAtLeast(0)
            if (clearFirst) {
                inner.clear()
            } else {
                evictOverLimit()
            }
        }
    }

    private fun evictOverLimit() {
        if (inner.size <= limitBytes) return
        val snapshot = inner.keys.toList()
        var index = 0
        while (inner.size > limitBytes && index < snapshot.size) {
            inner.remove(snapshot[index])
            index++
        }
    }
}
