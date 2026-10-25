package app.echo.android.playback

import app.echo.android.model.playback.EchoLinkPlaybackUri
import java.io.IOException
import kotlinx.coroutines.withTimeoutOrNull

/** A failed refresh must never silently reuse the rejected stream URL. */
internal suspend fun resolveEchoLinkStreamUri(
    mediaId: String,
    uri: String,
    resolver: EchoPlaybackStreamResolver?,
    timeoutMs: Long = EchoLinkStreamRefreshPolicy.ResolveTimeoutMs,
): String {
    if (!EchoLinkPlaybackUri.requiresStreamResolve(mediaId, uri) &&
        !EchoLinkPlaybackUri.isOneShotStreamUri(uri)) return uri
    // The application registers its resolver during startup.
    if (resolver == null) return uri
    val resolved = withTimeoutOrNull(timeoutMs) { resolver.resolvePlayUri(mediaId, uri) }
        ?.takeIf { it.isNotBlank() && EchoLinkPlaybackUri.trackIdFromPersistUri(it) == null }
    return resolved ?: throw IOException("PC ECHO stream is unavailable or timed out")
}
