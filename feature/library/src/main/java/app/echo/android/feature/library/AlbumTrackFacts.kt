package app.echo.android.feature.library

import app.echo.android.model.library.EchoTrack
import java.util.Locale

/** Everything the album page derives from the loaded tracks, computed in one pass. */
internal data class AlbumTrackFacts(
    val multiDisc: Boolean,
    val hasCue: Boolean,
    val genre: String?,
    val spec: AlbumSpec,
    val discs: Map<Int, AlbumDiscTotals>,
)

internal data class AlbumDiscTotals(val trackCount: Int, val durationMs: Long)

/** [formatKey] and [sampleRateHz] are null when the file does not report them. */
internal data class TrackSpec(val formatKey: String?, val sampleRateHz: Int?)

internal data class AlbumSpec(
    /** Distinct formats in track order, e.g. `FLAC`. */
    val formats: List<String>,
    /** Distinct reported sample rates, ascending. */
    val sampleRatesHz: List<Int>,
    /** A lossless file at 88.2 kHz or above. */
    val hiRes: Boolean,
    /** Null unless every loaded track reports a size. */
    val sizeBytes: Long?,
    /** The spec shared by more than half of the tracks; null when the album is evenly mixed. */
    val dominant: TrackSpec?,
    /** Tracks per format and sample rate, largest group first. */
    val groups: List<AlbumSpecGroup>,
)

internal data class AlbumSpecGroup(val spec: TrackSpec, val trackCount: Int)

private const val HI_RES_MIN_HZ = 88_200

internal fun albumTrackFacts(tracks: List<EchoTrack>): AlbumTrackFacts {
    val discTotals = LinkedHashMap<Int, AlbumDiscTotals>()
    val genres = LinkedHashMap<String, Int>()
    for (track in tracks) {
        val disc = track.albumDiscNumber()
        val previous = discTotals[disc]
        val duration = track.durationMs.coerceAtLeast(0L)
        discTotals[disc] = AlbumDiscTotals(
            trackCount = (previous?.trackCount ?: 0) + 1,
            durationMs = (previous?.durationMs ?: 0L) + duration,
        )
        track.genre?.trim()?.takeIf { it.isNotEmpty() }?.let { genres[it] = (genres[it] ?: 0) + 1 }
    }
    return AlbumTrackFacts(
        multiDisc = albumHasMultipleDiscs(tracks),
        hasCue = albumHasCueMovements(tracks),
        genre = genres.maxByOrNull { it.value }?.key,
        spec = albumSpec(tracks),
        discs = discTotals,
    )
}

internal fun albumSpec(tracks: List<EchoTrack>): AlbumSpec {
    val groupCounts = LinkedHashMap<TrackSpec, Int>()
    for (track in tracks) {
        val spec = track.trackSpec()
        if (spec.formatKey == null && spec.sampleRateHz == null) continue
        groupCounts[spec] = (groupCounts[spec] ?: 0) + 1
    }
    val groups = groupCounts.map { AlbumSpecGroup(it.key, it.value) }.sortedByDescending { it.trackCount }
    val dominant = groups.firstOrNull()?.takeIf { it.trackCount * 2 > tracks.size }?.spec
    return AlbumSpec(
        formats = tracks.mapNotNull { formatMimeType(it.mimeType) }.distinct(),
        sampleRatesHz = tracks.mapNotNull { it.sampleRateHz?.takeIf { hz -> hz > 0 } }.distinct().sorted(),
        hiRes = tracks.any { track ->
            val hz = track.sampleRateHz ?: 0
            hz >= HI_RES_MIN_HZ && isLosslessFormat(formatMimeType(track.mimeType))
        },
        sizeBytes = tracks.takeIf { list -> list.isNotEmpty() && list.all { it.sizeBytes > 0L } }
            ?.sumOf { it.sizeBytes },
        dominant = dominant,
        groups = groups,
    )
}

internal fun EchoTrack.trackSpec(): TrackSpec =
    TrackSpec(formatMimeType(mimeType), sampleRateHz?.takeIf { it > 0 })

/**
 * Short label such as `MP3 44.1` for a track whose format or sample rate differs from the rest of the album,
 * or null when it matches or the album has no dominant spec. A missing field never counts as different.
 */
internal fun trackSpecOutlier(track: EchoTrack, dominant: TrackSpec?): String? {
    if (dominant == null) return null
    val own = track.trackSpec()
    val formatDiffers = own.formatKey != null && dominant.formatKey != null && own.formatKey != dominant.formatKey
    val rateDiffers = own.sampleRateHz != null && dominant.sampleRateHz != null && own.sampleRateHz != dominant.sampleRateHz
    if (!formatDiffers && !rateDiffers) return null
    return listOfNotNull(own.formatKey, own.sampleRateHz?.let(::formatSampleRateNumber)).joinToString(" ")
}

internal fun isLosslessFormat(label: String?): Boolean {
    val upper = label?.uppercase(Locale.ROOT) ?: return false
    return LosslessMarkers.any { upper.contains(it) }
}

private val LosslessMarkers = listOf("FLAC", "ALAC", "WAV", "AIFF", "WAVPACK", "APE", "DSD", "DSF", "DFF")

internal fun formatMimeType(mimeType: String?): String? {
    val raw = mimeType?.substringAfter("audio/", missingDelimiterValue = mimeType)
        ?.substringBefore(";")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: return null
    return when {
        raw.equals("mpeg", ignoreCase = true) -> "MP3"
        raw.equals("mp4", ignoreCase = true) || raw.equals("mp4a-latm", ignoreCase = true) -> "AAC"
        raw.equals("x-wav", ignoreCase = true) || raw.equals("wav", ignoreCase = true) -> "WAV"
        raw.equals("x-flac", ignoreCase = true) || raw.equals("flac", ignoreCase = true) -> "FLAC"
        else -> raw.uppercase(Locale.ROOT)
    }
}

/** `44.1 kHz`, or `44.1–96 kHz` for a range. */
internal fun formatSampleRates(sampleRates: List<Int>): String? {
    if (sampleRates.isEmpty()) return null
    val first = sampleRates.min()
    val last = sampleRates.max()
    return if (first == last) formatSampleRate(first) else "${formatSampleRateNumber(first)}–${formatSampleRate(last)}"
}

internal fun formatSampleRate(hz: Int): String = "${formatSampleRateNumber(hz)} kHz"

internal fun formatSampleRateNumber(hz: Int): String =
    if (hz % 1000 == 0) (hz / 1000).toString() else String.format(Locale.ROOT, "%.1f", hz / 1000.0)

internal fun formatFileSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    return if (value >= 100.0) {
        "${value.toInt()} ${units[unitIndex]}"
    } else {
        String.format(Locale.ROOT, "%.1f %s", value, units[unitIndex])
    }
}
