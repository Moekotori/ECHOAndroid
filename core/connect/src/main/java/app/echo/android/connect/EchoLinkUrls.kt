package app.echo.android.connect

import app.echo.android.model.connect.EchoProtocolVersion
import app.echo.android.model.connect.EchoRemoteEndpoint
import okhttp3.HttpUrl

internal val EchoProtocolVersion.number: Int
    get() = major.coerceAtLeast(1)

internal fun echoLinkLibraryTracksUrl(
    endpoint: EchoRemoteEndpoint,
    query: String,
    page: Int,
    pageSize: Int,
): HttpUrl {
    val builder = HttpUrl.Builder()
        .scheme(endpoint.scheme)
        .host(endpoint.host)
        .port(endpoint.port)
        .addPathSegment("echo-link")
        .addPathSegment("v${endpoint.protocolVersion.number}")
        .addPathSegment("library")
        .addPathSegment("tracks")
        .addQueryParameter("page", page.coerceAtLeast(1).toString())
        .addQueryParameter("pageSize", pageSize.coerceIn(1, 500).toString())
    query.trim().takeIf { it.isNotEmpty() }?.let { builder.addQueryParameter("q", it) }
    return builder.build()
}

internal fun echoLinkPlaylistTracksUrl(
    endpoint: EchoRemoteEndpoint,
    playlistId: String,
    pageSize: Int,
): HttpUrl =
    HttpUrl.Builder()
        .scheme(endpoint.scheme)
        .host(endpoint.host)
        .port(endpoint.port)
        .addPathSegment("echo-link")
        .addPathSegment("v${endpoint.protocolVersion.number}")
        .addPathSegment("library")
        .addPathSegment("playlists")
        .addPathSegment(playlistId.trim())
        .addPathSegment("tracks")
        .addQueryParameter("page", "1")
        .addQueryParameter("pageSize", pageSize.coerceIn(1, 500).toString())
        .build()
