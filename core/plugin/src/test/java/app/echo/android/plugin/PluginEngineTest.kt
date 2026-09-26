package app.echo.android.plugin

import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PluginEngineTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun samplePageReadsPlaybackOnlyAfterTheGrant() {
        val services = FakeServices()
        withEngine(services) { engine ->
            val id = installSample(engine)
            engine.setGrant(id, PluginCapability.UiPage, true)
            engine.setEnabled(id, true)
            engine.flushForTest()
            engine.openPage(id)
            engine.flushForTest()
            engine.performAction(id, "refresh")
            engine.flushForTest()
            val blocked = engine.snapshot.plugins.single().page
            assertEquals("Playback can't be read yet: not_granted", blocked?.text())
            engine.setGrant(id, PluginCapability.PlaybackRead, true)
            engine.flushForTest()
            engine.performAction(id, "refresh")
            engine.flushForTest()
            assertTrue(engine.snapshot.plugins.single().page?.text().orEmpty().contains("Song"))
            assertTrue(services.commands.isEmpty())
        }
    }

    @Test
    fun transportAndJavaAccessStayClosedUntilAllowed() {
        val services = FakeServices()
        withEngine(services) { engine ->
            val id = install(engine, pagePlugin("""
                function onAction(id) {
                  var text = "blocked";
                  if (id === "play") {
                    var result = echo.playback.play();
                    text = result.ok ? "played" : result.error;
                  }
                  if (id === "java") {
                    try { java.lang.Runtime.getRuntime(); text = "escaped"; }
                    catch (e) { text = "blocked"; }
                  }
                  if (id === "class") {
                    try { text = "escaped " + echo.log.getClass(); }
                    catch (e) { text = "blocked"; }
                  }
                  echo.ui.setPage({ title: "Probe", items: [{ type: "text", text: text }] });
                }
            """, permissions = listOf("ui.page", "playback.control")))
            engine.setGrant(id, PluginCapability.UiPage, true)
            engine.setEnabled(id, true)
            engine.flushForTest()
            engine.performAction(id, "play")
            engine.flushForTest()
            assertEquals("not_granted", engine.snapshot.plugins.single().page?.text())
            assertTrue(services.commands.isEmpty())
            engine.setGrant(id, PluginCapability.PlaybackControl, true)
            engine.flushForTest()
            engine.performAction(id, "play")
            engine.flushForTest()
            assertEquals(listOf(TransportCommand.Play), services.commands)
            engine.performAction(id, "java")
            engine.flushForTest()
            assertEquals("blocked", engine.snapshot.plugins.single().page?.text())
            engine.performAction(id, "class")
            engine.flushForTest()
            assertEquals("blocked", engine.snapshot.plugins.single().page?.text())
        }
    }

    @Test
    fun libraryHitsDropFilePaths() {
        val services = FakeServices().apply {
            hits = listOf(LibraryTrackHit("file:///secret/song.flac", "Song", "Artist", "Album"))
        }
        withEngine(services) { engine ->
            val id = install(engine, pagePlugin("""
                function onAction(id) {
                  var found = echo.library.search("song");
                  var track = found.ok ? found.tracks[0].id : found.error;
                  echo.ui.setPage({ title: "Search", items: [{ type: "text", text: String(track) }] });
                }
            """, permissions = listOf("ui.page", "library.search")))
            engine.setGrant(id, PluginCapability.UiPage, true)
            engine.setGrant(id, PluginCapability.LibrarySearch, true)
            engine.setEnabled(id, true)
            engine.flushForTest()
            engine.performAction(id, "go")
            engine.flushForTest()
            val text = engine.snapshot.plugins.single().page?.text().orEmpty()
            assertFalse(text.contains("secret"))
            assertFalse(text.contains("file:"))
        }
    }

    @Test
    fun aTightLoopStopsThePlugin() {
        val services = FakeServices()
        withEngine(services) { engine ->
            val id = install(engine, pagePlugin("""
                function onEnable() { while (true) {} }
            """))
            engine.setEnabled(id, true)
            engine.flushForTest()
            val plugin = engine.snapshot.plugins.single()
            assertFalse(plugin.enabled)
            assertEquals("timeout", plugin.errorCode)
        }
    }

    @Test
    fun identicalPlaybackSnapshotsAreNotDeliveredTwice() {
        val services = FakeServices()
        withEngine(services) { engine ->
            val id = install(engine, pagePlugin("""
                var count = 0;
                function onPlayback() {
                  count += 1;
                  echo.storage.set("count", String(count));
                }
            """, permissions = listOf("playback.read", "storage")))
            engine.setGrant(id, PluginCapability.PlaybackRead, true)
            engine.setGrant(id, PluginCapability.Storage, true)
            engine.setEnabled(id, true)
            engine.flushForTest()
            val snapshot = PlaybackSnapshot(title = "Song", artist = "Artist", playing = true, positionMs = 1_000)
            engine.dispatchPlayback(snapshot)
            engine.flushForTest()
            engine.dispatchPlayback(snapshot)
            engine.flushForTest()
            val stored = java.io.File(folder.root, "plugins/$id/storage.json").readText()
            assertTrue(stored.contains("\"count\":\"1\""))
        }
    }

    @Test
    fun storageSurvivesARestart() {
        val root = folder.newFolder("plugins")
        val first = PluginEngine(root, FakeServices())
        first.start()
        val id = install(first, pagePlugin("""
            function onEnable() { echo.storage.set("note", "kept"); }
        """, permissions = listOf("storage")))
        first.setGrant(id, PluginCapability.Storage, true)
        first.setEnabled(id, true)
        first.flushForTest()
        first.close()
        val second = PluginEngine(root, FakeServices())
        try {
            second.start()
            second.flushForTest()
            second.setEnabled(id, true)
            second.flushForTest()
            assertTrue(second.snapshot.plugins.single().enabled)
            assertTrue(File(root, "$id/storage.json").readText().contains("kept"))
        } finally {
            second.close()
        }
    }

    private fun withEngine(services: FakeServices, block: (PluginEngine) -> Unit) {
        val engine = PluginEngine(folder.newFolder("plugins"), services)
        try {
            engine.start()
            engine.flushForTest()
            block(engine)
        } finally {
            engine.close()
        }
    }

    private fun installSample(engine: PluginEngine): String {
        var id = ""
        engine.installSample { result -> id = (result as PluginInstallResult.Installed).id }
        engine.flushForTest()
        return id
    }

    private fun install(engine: PluginEngine, bytes: ByteArray): String {
        var id = ""
        engine.install(bytes) { result -> id = (result as PluginInstallResult.Installed).id }
        engine.flushForTest()
        return id
    }

    private fun pagePlugin(script: String, permissions: List<String> = listOf("ui.page")): ByteArray {
        val manifest = """
            {"format":1,"id":"echo.probe","name":"Probe","version":"1","entry":"main.js","api":1,
             "permissions":[${permissions.joinToString(",") { "\"$it\"" }}]}
        """.trimIndent()
        return zip(listOf("echo-plugin.json" to manifest, "main.js" to script.trimIndent()))
    }

    private fun zip(entries: List<Pair<String, String>>): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(output).use { zip ->
            entries.forEach { (name, text) ->
                zip.putNextEntry(java.util.zip.ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun PluginPageDocument.text(): String =
        items.filterIsInstance<PluginPageItem.Text>().joinToString("\n") { it.text }
}

private class FakeServices : PluginServices {
    val commands = CopyOnWriteArrayList<TransportCommand>()
    var hits: List<LibraryTrackHit> = emptyList()
    override fun playbackSnapshot() = PlaybackSnapshot(title = "Song", artist = "Artist", album = "Album", playing = true)
    override fun transport(command: TransportCommand) { commands += command }
    override fun searchLibrary(query: String) = hits
    override fun fetch(url: String) = PluginHttpResponse(ok = false, status = 0, body = "", error = "failed")
}
