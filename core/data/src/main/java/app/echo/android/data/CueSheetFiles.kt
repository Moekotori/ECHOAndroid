package app.echo.android.data

import app.echo.android.model.library.CueSheet
import app.echo.android.model.library.CueSheetPolicy
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Directory-local index: resolving many movements must not rescan every sibling filename. */
internal class CueAudioNameIndex(audioNames: Collection<String>) {
    private val exact = audioNames.distinct().groupBy { it.lowercase(java.util.Locale.ROOT) }
    private val replacements = audioNames.distinct().filter {
        it.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT) in setOf("flac", "fla", "ape", "wv")
    }.groupBy { it.substringBeforeLast('.').lowercase(java.util.Locale.ROOT) }

    fun match(reference: String?): String? {
        val wanted = CueSheetPolicy.localAudioName(reference) ?: return null
        exact[wanted.lowercase(java.util.Locale.ROOT)]?.let { hits ->
            return hits.firstOrNull { it == wanted } ?: hits.singleOrNull()
        }
        if (wanted.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT) !in setOf("wav", "wave", "cue")) return null
        return replacements[wanted.substringBeforeLast('.').lowercase(java.util.Locale.ROOT)]?.singleOrNull()
    }
}

internal fun CueSheet.forDirectoryAudio(audioNames: Collection<String>, cueName: String): Map<String, CueSheet> =
    forDirectoryAudio(CueAudioNameIndex(audioNames), cueName)

internal fun CueSheet.forDirectoryAudio(index: CueAudioNameIndex, cueName: String): Map<String, CueSheet> {
    val resolved = tracks.groupBy { track -> index.match(track.fileName ?: fileName ?: cueName) }
    return resolved.mapNotNull { (audio, tracks) ->
        audio?.let { it to copy(fileName = it, tracks = tracks.map { track -> track.copy(fileName = it) }) }
    }.toMap()
}

/** Resolve portable descendant paths without ever escaping the selected directory/tree. */
internal fun CueSheet.forLocalDirectories(): Map<String, CueSheet> {
    val groups = tracks.mapNotNull { track ->
        val reference = track.fileName ?: fileName
        if (reference == null) return@mapNotNull "" to track
        val path = reference.trim().replace('\\', '/')
        if (path.startsWith('/') || ':' in path || '\u0000' in path) return@mapNotNull null
        val parts = path.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty() || ".." in parts) return@mapNotNull null
        parts.dropLast(1).joinToString("/") to track.copy(fileName = parts.last())
    }.groupBy({ it.first }, { it.second })
    return groups.mapValues { (_, tracks) -> copy(fileName = null, tracks = tracks) }
}

internal fun readCueSheet(input: InputStream, referenceExists: ((String) -> Boolean)? = null): CueSheet? {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = input.read(buffer, 0, minOf(buffer.size, CueSheetPolicy.MaxCueBytes + 1 - output.size()))
        if (count < 0) break
        if (count == 0) return null
        output.write(buffer, 0, count)
        if (output.size() > CueSheetPolicy.MaxCueBytes) return null
    }
    return CueSheetParser.parse(output.toByteArray(), referenceExists)
}

/** Portable path relative to the sidecar folder; never relocate an absolute or parent reference. */
internal fun cueReference(reference: String): Pair<String, String>? {
    val path = reference.trim().replace('\\', '/')
    if (path.startsWith('/') || ':' in path || '\u0000' in path) return null
    val parts = path.split('/').filter { it.isNotEmpty() && it != "." }
    if (parts.isEmpty() || ".." in parts) return null
    return parts.dropLast(1).joinToString("/") to parts.last()
}
