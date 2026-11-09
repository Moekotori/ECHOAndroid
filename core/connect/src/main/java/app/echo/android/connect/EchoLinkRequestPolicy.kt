package app.echo.android.connect

import app.echo.android.model.connect.EchoRemoteEndpoint
import okhttp3.HttpUrl

object EchoLinkRequestPolicy {
    fun shouldApplyResolvedPlay(requestGeneration: Long, latestGeneration: Long): Boolean =
        requestGeneration > 0L && requestGeneration == latestGeneration

    fun endpointIdentity(endpoint: EchoRemoteEndpoint): String =
        "${endpoint.scheme}://${endpoint.host}:${endpoint.port}|${endpoint.token}"

    // A playback queue survives reconnect and re-pairing. Its origin contains no credentials.
    internal fun playbackSourceKey(endpoint: EchoRemoteEndpoint): String =
        HttpUrl.Builder().scheme(endpoint.scheme).host(endpoint.host).port(endpoint.port).build().toString()

    internal fun matchesPlaybackSource(endpoint: EchoRemoteEndpoint, sourceKey: String?): Boolean =
        sourceKey == null || sourceKey == playbackSourceKey(endpoint)

    fun isSameEndpoint(current: EchoRemoteEndpoint?, target: EchoRemoteEndpoint): Boolean =
        current != null && endpointIdentity(current) == endpointIdentity(target)

    fun shouldPersistEndpoint(endpoint: EchoRemoteEndpoint): Boolean =
        !endpoint.needsV2PairExchange

    fun shouldFailPairingAfterAttempts(attempts: Int, maxAttempts: Int = 2): Boolean =
        attempts >= maxAttempts

    fun shouldClearPersistedPairingSecret(
        connectionFailed: Boolean,
        needsV2PairExchange: Boolean,
    ): Boolean = connectionFailed && needsV2PairExchange

    fun shouldDisconnectOnCommandFailure(statusCode: Int?): Boolean =
        statusCode == 401 || statusCode == 403

    fun shouldMarkAlbumsUnavailable(collectionNotFound: Boolean): Boolean = collectionNotFound

    fun shouldMarkFoldersUnavailable(path: String, notFound: Boolean): Boolean =
        notFound && path.trim().trim('/').isEmpty()
}
