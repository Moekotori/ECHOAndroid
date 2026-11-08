package app.echo.android.plugin

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Installed disabled, with zero grants, like every other imported plugin. */
object SampleLyricsPlugin {
    const val Manifest = """{"format":1,"id":"echo.sample.lrclib","name":"LRCLIB Lyrics","version":"1",
        "entry":"main.js","api":1,"summary":"LRCLIB lyric candidates, searched only on request.",
        "permissions":["lyrics.provider","network"]}"""
    const val Script = """
        function onLyricsSearch(track) {
          var url = "https://lrclib.net/api/get?track_name=" + encodeURIComponent(track.title)
            + "&artist_name=" + encodeURIComponent(track.artist);
          if (track.album) url += "&album_name=" + encodeURIComponent(track.album);
          if (track.durationMs > 0) url += "&duration=" + Math.round(track.durationMs / 1000);
          var response = echo.net.fetch(url);
          if (!response.ok || response.status !== 200) return [];
          var result = JSON.parse(response.body);
          var text = result.syncedLyrics || result.plainLyrics;
          return text ? [{text: text}] : [];
        }
    """
    fun zipBytes(): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip ->
            for ((name, text) in listOf("echo-plugin.json" to Manifest, "main.js" to Script)) {
                zip.putNextEntry(ZipEntry(name)); zip.write(text.trimIndent().toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
        }
    }.toByteArray()
}
