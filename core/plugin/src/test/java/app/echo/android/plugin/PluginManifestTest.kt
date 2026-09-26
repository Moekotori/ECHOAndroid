package app.echo.android.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PluginManifestTest {
    @Test
    fun parsesTheSampleManifest() {
        val manifest = parsePluginManifest(SamplePlugin.Manifest.trimIndent())
        assertEquals("echo.sample.hello", manifest?.id)
        assertEquals(listOf(PluginCapability.PlaybackRead, PluginCapability.UiPage), manifest?.permissions)
    }

    @Test
    fun rejectsUnknownApiAndKeepsUnknownPermissionsOut() {
        val manifest = parsePluginManifest(
            """
            {"format":1,"id":"echo.ok","name":"Ok","version":"1","entry":"main.js","api":1,
             "permissions":["ui.page","future.power","playback.read"]}
            """.trimIndent(),
        )
        assertEquals(listOf(PluginCapability.UiPage, PluginCapability.PlaybackRead), manifest?.permissions)
        assertNull(
            parsePluginManifest(
                """{"format":2,"id":"echo.ok","name":"Ok","version":"1","entry":"main.js","api":1}""",
            ),
        )
    }

    @Test
    fun rejectsPathLikeIdsAndEntries() {
        assertNull(parsePluginManifest(manifest(id = "../x")))
        assertNull(parsePluginManifest(manifest(entry = "../main.js")))
        assertNull(parsePluginManifest(manifest(entry = "/main.js")))
    }

    private fun manifest(id: String = "echo.ok", entry: String = "main.js") =
        """{"format":1,"id":"$id","name":"Ok","version":"1","entry":"$entry","api":1}"""
}
