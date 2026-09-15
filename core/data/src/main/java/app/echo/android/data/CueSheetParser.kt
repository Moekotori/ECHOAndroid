package app.echo.android.data

import app.echo.android.model.library.CueSheet
import app.echo.android.model.library.CueSheetPolicy
import app.echo.android.model.library.CueSheetTrack
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

private val CueFallbackCharsets: List<Charset> = listOf("GB18030", "Shift_JIS", "ISO-8859-1")
    .mapNotNull { name -> runCatching { Charset.forName(name) }.getOrNull() }

object CueSheetParser {
    private val IndexTime = Regex("""^(\d{1,3}):(\d{2}):(\d{2})$""")
    private val TrackHeader = Regex("""^(\d{1,2})\s+AUDIO$""", RegexOption.IGNORE_CASE)

    fun parse(bytes: ByteArray): CueSheet? {
        if (bytes.isEmpty() || bytes.size > CueSheetPolicy.MaxCueBytes) return null
        val text = decode(bytes)
        return parseText(text)
    }

    fun parseText(text: String): CueSheet? {
        var fileName: String? = null
        var currentFile: String? = null
        var album: String? = null
        var performer: String? = null
        var year: Int? = null
        var genre: String? = null
        val tracks = ArrayList<CueSheetTrack>(8)
        var number = 0
        var title: String? = null
        var trackPerformer: String? = null
        var startMs: Long? = null
        var inTrack = false

        fun commit() {
            if (!inTrack) return
            val start = startMs ?: return
            val name = title?.takeIf { it.isNotBlank() } ?: "Track $number"
            tracks += CueSheetTrack(
                number = number,
                title = name.take(200),
                performer = trackPerformer,
                startMs = start,
                fileName = currentFile,
            )
            inTrack = false
            title = null
            trackPerformer = null
            startMs = null
        }

        for (raw in text.lineSequence()) {
            val line = raw.trim().trimStart('\uFEFF')
            if (line.isEmpty()) continue
            val (command, argument) = splitCommand(line) ?: continue
            when (command) {
                "FILE" -> {
                    commit()
                    currentFile = unquote(argument.substringBeforeLast(' ', argument)).takeIf { it.isNotEmpty() }
                    if (fileName == null) fileName = currentFile
                }
                "TITLE" -> {
                    val value = unquote(argument).takeIf { it.isNotEmpty() }
                    if (inTrack) title = value else if (album == null) album = value
                }
                "PERFORMER" -> {
                    val value = unquote(argument).takeIf { it.isNotEmpty() }
                    if (inTrack) trackPerformer = value else if (performer == null) performer = value
                }
                "REM" -> {
                    val rem = splitCommand(argument) ?: continue
                    when (rem.first) {
                        "GENRE" -> if (genre == null) genre = unquote(rem.second).takeIf { it.isNotEmpty() }
                        "DATE" -> if (year == null) year = unquote(rem.second).take(4).toIntOrNull()?.takeIf { it in 1000..9999 }
                    }
                }
                "TRACK" -> {
                    commit()
                    val match = TrackHeader.matchEntire(argument.trim()) ?: continue
                    number = match.groupValues[1].toInt()
                    if (number in 1..CueSheetPolicy.MaxTracks) {
                        inTrack = true
                    }
                }
                "INDEX" -> if (inTrack) {
                    val parts = argument.trim().split(Regex("\\s+"), limit = 2)
                    if (parts.size == 2) {
                        val index = parts[0].trimStart('0').ifEmpty { "0" }
                        when {
                            index == "1" -> startMs = parseIndex(parts[1])
                            index == "0" && startMs == null -> startMs = parseIndex(parts[1])
                        }
                    }
                }
            }
            if (tracks.size >= CueSheetPolicy.MaxTracks) break
        }
        commit()
        if (tracks.size < 2 && tracks.none { it.startMs > 0L }) return null
        if (tracks.isEmpty()) return null
        return CueSheet(
            fileName = fileName,
            album = album,
            performer = performer,
            year = year,
            genre = genre,
            tracks = tracks,
        )
    }

    private fun parseIndex(raw: String): Long? {
        val match = IndexTime.matchEntire(raw.trim()) ?: return null
        val minutes = match.groupValues[1].toInt()
        val seconds = match.groupValues[2].toInt()
        val frames = match.groupValues[3].toInt()
        if (seconds > 59 || frames > 74) return null
        return CueSheetPolicy.framesToMs(minutes, seconds, frames)
    }

    private fun splitCommand(line: String): Pair<String, String>? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        val split = trimmed.indexOf(' ')
        return if (split < 0) {
            trimmed.uppercase() to ""
        } else {
            trimmed.substring(0, split).uppercase() to trimmed.substring(split + 1).trim()
        }
    }

    private fun unquote(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.length >= 2 && trimmed.startsWith('"') && trimmed.endsWith('"')) {
            trimmed.substring(1, trimmed.length - 1)
        } else {
            trimmed
        }
    }

    internal fun decode(bytes: ByteArray): String {
        if (bytes.size >= 2) {
            when {
                bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                    return String(bytes, StandardCharsets.UTF_16LE)
                bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
                    return String(bytes, StandardCharsets.UTF_16BE)
            }
        }
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
        }
        val utf8 = String(bytes, StandardCharsets.UTF_8)
        if (!utf8.contains('\uFFFD')) return utf8
        val decoded = CueFallbackCharsets.mapNotNull { charset ->
            runCatching { String(bytes, charset) }.getOrNull()
        }
        val valid = decoded.filterNot { it.contains('\uFFFD') }.ifEmpty { decoded }
        val withCjk = valid.filter { cjkCount(it) > 0 }
        return withCjk.maxByOrNull(::cjkCount) ?: valid.lastOrNull() ?: utf8
    }

    private fun cjkCount(text: String): Int =
        text.count { ch ->
            ch in '\u4e00'..'\u9fff' || ch in '\u3040'..'\u30ff' || ch in '\uac00'..'\ud7af'
        }
}
