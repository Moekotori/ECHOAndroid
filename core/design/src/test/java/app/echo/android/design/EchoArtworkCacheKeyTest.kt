package app.echo.android.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class EchoArtworkCacheKeyTest {
    @Test
    fun sizeAndBitDepthChangeTheKey() {
        val uri = "content://media/external/audio/albumart/1"
        val base = echoArtworkCacheKey(uri, 160, highBitDepth = false)
        assertNotEquals(base, echoArtworkCacheKey(uri, 256, highBitDepth = false))
        assertNotEquals(base, echoArtworkCacheKey(uri, 160, highBitDepth = true))
        assertEquals(base, echoArtworkCacheKey(uri, 160, highBitDepth = false))
    }
}
