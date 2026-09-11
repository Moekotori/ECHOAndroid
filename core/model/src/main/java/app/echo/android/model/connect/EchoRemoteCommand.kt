package app.echo.android.model.connect

sealed interface EchoRemoteCommand {
    data object PlayPause : EchoRemoteCommand
    data object Next : EchoRemoteCommand
    data object Previous : EchoRemoteCommand
    data object Stop : EchoRemoteCommand
    data class SeekTo(val positionMs: Long) : EchoRemoteCommand
    data class SetVolume(val volume: Float) : EchoRemoteCommand
    data class PlayTrackOnPc(val trackId: String) : EchoRemoteCommand
    data class HandoffToPc(val trackId: String, val positionMs: Long) : EchoRemoteCommand
    data class QueueReplace(
        val trackIds: List<String>,
        val startTrackId: String,
    ) : EchoRemoteCommand
    data class PlayRemoteStream(
        val streamUrl: String,
        val positionMs: Long,
        val track: EchoRemoteTrack,
    ) : EchoRemoteCommand
    data class QueueReplaceRemote(
        val items: List<EchoRemoteStreamItem>,
        val startTrackId: String,
    ) : EchoRemoteCommand
}
