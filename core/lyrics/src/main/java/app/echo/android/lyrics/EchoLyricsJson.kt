package app.echo.android.lyrics

import app.echo.android.model.lyrics.*
import org.json.JSONArray
import org.json.JSONObject

/** Versioned private storage representation, retaining word timing and auxiliary vocals. */
object EchoLyricsJson {
    fun encode(lyrics: EchoLyrics): String = JSONObject().apply {
        put("version", 1); put("format", lyrics.format.name); put("source", lyrics.sourceLabel)
        put("offset", lyrics.offsetMs); put("metadata", JSONObject(lyrics.metadata))
        put("lines", JSONArray().apply { lyrics.lines.forEach { line -> put(JSONObject().apply {
            put("start", line.startMs); put("end", line.endMs); put("text", line.text)
            put("translation", line.translation); put("romanization", line.romanization)
            put("speaker", line.speaker); put("background", line.isBackground)
            put("speakerId", line.speakerId); put("kana", line.kana)
            put("alternates", JSONArray().apply { line.alternateTexts.take(8).forEach { alternate ->
                put(JSONObject().put("role", alternate.role).put("text", alternate.text).put("language", alternate.language))
            } })
            put("words", JSONArray().apply { line.words.forEach { word -> put(JSONObject().apply {
                put("start", word.startMs); put("end", word.endMs); put("text", word.text)
            }) } })
        }) } })
    }.toString()

    fun decode(raw: String): EchoLyrics {
        val root = JSONObject(raw)
        require(root.getInt("version") == 1)
        val metadata = root.getJSONObject("metadata")
        val lines = root.getJSONArray("lines")
        return EchoLyrics(
            lines = List(lines.length()) { i ->
                val line = lines.getJSONObject(i); val words = line.getJSONArray("words")
                EchoLyricLine(line.getLong("start"), line.longOrNull("end"), line.getString("text"),
                    line.stringOrNull("translation"), line.stringOrNull("romanization"),
                    List(words.length()) { j -> val word = words.getJSONObject(j)
                        EchoLyricWord(word.getLong("start"), word.longOrNull("end"), word.getString("text")) },
                    line.stringOrNull("speaker"), line.optBoolean("background"),
                    line.optJSONArray("alternates")?.let { alternates -> List(minOf(alternates.length(), 8)) { j ->
                        val alternate = alternates.getJSONObject(j)
                        EchoLyricAlternateText(alternate.getString("role"), alternate.getString("text"), alternate.stringOrNull("language"))
                    } }.orEmpty(), line.stringOrNull("speakerId"), line.stringOrNull("kana"))
            },
            metadata = metadata.keys().asSequence().associateWith { metadata.getString(it) },
            sourceLabel = root.stringOrNull("source"), format = EchoLyricsFormat.valueOf(root.getString("format")),
            offsetMs = root.getLong("offset"),
        )
    }
    private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else getString(key)
    private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)
}
