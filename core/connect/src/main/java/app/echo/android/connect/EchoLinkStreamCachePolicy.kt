package app.echo.android.connect

object EchoLinkStreamCachePolicy {
    const val MaxEntries = 16
    const val ExpirySkewMs = 15_000L

    fun cacheKey(endpointIdentity: String, trackId: String): String =
        "$endpointIdentity\n$trackId"

    fun shouldCache(expiresAtEpochMs: Long?): Boolean =
        expiresAtEpochMs != null && expiresAtEpochMs > 0L

    fun isFresh(
        expiresAtEpochMs: Long?,
        nowEpochMs: Long,
        skewMs: Long = ExpirySkewMs,
    ): Boolean {
        return shouldCache(expiresAtEpochMs) && requireNotNull(expiresAtEpochMs) - skewMs > nowEpochMs
    }
}
