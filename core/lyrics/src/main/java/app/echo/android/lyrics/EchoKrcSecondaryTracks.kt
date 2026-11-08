package app.echo.android.lyrics

import app.echo.android.model.lyrics.EchoLyricLine
import org.json.JSONObject
import java.util.Base64

/** Optional KRC language metadata must never prevent the primary lyric from loading. */
internal object EchoKrcSecondaryTracks {
    private val language = Regex("""\[language:([^]]*)]""", RegexOption.IGNORE_CASE)
    private val whitespace = Regex("""\s+""")

    fun attach(text: String, lines: List<EchoLyricLine>): List<EchoLyricLine> {
        val encoded = language.find(text)?.groupValues?.get(1) ?: return lines
        if (encoded.length > 512 * 1024) return lines
        val content = runCatching {
            val bytes = Base64.getDecoder().decode(encoded.replace('-', '+').replace('_', '/'))
            JSONObject(bytes.toString(Charsets.UTF_8)).optJSONArray("content")
        }.getOrNull() ?: return lines
        var translated: List<String>? = null
        var romanized: List<String>? = null
        for (i in 0 until minOf(content.length(), 32)) {
            val track = content.optJSONObject(i) ?: continue
            val type = track.optInt("type", -1)
            if (type !in 0..1 || type == 0 && romanized != null || type == 1 && translated != null) continue
            val rows = track.optJSONArray("lyricContent") ?: continue
            val values = List(minOf(rows.length(), 2000)) { row ->
                val parts = rows.optJSONArray(row)
                val value = if (parts == null) rows.optString(row, "") else buildString {
                    for (part in 0 until minOf(parts.length(), 512)) append(parts.optString(part, ""))
                }
                value.replace(whitespace, " ").trim().takeIf { it.length <= 2000 }.orEmpty()
            }
            if (type == 0) romanized = values else translated = values
        }
        return lines.mapIndexed { index, line -> line.copy(
            translation = translated?.getOrNull(index)?.takeIf(String::isNotBlank) ?: line.translation,
            romanization = romanized?.getOrNull(index)?.takeIf(String::isNotBlank) ?: line.romanization,
        ) }
    }
}
