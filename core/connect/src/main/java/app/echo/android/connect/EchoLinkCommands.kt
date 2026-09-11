package app.echo.android.connect

import app.echo.android.model.connect.EchoRemoteAudioFormat
import app.echo.android.model.connect.EchoRemoteCommand
import app.echo.android.model.connect.EchoRemoteStreamItem
import app.echo.android.model.connect.EchoRemoteTrack
import org.json.JSONArray
import org.json.JSONObject

internal fun EchoRemoteCommand.toJson(): JSONObject {
    val json = JSONObject()
    when (this) {
        EchoRemoteCommand.PlayPause -> json.put("command", "playPause")
        EchoRemoteCommand.Next -> json.put("command", "next")
        EchoRemoteCommand.Previous -> json.put("command", "previous")
        EchoRemoteCommand.Stop -> json.put("command", "stop")
        is EchoRemoteCommand.SeekTo -> {
            json.put("command", "seekTo")
            json.put("positionMs", positionMs)
        }
        is EchoRemoteCommand.SetVolume -> {
            json.put("command", "setVolume")
            json.put("volume", volume.coerceIn(0f, 1f))
        }
        is EchoRemoteCommand.PlayTrackOnPc -> {
            json.put("command", "playTrack")
            json.put("trackId", trackId)
            json.put("output", "pc")
        }
        is EchoRemoteCommand.HandoffToPc -> {
            json.put("command", "handoff")
            json.put("trackId", trackId)
            json.put("positionMs", positionMs)
            json.put("target", "pc")
        }
        is EchoRemoteCommand.QueueReplace -> {
            json.put("command", "queueReplace")
            json.put("trackIds", JSONArray(trackIds))
            json.put("startTrackId", startTrackId)
            json.put("output", "pc")
        }
        is EchoRemoteCommand.PlayRemoteStream -> {
            json.put("command", "playRemoteStream")
            json.put("target", "pc")
            json.put("quality", EchoLinkCastPolicy.QualityOriginal)
            json.put("positionMs", positionMs.coerceAtLeast(0L))
            json.put("streamUrl", streamUrl)
            json.put("track", track.toCommandJson())
            audio?.toCommandJson()?.let { json.put("audio", it) }
        }
        is EchoRemoteCommand.QueueReplaceRemote -> {
            json.put("command", "queueReplaceRemote")
            json.put("target", "pc")
            json.put("startTrackId", startTrackId)
            json.put("items", JSONArray().also { array ->
                items.forEach { item -> array.put(item.toCommandJson()) }
            })
        }
    }
    return json
}

private fun EchoRemoteTrack.toCommandJson(): JSONObject = JSONObject()
    .put("id", id.orEmpty())
    .put("title", title)
    .put("artist", artist)
    .put("album", album.orEmpty())
    .put("durationMs", durationMs)
    .putOpt("artworkUrl", artworkUrl)

private fun EchoRemoteStreamItem.toCommandJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("streamUrl", streamUrl)
    .put("title", title)
    .put("artist", artist)
    .put("album", album.orEmpty())
    .put("durationMs", durationMs)
    .putOpt("artworkUrl", artworkUrl)
    .also { json -> audio?.toCommandJson()?.let { json.put("audio", it) } }

private fun EchoRemoteAudioFormat.toCommandJson(): JSONObject {
    val json = JSONObject()
    codec?.takeIf { it.isNotBlank() }?.let { json.put("codec", it) }
    mimeType?.takeIf { it.isNotBlank() }?.let { json.put("mimeType", it) }
    sampleRateHz?.takeIf { it > 0 }?.let { json.put("sampleRateHz", it) }
    bitDepth?.takeIf { it > 0 }?.let { json.put("bitDepth", it) }
    channelCount?.takeIf { it > 0 }?.let { json.put("channelCount", it) }
    lossless?.let { json.put("lossless", it) }
    return json
}
