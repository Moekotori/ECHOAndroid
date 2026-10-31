package app.echo.android.model.library

import java.util.Locale

/** Technical metadata, not an assertion about audible quality or the recording's provenance. */
data class EchoTrackAudioQuality(val sampleRateHz: Int? = null, val bitDepth: Int? = null, val bitrate: Int? = null, val codecMime: String? = null) {
    fun score(track: EchoTrack): Int {
        val extension = if (codecMime == null) track.fileName.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT) else ""
        val mime = (codecMime ?: track.mimeType).orEmpty().substringBefore(';').trim().lowercase(Locale.ROOT)
        val lossless = (codecMime == null && extension in LosslessExtensions) || mime in LosslessMimes
        val known = lossless || extension in LossyExtensions || mime.startsWith("audio/")
        return (if (lossless) 10000 else if (known) 2000 else 250) +
            (bitDepth ?: 0).coerceIn(0, 32) * 100 + (sampleRateHz ?: track.sampleRateHz ?: 0).coerceIn(0, 192000) / 100 +
            (bitrate ?: 0).coerceIn(0, 10000000) / 10000
    }
    private companion object {
        val LosslessExtensions = setOf("flac", "alac", "wav", "wave", "aif", "aiff", "ape", "wv", "dsf", "dff", "tta", "tak")
        val LossyExtensions = setOf("mp3", "aac", "m4a", "ogg", "opus", "wma")
        val LosslessMimes = setOf("audio/raw", "audio/flac", "audio/x-flac", "application/flac", "application/x-flac", "audio/alac",
            "audio/wav", "audio/x-wav", "audio/aiff", "audio/x-aiff", "audio/x-ape", "audio/x-wavpack", "audio/dsf", "audio/dff")
    }
}

object EchoDuplicateVersionPolicy {
    private val speed = Regex("(?:^|[\\s._()\\[\\]-])(\\d+(?:\\.\\d+)?)\\s*[x×](?=$|[\\s._()\\[\\]-])", RegexOption.IGNORE_CASE)
    private fun speed(track: EchoTrack): String? = speed.find(track.title + " " + track.fileName.orEmpty())
        ?.groupValues?.get(1)?.toDoubleOrNull()?.takeUnless { it == 1.0 }?.toString()
    private val editionMarkers = listOf("\\blive\\b|现场", "\\bremix\\b|混音", "\\bremaster(?:ed)?\\b|重制", "\\brerecord(?:ed)?\\b|重录",
        "\\bcover\\b|翻唱", "\\binstrumental\\b|纯音乐", "\\bkaraoke\\b|伴奏", "\\boff\\s+vocal\\b", "\\btv\\s+size\\b",
        "\\bradio\\s+edit\\b|剪辑版", "\\bextended\\b|加长版", "\\bacoustic\\b", "\\bdemo\\b", "\\bmono\\b", "\\bstereo\\s+mix\\b")
        .map(::Regex)
    private val releaseYear = Regex("(?:19|20)\\d{2}")
    private fun editions(track: EchoTrack): Pair<List<Int>, List<String>> {
        val text = EchoTrackVersionPolicy.normalized(track.title + " " + track.fileName.orEmpty()).replace('_', ' ')
        val markers = editionMarkers.indices.filter { editionMarkers[it].containsMatchIn(text) }
        val years = if (2 in markers || 3 in markers) releaseYear.findAll(text).map { it.value }.distinct().sorted().toList() else emptyList()
        return markers to years
    }
    fun associationKey(track: EchoTrack): String? = EchoTrackVersionPolicy.key(track)?.let { base ->
        val (markers, years) = editions(track)
        "$base\u0000clip:${track.clipStartMs}:${track.clipEndMs}\u0000speed:${speed(track).orEmpty()}\u0000edition:${markers.joinToString(",")}:${years.joinToString(",")}"
    }

    fun matches(a: EchoTrack, b: EchoTrack): Boolean = EchoTrackVersionPolicy.matches(a, b) && speed(a) == speed(b) && editions(a) == editions(b) &&
        agrees(a.discNumber, b.discNumber) && agrees(a.trackNumber, b.trackNumber) && agrees(a.year, b.year)
    private fun agrees(a: Int?, b: Int?): Boolean = a == null || a <= 0 || b == null || b <= 0 || a == b

    /** All members must agree, preventing a 0s/2s/4s transitive duration merge. */
    fun clusters(tracks: List<EchoTrack>): List<List<EchoTrack>> {
        val result = mutableListOf<MutableList<EchoTrack>>()
        tracks.sortedWith(compareBy<EchoTrack> { it.durationMs }.thenBy { it.id }).forEach { track ->
            if (EchoTrackVersionPolicy.key(track) != null) {
                val group = result.firstOrNull { cluster -> cluster.all { matches(it, track) } }
                if (group == null) result += mutableListOf(track) else group += track
            }
        }
        return result.filter { it.size > 1 }
    }
}
