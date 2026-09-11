package app.echo.android.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.ContentMetadataMutations
import app.echo.android.model.settings.EchoEffectivePerformanceMode
import java.io.File
import java.util.NavigableSet
import java.util.TreeSet
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@UnstableApi
class EchoPlaybackCacheEvictorTest {
    @Before
    fun resetPolicy() {
        EchoPlaybackCacheTrim.action = {}
        EchoPlaybackCachePolicy.bindDeviceConstraints(Long.MAX_VALUE, false)
        EchoPlaybackCachePolicy.setEffectivePerformanceMode(EchoEffectivePerformanceMode.Balanced)
    }

    @After
    fun restorePolicy() {
        EchoPlaybackCacheTrim.action = {}
        EchoPlaybackCachePolicy.bindDeviceConstraints(Long.MAX_VALUE, false)
        EchoPlaybackCachePolicy.setEffectivePerformanceMode(EchoEffectivePerformanceMode.Balanced)
    }

    @Test
    fun removingAMissingSpanDoesNotShrinkTheAccountedSizeTwice() {
        val evictor = EchoPlaybackCacheEvictor()
        val cache = RecordingCache(evictor)
        val span = CacheSpan("track", 0L, 1_024L)
        evictor.onSpanAdded(cache, span)
        assertEquals(1_024L, evictor.cachedSizeBytes())
        evictor.onSpanRemoved(cache, span)
        assertEquals(0L, evictor.cachedSizeBytes())
        evictor.onSpanRemoved(cache, span)
        assertEquals(0L, evictor.cachedSizeBytes())
        assertEquals(0, evictor.cachedSpanCount())
    }

    @Test
    fun shrinkingTheCapEvictsLeastRecentlyUsedSpans() {
        val evictor = EchoPlaybackCacheEvictor()
        val cache = RecordingCache(evictor)
        EchoPlaybackCachePolicy.setEffectivePerformanceMode(EchoEffectivePerformanceMode.HighPerformance)
        val old = CacheSpan("old", 0L, EchoPlaybackCachePolicy.LightweightMaxBytes, 10L, File("old"))
        val keep = CacheSpan("keep", 0L, 4_096L, 20L, File("keep"))
        evictor.onSpanAdded(cache, old)
        evictor.onSpanAdded(cache, keep)
        EchoPlaybackCachePolicy.setEffectivePerformanceMode(EchoEffectivePerformanceMode.Lightweight)
        evictor.trim(cache)
        assertTrue(evictor.cachedSizeBytes() <= EchoPlaybackCachePolicy.LightweightMaxBytes)
        assertEquals(1, evictor.cachedSpanCount())
    }
}

@UnstableApi
private class RecordingCache(
    private val evictor: EchoPlaybackCacheEvictor,
) : Cache {
    override fun removeSpan(span: CacheSpan) {
        evictor.onSpanRemoved(this, span)
    }

    override fun getUid(): Long = 1L
    override fun release() = Unit
    override fun addListener(key: String, listener: Cache.Listener): NavigableSet<CacheSpan> = TreeSet()
    override fun removeListener(key: String, listener: Cache.Listener) = Unit
    override fun getCachedSpans(key: String): NavigableSet<CacheSpan> = TreeSet()
    override fun getKeys(): Set<String> = emptySet()
    override fun getCacheSpace(): Long = 0L
    override fun startReadWrite(key: String, position: Long, length: Long): CacheSpan =
        throw UnsupportedOperationException()
    override fun startReadWriteNonBlocking(key: String, position: Long, length: Long): CacheSpan? = null
    override fun startFile(key: String, position: Long, length: Long): File =
        throw UnsupportedOperationException()
    override fun commitFile(file: File, length: Long) = Unit
    override fun releaseHoleSpan(holeSpan: CacheSpan) = Unit
    override fun removeResource(key: String) = Unit
    override fun isCached(key: String, position: Long, length: Long): Boolean = false
    override fun getCachedLength(key: String, position: Long, length: Long): Long = 0L
    override fun getCachedBytes(key: String, position: Long, length: Long): Long = 0L
    override fun applyContentMetadataMutations(key: String, mutations: ContentMetadataMutations) = Unit
    override fun getContentMetadata(key: String): ContentMetadata =
        throw UnsupportedOperationException()
}
