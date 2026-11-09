package app.echo.android.model.playback

import app.echo.android.model.library.LibrarySource
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object EchoLinkPlaybackUri {
    const val MediaIdPrefix = "echo-link:"
    const val PersistUriPrefix = "echo-link://track/"
    const val OneShotStreamPath = "/echo-link/media/"
    private const val ScopedMediaIdPrefix = "echo-link-pc:"
    private const val SourceParameter = "?pc="

    fun mediaId(trackId: String, sourceKey: String? = null): String =
        if (sourceKey == null) MediaIdPrefix + trackId
        else "$ScopedMediaIdPrefix${encodeSegment(sourceKey)}/${encodeSegment(trackId)}"

    fun persistUri(trackId: String, sourceKey: String? = null): String =
        PersistUriPrefix + encodeSegment(trackId) + sourceKey?.let { SourceParameter + encodeSegment(it) }.orEmpty()

    fun trackIdFromMediaId(mediaId: String): String? {
        if (mediaId.startsWith(ScopedMediaIdPrefix)) {
            return decodeSegment(mediaId.substringAfter('/', "")).takeIf { it.isNotBlank() }
        }
        if (!mediaId.startsWith(MediaIdPrefix)) return null
        return mediaId.removePrefix(MediaIdPrefix).takeIf { it.isNotBlank() }
    }

    fun trackIdFromPersistUri(uri: String): String? {
        if (!uri.startsWith(PersistUriPrefix)) return null
        return decodeSegment(uri.removePrefix(PersistUriPrefix).substringBefore(SourceParameter)).takeIf { it.isNotBlank() }
    }

    fun sourceKey(mediaId: String, uri: String): String? = when {
        mediaId.startsWith(ScopedMediaIdPrefix) ->
            decodeSegment(mediaId.removePrefix(ScopedMediaIdPrefix).substringBefore('/')).takeIf { it.isNotBlank() }
        uri.startsWith(PersistUriPrefix) ->
            decodeSegment(uri.substringAfter(SourceParameter, "")).takeIf { it.isNotBlank() }
        else -> null
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
        if (trackId != null) return persistUri(trackId, sourceKey(mediaId, playUri))
        return playUri
    }

    fun requiresStreamResolve(mediaId: String, uri: String): Boolean {
        return trackId(mediaId, uri) != null && (isOneShotStreamUri(uri) || trackIdFromPersistUri(uri) != null || trackIdFromMediaId(mediaId) != null)
    }

    fun playUriNeedsResolve(playUri: String, playerUnavailable: Boolean): Boolean {
        return trackIdFromPersistUri(playUri) != null || isOneShotStreamUri(playUri) && playerUnavailable
    }

    fun isPcLibrarySource(sourceId: String?, mediaId: String?, uri: String): Boolean {
        return sourceId == LibrarySource.EchoLink.id || trackId(mediaId.orEmpty(), uri) != null
    }

    private fun encodeSegment(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private fun decodeSegment(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
}
