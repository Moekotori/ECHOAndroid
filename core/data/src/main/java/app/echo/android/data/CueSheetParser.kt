package app.echo.android.data

import app.echo.android.model.library.CueSheet
import app.echo.android.model.library.CueSheetPolicy
import app.echo.android.model.library.CueSheetTrack
import java.nio.charset.StandardCharsets
import java.util.Locale

/** Bounded text parser. An incomplete sheet never authorizes a partial library replacement. */
object CueSheetParser {
    private val TrackHeader = Regex("""^(\d+)\s+(\S+)$""")
    private val Index = Regex("""^0*([01])\s+(\d+)(?::(\d+):(\d+))?$""")
    private val DataMode = Regex("""^(?:MODE[12]/\d+|CDI/\d+)$""", RegexOption.IGNORE_CASE)
    private val FileValue = Regex("""^(?:"((?:[^"\\]|\\.)+)"|'((?:[^'\\]|\\.)+)'|(.+?))(?:\s+\S+)?$""")

    fun parse(bytes: ByteArray): CueSheet? =
        if (bytes.isEmpty() || bytes.size > CueSheetPolicy.MaxCueBytes) null else parseText(decode(bytes))

    fun parseText(text: String): CueSheet? {
        if (text.isEmpty() || text.length > CueSheetPolicy.MaxCueBytes || '\u0000' in text) return null
        var file: String? = null
        var firstFile: String? = null
        var album: String? = null
        var performer: String? = null
        var year: Int? = null
        var genre: String? = null
        val tracks = ArrayList<CueSheetTrack>()
        val numbers = HashSet<Int>()
        val lastBySource = HashMap<String, Int>()
        var number: Int? = null
        var title: String? = null
        var artist: String? = null
        var start: Long? = null
        var pregap: Long? = null
        var data = false
        var failed = false
        fun sourceKey() = file.orEmpty().replace('\\', '/').lowercase(Locale.ROOT)
        fun bound(time: Long) {
            val previous = lastBySource[sourceKey()] ?: return
            val track = tracks[previous]
            if (time <= track.startMs || (track.endMs > 0L && time < track.endMs)) { failed = true; return }
            if (track.endMs == 0L) tracks[previous] = track.copy(endMs = time)
        }
        fun commit() {
            val n = number
            val offset = start
            if (n != null && !data) {
                if (offset == null || pregap?.let { it > offset } == true) failed = true
                else {
                    bound(offset)
                    lastBySource[sourceKey()] = tracks.size
                    tracks += CueSheetTrack(n, title?.takeIf { it.isNotBlank() } ?: "Track $n", artist, offset, fileName = file)
                }
            }
            // Always clear, even for incomplete or non-audio tracks.
            number = null; title = null; artist = null; start = null; pregap = null; data = false
        }
        for (raw in text.lineSequence()) {
            val line = raw.trim().trimStart('\uFEFF').trimStart()
            val (command, argument) = splitCommand(line) ?: continue
            when (command) {
                "FILE" -> {
                    val match = FileValue.matchEntire(argument) ?: return null
                    val next = match.groups[1]?.value?.replace("\\\"", "\"")
                        ?: match.groups[2]?.value?.replace("\\'", "'") ?: match.groupValues[3].trim()
                    if (next.isBlank() || next.length > 4096) return null
                    // FILE may appear inside TRACK before INDEX 01 (e.g. pregap in another file).
                    if (number != null && start == null) {
                        if (file != next) pregap = null
                    } else commit()
                    file = next
                    if (firstFile == null) firstFile = next
                }
                "TRACK" -> {
                    commit()
                    val match = TrackHeader.matchEntire(argument) ?: return null
                    val n = match.groupValues[1].toIntOrNull() ?: return null
                    if (n <= 0 || !numbers.add(n) || numbers.size > CueSheetPolicy.MaxTracks) return null
                    val mode = match.groupValues[2]
                    data = !mode.equals("AUDIO", true)
                    if (data && !DataMode.matches(mode)) return null
                    number = n
                }
                "TITLE", "PERFORMER" -> {
                    val value = (unquote(argument) ?: return null).take(200)
                    if (number != null) {
                        if (!data) { if (command == "TITLE") title = value else artist = value }
                    } else if (command == "TITLE") album = value else performer = value
                }
                "REM" -> {
                    val rem = splitCommand(argument) ?: continue
                    when (rem.first) {
                        "GENRE" -> genre = unquote(rem.second)?.take(200)
                        "DATE" -> year = unquote(rem.second)?.take(4)?.toIntOrNull()?.takeIf { it in 1000..9999 }
                    }
                }
                "INDEX" -> if (number != null) {
                    val match = Index.matchEntire(argument)
                    // Higher indices do not define movement boundaries.
                    if (match == null) {
                        val index = argument.takeWhile { !it.isWhitespace() }.toIntOrNull()
                        if (index == null || index <= 1) return null
                        continue
                    }
                    val time = indexTime(match) ?: return null
                    if (data) bound(time)
                    else if (match.groupValues[1] == "0") pregap = time
                    else { if (start != null) return null; start = time }
                }
            }
            if (failed) return null
        }
        commit()
        if (failed || tracks.isEmpty()) return null
        return CueSheet(firstFile, album, performer, year, genre, tracks)
    }

    private fun indexTime(match: MatchResult): Long? {
        val first = match.groupValues[2].toLongOrNull() ?: return null
        if (match.groups[3] == null) return first.takeIf { it <= Long.MAX_VALUE / 1000 }?.let { it * 1000 / 75 }
        val seconds = match.groupValues[3].toIntOrNull() ?: return null
        val frames = match.groupValues[4].toIntOrNull() ?: return null
        if (seconds !in 0..59 || frames !in 0..74 || first > Int.MAX_VALUE) return null
        return CueSheetPolicy.framesToMs(first.toInt(), seconds, frames)
    }

    private fun splitCommand(line: String): Pair<String, String>? {
        if (line.isBlank()) return null
        val split = line.indexOfFirst(Char::isWhitespace)
        return if (split < 0) line.uppercase(Locale.ROOT) to "" else
            line.substring(0, split).uppercase(Locale.ROOT) to line.substring(split + 1).trim()
    }

    private fun unquote(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        if (value[0] != '"' && value[0] != '\'') return value
        if (value.length < 2 || value.last() != value[0]) return null
        return value.substring(1, value.length - 1).replace("\\${value[0]}", "${value[0]}")
    }

    internal fun decode(bytes: ByteArray): String =
        TagTextDecoder.decode(bytes) ?: String(bytes, StandardCharsets.UTF_8)
}
