package app.echo.android.data

import app.echo.android.model.lyrics.EchoLyricLine
import app.echo.android.model.lyrics.EchoLyrics
import org.json.JSONObject

data class SubsonicLyricsContent(val document: EchoLyrics? = null, val text: String? = null)

fun fetchSubsonicLyrics(endpoint: SubsonicEndpoint, songId: String, artist: String, title: String): SubsonicLyricsContent? {
    val client = SubsonicClient(endpoint)
    if (songId.isNotBlank()) {
        val document = try {
            parseSubsonicStructuredLyrics(client.request("getLyricsBySongId.view", listOf("id" to songId)))
        } catch (cancelled: java.util.concurrent.CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        if (document != null) return SubsonicLyricsContent(document = document)
    }
    if (artist.isBlank() && title.isBlank()) return null
    val text = client.request("getLyrics.view", listOf("artist" to artist, "title" to title))
        .optJSONObject("lyrics")?.let { if (it.isNull("value")) null else it.optString("value").takeIf(String::isNotBlank) }
    return text?.let { SubsonicLyricsContent(text = it) }
}

/** Select one primary version. Merge only different languages with the same effective line time. */
internal fun parseSubsonicStructuredLyrics(root: JSONObject): EchoLyrics? {
    data class Version(val language: String, val synced: Boolean, val lines: List<EchoLyricLine>)
    val versions = root.optJSONObject("lyricsList")?.jsonObjects("structuredLyrics").orEmpty().take(32).map { block ->
        val synced = block.optBoolean("synced", true)
        val offset = block.optLong("offset")
        Version(block.optString("lang").lowercase().replace('_', '-'), synced,
            block.jsonObjects("line").take(20_000).mapNotNull { line ->
                val value = if (line.isNull("value")) "" else line.optString("value")
                if (value.isBlank()) null else EchoLyricLine(
                    startMs = if (synced && line.has("start")) (line.optLong("start") + offset).coerceAtLeast(0) else -1,
                    text = value)
            })
    }.filter { it.lines.isNotEmpty() }
    val primary = versions.firstOrNull { it.synced && it.lines.any { line -> line.startMs >= 0 } }
        ?: versions.firstOrNull() ?: return null
    var lines = primary.lines
    if (primary.synced && primary.language.isNotBlank()) {
        for (alternative in versions) {
            if (!alternative.synced || alternative.language.isBlank() || alternative.language == primary.language) continue
            val sameLanguage = alternative.language.substringBefore('-') == primary.language.substringBefore('-')
            val romanization = sameLanguage && alternative.language.contains("latn") && !primary.language.contains("latn")
            if (sameLanguage && !romanization) continue
            val byTime = alternative.lines.filter { it.startMs >= 0 }.associateBy { it.startMs }
            lines = lines.map { line ->
                val text = byTime[line.startMs]?.text?.takeIf { it != line.text } ?: return@map line
                if (romanization) line.copy(romanization = line.romanization ?: text)
                else line.copy(translation = line.translation ?: text)
            }
        }
    }
    return EchoLyrics(lines, metadata = mapOf("language" to primary.language), sourceLabel = "Navidrome")
}
