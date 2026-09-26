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
    )
    private val FileTagExtensions = listOf("flac", "mp3", "mp2", "aac", "adts", "wave")
}
