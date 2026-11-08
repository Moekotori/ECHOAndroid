package app.echo.android.playback

internal data class SubsonicTranscodeFallback(val uri: String, val positionMs: Long, val playWhenReady: Boolean)

internal fun subsonicTranscodeFallback(uri: String, positionMs: Long, playWhenReady: Boolean): SubsonicTranscodeFallback? =
    subsonicUnsupportedFormatFallbackUrl(uri)?.let {
        SubsonicTranscodeFallback(it, positionMs.coerceAtLeast(0), playWhenReady)
    }
