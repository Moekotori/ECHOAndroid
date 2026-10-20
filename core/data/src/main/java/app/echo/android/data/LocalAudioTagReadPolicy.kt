package app.echo.android.data

import java.util.Locale

/** Containers whose original tags take precedence over platform-decoded metadata. */
internal object LocalAudioTagReadPolicy {
    fun prefersFileTags(mimeType: String?, fileNameOrUri: String?): Boolean {
        if (LibraryWavTagPolicy.isWavContainer(mimeType, fileNameOrUri)) return true
        val mime = mimeType.orEmpty().substringBefore(';').trim().lowercase(Locale.ROOT)
        if (mime in FileTagMimeTypes) return true
        val name = fileNameOrUri.orEmpty().lowercase(Locale.ROOT).substringBefore('?')
        return FileTagExtensions.any { name.endsWith(".$it") || name.contains(".$it%") }
    }

    private val FileTagMimeTypes = setOf(
        "audio/flac", "audio/x-flac", "application/flac", "application/x-flac",
        "audio/mpeg", "audio/mp3", "audio/x-mp3", "audio/mp2", "audio/aac", "audio/aac-adts",
        "audio/mp4", "audio/x-m4a", "audio/m4a", "video/mp4", "application/mp4",
        "audio/ogg", "application/ogg", "audio/vorbis", "audio/opus",
        "audio/ape", "audio/x-ape", "audio/x-monkeys-audio", "audio/x-wavpack", "audio/wavpack",
        "audio/x-tta", "audio/tta", "audio/x-tak", "audio/tak",
    )
    private val FileTagExtensions = listOf("flac", "fla", "alac", "ape", "wv", "tta", "tak", "mp3", "mp2", "aac", "adts", "wave", "m4a", "m4b", "mp4", "ogg", "oga", "opus")
}
