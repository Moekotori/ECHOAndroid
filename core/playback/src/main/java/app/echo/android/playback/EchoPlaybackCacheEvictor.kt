package app.echo.android.playback

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import java.util.TreeSet

@UnstableApi
internal class EchoPlaybackCacheEvictor : CacheEvictor {
    private val leastRecentlyUsed = TreeSet(::compareSpans)
    private var currentSize = 0L

    override fun requiresCacheSpanTouches(): Boolean = true

    override fun onCacheInitialized() = Unit

    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        if (length != C.LENGTH_UNSET.toLong()) {
            evictCache(cache, length)
        }
    }

    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        leastRecentlyUsed.add(span)
        currentSize += span.length
        evictCache(cache, 0L)
    }

    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        leastRecentlyUsed.remove(span)
        currentSize -= span.length
    }

    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        onSpanRemoved(cache, oldSpan)
        onSpanAdded(cache, newSpan)
    }

    fun trim(cache: Cache) {
        evictCache(cache, 0L)
    }

    private fun evictCache(cache: Cache, requiredSpace: Long) {
        val maxBytes = EchoPlaybackCachePolicy.maxCacheBytes
        while (currentSize + requiredSpace > maxBytes && leastRecentlyUsed.isNotEmpty()) {
            cache.removeSpan(leastRecentlyUsed.first())
        }
    }

    private companion object {
        fun compareSpans(left: CacheSpan, right: CacheSpan): Int {
            val timestampDelta = left.lastTouchTimestamp - right.lastTouchTimestamp
            if (timestampDelta == 0L) return left.compareTo(right)
            return if (left.lastTouchTimestamp < right.lastTouchTimestamp) -1 else 1
        }
    }
}
