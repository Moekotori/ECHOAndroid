package app.echo.android.connect

import app.echo.android.model.connect.EchoRemoteCommand
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
    }
    return json
}
