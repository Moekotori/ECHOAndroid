package app.echo.android.data

import java.util.Locale

internal object LocalAudioFileTypes {
    fun mimeTypeForFileName(name: String): String? {
        val lower = name.lowercase(Locale.ROOT)
        return when {
            lower.endsWith(".flac") -> "audio/flac"
            lower.endsWith(".mp3") -> "audio/mpeg"
            lower.endsWith(".mp2") -> "audio/mpeg"
            lower.endsWith(".m4a") || lower.endsWith(".m4b") || lower.endsWith(".mp4") -> "audio/mp4"
            lower.endsWith(".aac") -> "audio/aac"
            lower.endsWith(".ogg") || lower.endsWith(".oga") -> "audio/ogg"
            lower.endsWith(".opus") -> "audio/opus"
            lower.endsWith(".wav") -> "audio/wav"
            lower.endsWith(".aiff") || lower.endsWith(".aif") || lower.endsWith(".aifc") -> "audio/aiff"
            lower.endsWith(".ape") -> "audio/ape"
            lower.endsWith(".dsf") -> "audio/dsf"
            lower.endsWith(".dff") -> "audio/dff"
            lower.endsWith(".mka") -> "audio/x-matroska"
            else -> null
        }
    }

    fun isSupported(name: String, mimeType: String?): Boolean =
        mimeType.isAudioMime() || mimeTypeForFileName(name) != null

    fun resolvedMimeType(name: String, mimeType: String?): String? =
        mimeType?.takeIf { it.isAudioMime() } ?: mimeTypeForFileName(name)
}

internal fun String?.isAudioMime(): Boolean =
    this?.startsWith("audio/", ignoreCase = true) == true
