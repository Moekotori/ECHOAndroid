package app.echo.android.connect

import app.echo.android.model.lyrics.EchoLyrics
import org.json.JSONArray
import org.json.JSONObject

data class EchoPhoneLyrics(
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val document: EchoLyrics,
    val userOffsetMs: Long = 0L,
)

/** Only public text/timings cross the LAN boundary; source labels and metadata stay local. */
internal fun phoneLyricsReply(value: EchoPhoneLyrics?): EchoLinkHttpReply {
    if (value == null) return EchoLinkHttpReply(body = "{\"lyrics\":null}")
    val document = value.document
    var textSize = value.title.length.toLong() + value.artist.length + (value.album?.length ?: 0)
    var wordCount = 0L
    if (document.lines.size > 4000 || value.title.length > 2048 || value.artist.length > 2048 || (value.album?.length ?: 0) > 2048) {
        return lyricsTooLarge()
    }
    for (line in document.lines) {
        if (line.text.length > 4096 || (line.translation?.length ?: 0) > 4096 || (line.romanization?.length ?: 0) > 4096 || line.words.size > 1000) return lyricsTooLarge()
        textSize += line.text.length + (line.translation?.length ?: 0) + (line.romanization?.length ?: 0)
        wordCount += line.words.size
        for (word in line.words) {
            if (word.text.length > 4096) return lyricsTooLarge()
            textSize += word.text.length
        }
        if (textSize > 100_000 || wordCount > 16_000) return lyricsTooLarge()
    }
    // Android parsers already apply the document offset. Bake only the user's adjustment
    // into transmitted timestamps, then send zero so desktop does not apply it twice.
    fun time(ms: Long): Long = if (ms < 0) ms else (ms + value.userOffsetMs.coerceIn(-30_000, 30_000)).coerceAtLeast(0)
    val lines = JSONArray()
    document.lines.forEach { line ->
        val words = JSONArray()
        line.words.forEach { word -> words.put(JSONObject().put("text", word.text).put("startMs", time(word.startMs))
            .put("endMs", word.endMs?.let(::time) ?: JSONObject.NULL)) }
        lines.put(JSONObject().put("timeMs", time(line.startMs)).put("endMs", line.endMs?.let(::time) ?: JSONObject.NULL)
            .put("text", line.text).putOpt("translation", line.translation).putOpt("romanization", line.romanization).put("words", words))
    }
    val lyrics = JSONObject().put("kind", if (document.isSynced) "synced" else "plain")
        .put("title", value.title).put("artist", value.artist).put("album", value.album ?: JSONObject.NULL)
        .put("durationSeconds", value.durationMs.coerceAtLeast(0) / 1000.0).put("offsetMs", 0).put("lines", lines)
    if (!document.isSynced) lyrics.put("plainText", document.lines.joinToString("\n") { it.text })
    val body = JSONObject().put("lyrics", lyrics).toString()
    return if (body.toByteArray(Charsets.UTF_8).size > 512 * 1024) lyricsTooLarge() else EchoLinkHttpReply(body = body)
}

private fun lyricsTooLarge() = EchoLinkHttpReply(413, "{\"error\":\"phone_lyrics_too_large\"}")
