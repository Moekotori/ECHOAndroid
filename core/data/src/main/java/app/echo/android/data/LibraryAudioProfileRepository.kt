package app.echo.android.data

import app.echo.android.model.library.LibraryAudioDistribution
import app.echo.android.model.library.LibraryAudioProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Fixed format and rate buckets keep the aggregation bounded. */
internal const val LibraryAudioProfileQuery = """
    SELECT
        CASE
            WHEN mime IN ('audio/flac', 'audio/x-flac') OR name LIKE '%.flac' THEN 'FLAC'
            WHEN mime IN ('audio/alac', 'audio/x-alac') THEN 'ALAC'
            WHEN mime IN ('audio/ape', 'audio/x-ape', 'audio/x-monkeys-audio') OR name LIKE '%.ape' THEN 'APE'
            WHEN mime IN ('audio/tta', 'audio/x-tta') OR name LIKE '%.tta' THEN 'TTA'
            WHEN mime IN ('audio/tak', 'audio/x-tak') OR name LIKE '%.tak' THEN 'TAK'
            WHEN mime IN ('audio/w64', 'audio/x-w64') OR name LIKE '%.w64' THEN 'W64'
            WHEN mime IN ('audio/dsd', 'audio/x-dsd', 'audio/dsf', 'audio/x-dsf', 'audio/dff', 'audio/x-dff') OR name LIKE '%.dsf' OR name LIKE '%.dff' THEN 'DSD'
            WHEN name LIKE '%.mp2' THEN 'MP2'
            WHEN mime IN ('audio/mpeg', 'audio/mp3', 'audio/x-mp3') OR name LIKE '%.mp3' THEN 'MP3'
            WHEN mime IN ('audio/aac', 'audio/aac-adts', 'audio/x-aac') OR name LIKE '%.aac' THEN 'AAC'
            WHEN mime IN ('audio/opus', 'audio/x-opus') OR name LIKE '%.opus' THEN 'OPUS'
            WHEN mime IN ('audio/vorbis', 'audio/x-vorbis', 'audio/ogg; codecs=vorbis') THEN 'VORBIS'
            WHEN mime IN ('audio/wav', 'audio/x-wav', 'audio/wave', 'audio/vnd.wave') OR name LIKE '%.wav' THEN 'WAV'
            WHEN mime IN ('audio/aiff', 'audio/x-aiff') OR name LIKE '%.aif' OR name LIKE '%.aiff' OR name LIKE '%.aifc' THEN 'AIFF'
            WHEN mime IN ('audio/wavpack', 'audio/x-wavpack') OR name LIKE '%.wv' THEN 'WAVPACK'
            WHEN mime IN ('audio/mp4', 'audio/x-m4a') OR name LIKE '%.m4a' OR name LIKE '%.mp4' THEN 'M4A'
            WHEN mime IN ('audio/ogg', 'application/ogg', 'audio/x-ogg') OR name LIKE '%.ogg' OR name LIKE '%.oga' THEN 'OGG'
            WHEN mime IN ('audio/x-ms-wma', 'audio/wma') OR name LIKE '%.wma' THEN 'WMA'
            WHEN mime = 'audio/ac3' OR name LIKE '%.ac3' THEN 'AC3'
            WHEN mime = 'audio/eac3' OR name LIKE '%.eac3' OR name LIKE '%.ec3' THEN 'EAC3'
            WHEN mime IN ('audio/vnd.dts', 'audio/vnd.dts.hd') OR name LIKE '%.dts' THEN 'DTS'
            WHEN mime IN ('audio/amr', 'audio/amr-wb') OR name LIKE '%.amr' THEN 'AMR'
            WHEN mime = 'audio/x-matroska' OR name LIKE '%.mka' THEN 'MKA'
            ELSE 'UNKNOWN'
        END AS format,
        CASE
            WHEN sampleRateHz IS NULL OR sampleRateHz <= 0 THEN 'UNKNOWN'
            WHEN sampleRateHz IN (8000, 16000, 22050, 32000, 44100, 48000, 88200, 96000, 176400, 192000, 352800, 384000) THEN CAST(sampleRateHz AS TEXT)
            WHEN sampleRateHz >= 2822400 AND sampleRateHz % 2822400 = 0 THEN 'DSD'
            ELSE 'OTHER'
        END AS rate,
        COUNT(*) AS trackCount,
        SUM(CASE WHEN sampleRateHz > 48000 AND sampleRateHz < 2822400 THEN 1 ELSE 0 END) AS highSampleRateCount
    FROM (
        SELECT LOWER(TRIM(COALESCE(mimeType, ''))) AS mime,
            LOWER(COALESCE(NULLIF(fileName, ''), contentUri)) AS name, sampleRateHz
        FROM library_tracks WHERE source = 'mediastore' OR source = 'saf'
    )
    GROUP BY format, rate
"""

data class LibraryAudioProfileBucket(
    val format: String,
    val rate: String,
    val trackCount: Int,
    val highSampleRateCount: Int,
)

suspend fun EchoLibraryRepository.libraryAudioProfile(): LibraryAudioProfile = withContext(Dispatchers.IO) {
    audioProfileFromBuckets(database.trackDao().localAudioProfile())
}

internal fun audioProfileFromBuckets(buckets: List<LibraryAudioProfileBucket>): LibraryAudioProfile {
    val formats = mutableMapOf<String, Int>()
    val rates = mutableMapOf<String, Int>()
    var total = 0
    var lossless = 0
    var lossy = 0
    var dsd = 0
    var highRate = 0
    for (bucket in buckets) {
        total += bucket.trackCount
        highRate += if (bucket.format == "DSD") 0 else bucket.highSampleRateCount
        formats[bucket.format] = (formats[bucket.format] ?: 0) + bucket.trackCount
        val rate = when {
            bucket.format == "DSD" && bucket.rate != "UNKNOWN" -> "DSD"
            bucket.rate == "DSD" -> "OTHER"
            else -> bucket.rate
        }
        rates[rate] = (rates[rate] ?: 0) + bucket.trackCount
        when (bucket.format) {
            "FLAC", "ALAC", "APE", "TTA", "TAK" -> lossless += bucket.trackCount
            "MP2", "MP3", "AAC", "OPUS", "VORBIS", "AC3", "EAC3", "AMR" -> lossy += bucket.trackCount
            "DSD" -> dsd += bucket.trackCount
        }
    }
    return LibraryAudioProfile(
        trackCount = total, losslessCount = lossless, lossyCount = lossy, dsdCount = dsd,
        unconfirmedCount = total - lossless - lossy - dsd, highSampleRateCount = highRate,
        formats = formats.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { LibraryAudioDistribution(it.key, it.value) },
        sampleRates = rates.entries.sortedWith(compareBy<Map.Entry<String, Int>> { it.key.toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it.key })
            .map { LibraryAudioDistribution(it.key, it.value) },
    )
}
