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
    return EchoRemoteMessage.StatusSnapshot(
        payload = playbackJson.toPlaybackSnapshot(endpoint),
        queueIncluded = playbackJson.has("queue"),
        trackArtworkIncluded = playbackJson.optJSONObject("track")?.let {
            it.has("artworkUrl") || it.has("coverUrl") || it.has("coverThumb") ||
                it.has("cover") || it.has("albumArtUrl") || it.has("albumArt")
        } ?: true,
        volumeControlIncluded = playbackJson.has("volumeControlEnabled"),
        outputIncluded = playbackJson.has("outputMode") || playbackJson.has("output"),
        playbackOrderIncluded = playbackJson.has("playbackOrder"),
    )
}

internal fun JSONObject.toPlaybackSnapshot(endpoint: EchoRemoteEndpoint): EchoRemotePlaybackSnapshot =
    EchoRemotePlaybackSnapshot(
        state = optText("state").toPlaybackState(),
        track = optJSONObject("track")?.toRemoteTrack(endpoint),
        positionMs = optLong("positionMs", 0L).coerceAtLeast(0L),
        durationMs = optDurationMs(),
        volume = optDouble("volume", 1.0).toFloat().coerceIn(0f, 1f),
        outputMode = optText("outputMode") ?: optJSONObject("output")?.optText("mode")
            ?: if (opt("output") is String) optText("output") ?: "PC ECHO" else "PC ECHO",
        updatedAtEpochMs = optLong("updatedAtEpochMs", System.currentTimeMillis()),
        queue = optJSONObject("queue")?.toRemoteQueue(endpoint) ?: EchoRemotePlaybackQueue(
            revision = optLong("queueRevision", -1).takeIf { it >= 0 },
            currentQueueId = optText("currentQueueId")),
        volumeControlEnabled = optBoolean("volumeControlEnabled", true),
        volumeLockedReason = optText("volumeLockedReason"),
        playbackOrder = app.echo.android.model.connect.EchoRemotePlaybackOrder.fromWireValue(optText("playbackOrder")),
        supportsAtomicPhoneQueue = optBoolean("supportsAtomicPhoneQueue", false),
        queueIdentityAvailable = has("currentQueueId") || optJSONObject("queue")?.has("currentQueueId") == true,
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
        totalCount = optInt("totalCount", items.size).coerceAtLeast(items.size),
        revision = optLong("revision", -1).takeIf { it >= 0 },
        currentQueueId = optText("currentQueueId"),
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
