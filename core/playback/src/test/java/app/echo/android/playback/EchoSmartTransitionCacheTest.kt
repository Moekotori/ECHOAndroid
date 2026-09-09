package app.echo.android.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EchoSmartTransitionCacheTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun memoryAndDiskRoundTripAndEvict() {
        val cache = EchoSmartTransitionCache(folder.newFolder("smart"))
        val first = EchoSmartTransitionAnalysis(120_000, 120, 80, 0.4f, 0.6f)
        cache.put("one", first)
        assertEquals(first, cache.get("one"))
        assertTrue(cache.diskSizeBytes() > 0)

        repeat(EchoSmartTransitionPolicy.MemoryCacheLimit + 4) { index ->
            cache.put(
                "k$index",
                EchoSmartTransitionAnalysis(1_000L * index, 0, 0, 0.1f, 0.2f),
            )
        }
        assertTrue(cache.memorySize() <= EchoSmartTransitionPolicy.MemoryCacheLimit)
        assertTrue(cache.diskSizeBytes() <= EchoSmartTransitionPolicy.DiskCacheMaxBytes)
    }
}
