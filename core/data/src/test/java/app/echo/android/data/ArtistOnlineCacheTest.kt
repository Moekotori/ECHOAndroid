package app.echo.android.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ArtistOnlineCacheTest {
    @get:Rule val directory = TemporaryFolder()

    @Test fun refreshReplacesExistingSnapshotAndOversizeResponseKeepsGoodSnapshot() {
        val cache = ArtistOnlineCache(directory.root, maxBytes = 256, maxEntries = 2)
        cache.write("feed", JSONObject().put("version", 1))
        cache.write("feed", JSONObject().put("version", 2))
        assertEquals(2, cache.read("feed")!!.value!!.getInt("version"))
        cache.write("feed", JSONObject().put("body", "x".repeat(300)))
        assertEquals(2, cache.read("feed")!!.value!!.getInt("version"))
        assertEquals(1, directory.root.listFiles()!!.count { it.extension == "json" })
    }
}
