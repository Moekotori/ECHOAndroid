package app.echo.android.data

import app.echo.android.model.library.CueSheetPolicy
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.LibrarySource
import java.util.Locale

/** Sharing always refers to the complete original, including for a CUE selection. */
object OriginalAudioSharePolicy {
    const val MaxTracks = OriginalAudioShareStore.MaxTracks
    fun supports(track: EchoTrack): Boolean =
        (track.source.isLocalAudioFile || track.source == LibrarySource.Unknown && track.id.startsWith("incoming:")) &&
            track.uri.substringBefore(':').lowercase(Locale.ROOT) in setOf("content", "file")

    fun originalUri(track: EchoTrack): String = CueSheetPolicy.playbackUri(track.uri)

    internal fun safeName(value: String): String {
        val name = value.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[\\p{Cntrl}<>:\"/\\\\|?*]"), "_").trim().trim('.').ifBlank { "audio" }
        if (name.length <= 160 && name.toByteArray(Charsets.UTF_8).size <= 200) return name
        val extension = name.substringAfterLast('.', "").takeIf { it.matches(Regex("[A-Za-z0-9]{1,10}")) }
        val suffix = extension?.let { ".$it" }.orEmpty()
        var stem = (if (extension == null) name else name.substringBeforeLast('.')).take(160 - suffix.length)
        while (stem.toByteArray(Charsets.UTF_8).size > 200 - suffix.length) stem = stem.dropLast(1)
        if (stem.lastOrNull()?.isHighSurrogate() == true) stem = stem.dropLast(1)
        return stem + suffix
    }

    internal fun uniqueName(value: String, used: MutableSet<String>): String {
        val name = safeName(value)
        val stem = name.substringBeforeLast('.', name)
        val suffix = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var candidate = name
        var number = 2
        while (!used.add(candidate.lowercase(Locale.ROOT))) candidate = "$stem (${number++})$suffix"
        return candidate
    }
}

/** A shared stem prevents Song.flac + Song.mp3 from receiving ambiguous Song.lrc covers/lyrics. */
internal class OriginalAudioShareNames {
    private val stems = HashSet<String>()
    private val files = hashSetOf("manifest.json")

    fun audio(wanted: String): String {
        val safe = OriginalAudioSharePolicy.safeName(wanted)
        val stem = safe.substringBeforeLast('.', safe)
        val suffix = safe.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var base = stem
        var number = 2
        while (base.lowercase(Locale.ROOT) in stems || (base + suffix).lowercase(Locale.ROOT) in files) base = "$stem (${number++})"
        stems += base.lowercase(Locale.ROOT)
        files += (base + suffix).lowercase(Locale.ROOT)
        return base + suffix
    }

    fun attachment(wanted: String): String = OriginalAudioSharePolicy.uniqueName(wanted, files)
}
