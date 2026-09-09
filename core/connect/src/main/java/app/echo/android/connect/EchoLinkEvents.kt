package app.echo.android.connect

import app.echo.android.model.connect.EchoRemoteEndpoint
import app.echo.android.model.connect.EchoRemoteMessage
import app.echo.android.model.connect.EchoRemotePlaybackQueue
import app.echo.android.model.connect.EchoRemotePlaybackSnapshot
import app.echo.android.model.connect.EchoRemoteTrack
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

internal data class EchoLinkEventTicket(
    val ticket: String,
    val eventsUrl: HttpUrl,
)

internal fun interface EchoLinkEventSubscription {
    fun cancel()
}

internal fun parseEchoLinkEventData(
    raw: String,
    endpoint: EchoRemoteEndpoint,
): EchoRemoteMessage.StatusSnapshot? {
    val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
    val snapshotJson = json.optJSONObject("snapshot")
        ?: json.optJSONObject("playback")
        ?: json
    val playbackJson = snapshotJson.optJSONObject("playback") ?: snapshotJson
    return EchoRemoteMessage.StatusSnapshot(playbackJson.toPlaybackSnapshot(endpoint))
}

internal fun JSONObject.toPlaybackSnapshot(endpoint: EchoRemoteEndpoint): EchoRemotePlaybackSnapshot =
    EchoRemotePlaybackSnapshot(
        state = optText("state").toPlaybackState(),
        track = optJSONObject("track")?.toRemoteTrack(endpoint),
        positionMs = optLong("positionMs", 0L).coerceAtLeast(0L),
        durationMs = optDurationMs(),
        volume = optDouble("volume", 1.0).toFloat().coerceIn(0f, 1f),
        outputMode = optText("outputMode") ?: optText("output") ?: "PC ECHO",
        updatedAtEpochMs = optLong("updatedAtEpochMs", System.currentTimeMillis()),
        queue = optJSONObject("queue").toRemoteQueue(endpoint),
    )

internal fun JSONObject?.toRemoteQueue(endpoint: EchoRemoteEndpoint): EchoRemotePlaybackQueue {
    if (this == null) return EchoRemotePlaybackQueue()
    val itemsJson = optJSONArray("items") ?: optJSONArray("tracks") ?: JSONArray()
    val items = buildList {
        for (index in 0 until itemsJson.length()) {
            itemsJson.optJSONObject(index)?.toRemoteTrack(endpoint)?.let(::add)
        }
    }
    return EchoRemotePlaybackQueue(
        currentTrackId = optText("currentTrackId") ?: optText("currentId"),
        items = items,
    )
}

internal fun EchoRemoteEndpoint.resolveEventsUrl(raw: String?): HttpUrl? {
    val value = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
    value.toHttpUrlOrNull()?.let { return it }
    val relative = if (value.startsWith("/")) value else "/$value"
    return "http://placeholder.invalid$relative".toHttpUrlOrNull()?.let { parsed ->
        parsed.newBuilder()
            .scheme(scheme)
            .host(host)
            .port(port)
            .build()
    }
}
