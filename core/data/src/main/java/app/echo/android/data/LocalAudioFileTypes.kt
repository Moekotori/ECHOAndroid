package app.echo.android.data

import java.util.Locale

internal object LocalAudioFileTypes {
    fun mimeTypeForFileName(name: String): String? {
        val lower = name.lowercase(Locale.ROOT)
        return when {
            lower.endsWith(".flac") -> "audio/flac"
            lower.endsWith(".mp3") -> "audio/mpeg"
            lower.endsWith(".mp2") -> "audio/mpeg"
            lower.endsWith(".m4a") || lower.endsWith(".m4b") -> "audio/mp4"
            lower.endsWith(".aac") -> "audio/aac"
            lower.endsWith(".ogg") || lower.endsWith(".oga") -> "audio/ogg"
            lower.endsWith(".opus") -> "audio/opus"
            lower.endsWith(".wav") -> "audio/wav"
            lower.endsWith(".aiff") || lower.endsWith(".aif") || lower.endsWith(".aifc") -> "audio/aiff"
            lower.endsWith(".dsf") -> "audio/dsf"
            lower.endsWith(".dff") -> "audio/dff"
            lower.endsWith(".mka") -> "audio/x-matroska"
            lower.endsWith(".ac3") -> "audio/ac3"
            lower.endsWith(".eac3") || lower.endsWith(".ec3") -> "audio/eac3"
            lower.endsWith(".dts") -> "audio/vnd.dts"
            lower.endsWith(".amr") -> "audio/amr"
            else -> null
        }
    }

    fun isCueSheet(name: String, mimeType: String?): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        if (lower.endsWith(".cue")) return true
        val mime = mimeType?.lowercase(Locale.ROOT) ?: return false
        return mime == "application/x-cue" || mime == "text/x-cue"
    }

    fun isSupported(name: String, mimeType: String?): Boolean {
        if (isCueSheet(name, mimeType)) return false
        if (mimeType.isVideoMime()) return false
        if (name.lowercase(Locale.ROOT).endsWith(".ape")) return false
        return mimeType.isAudioMime() || mimeTypeForFileName(name) != null
    }

    fun resolvedMimeType(name: String, mimeType: String?): String? =
        mimeType?.takeIf { it.isAudioMime() } ?: mimeTypeForFileName(name)
}

internal fun String?.isAudioMime(): Boolean {
    val mime = this?.lowercase(Locale.ROOT) ?: return false
    return mime.startsWith("audio/") || mime in ApplicationAudioMimes
}

private val ApplicationAudioMimes = setOf(
    "application/ogg",
    "application/x-ogg",
    "application/flac",
    "application/x-flac",
)

internal fun String?.isVideoMime(): Boolean =
    this?.startsWith("video/", ignoreCase = true) == true
