package app.echo.android.data

import app.echo.android.model.library.CueSheet
import app.echo.android.model.library.CueSheetPolicy
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Resolve once against the complete directory, so an exact source wins over extension fallback. */
internal fun CueSheet.forDirectoryAudio(audioNames: Collection<String>, cueName: String): Map<String, CueSheet> {
    val resolved = tracks.groupBy { track ->
        CueSheetPolicy.matchAudioName(track.fileName ?: fileName ?: cueName, audioNames)
    }
    return resolved.mapNotNull { (audio, tracks) ->
        audio?.let { it to copy(fileName = it, tracks = tracks.map { track -> track.copy(fileName = it) }) }
    }.toMap()
}

internal fun readCueSheet(input: InputStream): CueSheet? {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = input.read(buffer, 0, minOf(buffer.size, CueSheetPolicy.MaxCueBytes + 1 - output.size()))
        if (count < 0) break
        if (count == 0) return null
        output.write(buffer, 0, count)
        if (output.size() > CueSheetPolicy.MaxCueBytes) return null
    }
    return CueSheetParser.parse(output.toByteArray())
}
