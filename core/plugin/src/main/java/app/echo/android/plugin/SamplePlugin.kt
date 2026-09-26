package app.echo.android.plugin

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object SamplePlugin {
    const val Manifest: String = """
        {
          "format": 1,
          "id": "echo.sample.hello",
          "name": "Sample",
          "version": "1",
          "entry": "main.js",
          "api": 1,
          "summary": "Shows the current track on its own page.",
          "permissions": ["playback.read", "ui.page"]
        }
    """

    const val Script: String = """
        function onPageOpen() {
          show("Sample plugin loaded. Allow Read playback, then tap the button.");
        }

        function onAction(id) {
          if (id !== "refresh") return;
          var now = echo.playback.now();
          if (!now.ok) {
            show("Playback can't be read yet: " + now.error);
            return;
          }
          var state = now.playing ? "Playing" : "Paused";
          var title = now.title ? now.title : "Nothing is playing";
          show(state + "\n" + title + " — " + now.artist);
        }

        function show(text) {
          echo.ui.setPage({
            title: "Sample",
            items: [
              { type: "text", text: text },
              { type: "button", id: "refresh", label: "Read current track" }
            ]
          });
        }
    """

    fun zipBytes(): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            put(zip, "echo-plugin.json", Manifest.trimIndent())
            put(zip, "main.js", Script.trimIndent())
        }
        return output.toByteArray()
    }

    private fun put(zip: ZipOutputStream, name: String, text: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(text.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
}
