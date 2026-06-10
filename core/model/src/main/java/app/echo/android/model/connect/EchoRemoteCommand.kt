package app.echo.android.model.connect

sealed interface EchoRemoteCommand {
    data object PlayPause : EchoRemoteCommand
    data object Next : EchoRemoteCommand
    data object Previous : EchoRemoteCommand
    data object Stop : EchoRemoteCommand
    data class SeekTo(val positionMs: Long) : EchoRemoteCommand
    data class SetVolume(val volume: Float) : EchoRemoteCommand
    data class PlayTrackOnPc(val trackId: String) : EchoRemoteCommand
}
