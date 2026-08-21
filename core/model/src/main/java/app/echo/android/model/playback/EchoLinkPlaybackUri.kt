package app.echo.android.model.playback

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object EchoLinkPlaybackUri {
    const val MediaIdPrefix = "echo-link:"
    const val PersistUriPrefix = "echo-link://track/"
    const val OneShotStreamPath = "/echo-link/media/"

    fun mediaId(trackId: String): String = MediaIdPrefix + trackId

    fun persistUri(trackId: String): String =
        PersistUriPrefix + encodeSegment(trackId)

    fun trackIdFromMediaId(mediaId: String): String? {
        if (!mediaId.startsWith(MediaIdPrefix)) return null
        return mediaId.removePrefix(MediaIdPrefix).takeIf { it.isNotBlank() }
    }

    fun trackIdFromPersistUri(uri: String): String? {
        if (!uri.startsWith(PersistUriPrefix)) return null
        return decodeSegment(uri.removePrefix(PersistUriPrefix)).takeIf { it.isNotBlank() }
    }

    fun trackId(mediaId: String, uri: String): String? =
        trackIdFromMediaId(mediaId) ?: trackIdFromPersistUri(uri)

    fun isOneShotStreamUri(uri: String): Boolean {
        val normalized = uri.lowercase()
        val isHttp = normalized.startsWith("http://") || normalized.startsWith("https://")
        return isHttp && normalized.contains(OneShotStreamPath)
    }

    fun persistableUri(mediaId: String, playUri: String): String {
        val trackId = trackId(mediaId, playUri)
        if (trackId != null) return persistUri(trackId)
        return playUri
    }

    fun requiresStreamResolve(mediaId: String, uri: String): Boolean {
        if (trackId(mediaId, uri) == null) return false
        return isOneShotStreamUri(uri) || trackIdFromPersistUri(uri) != null || trackIdFromMediaId(mediaId) != null
    }

    private fun encodeSegment(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

    private fun decodeSegment(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8)
}
