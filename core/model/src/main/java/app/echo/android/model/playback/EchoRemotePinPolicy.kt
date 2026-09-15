package app.echo.android.model.playback

object EchoRemotePinPolicy {
    const val MaxTracks = 80

    fun canPin(sourceId: String?, mediaId: String, uri: String): Boolean {
        if (EchoLinkPlaybackUri.trackId(mediaId, uri) != null) return true
        val source = sourceId.orEmpty()
        return source == "subsonic" || source == "jellyfin" || source == "webdav" || source == "echo-link"
    }

    fun resourceKey(mediaId: String, uri: String): String {
        val trackId = EchoLinkPlaybackUri.trackId(mediaId, uri)
        if (trackId != null) return "echo-link-track:$trackId"
        return mediaId
    }

    fun merge(current: List<String>, incoming: List<String>): List<String> =
        (incoming + current).distinct().take(MaxTracks)
}
