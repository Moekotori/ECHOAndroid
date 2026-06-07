package app.echo.android.playback

import android.net.Uri

enum class EchoPlaybackState {
    Idle,
    Loading,
    Playing,
    Paused,
    Seeking,
    Buffering,
    Ended,
    Stopped,
    Error,
}

enum class EchoAudioErrorKind {
    PermissionDenied,
    FileMissing,
    UnsupportedFormat,
    DecodeFailure,
    OutputRouteFailure,
    AudioFocusLost,
    SystemInterrupted,
    Unknown,
}

enum class EchoRepeatMode {
    Off,
    One,
    All,
}

data class EchoTrackRef(
    val id: String,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUri: Uri? = null,
    val durationMs: Long = 0L,
)

data class EchoPlaybackError(
    val kind: EchoAudioErrorKind,
    val message: String,
    val recoverable: Boolean,
)

data class EchoPlaybackDiagnostics(
    val codec: String? = null,
    val sampleRateHz: Int? = null,
    val channelCount: Int? = null,
    val bitrate: Int? = null,
    val outputRoute: String = "system",
    val offloadActive: Boolean = false,
    val bufferedMs: Long = 0L,
    val requestToken: Long = 0L,
    val lastCommand: String? = null,
    val lastError: EchoPlaybackError? = null,
)

data class EchoPlaybackStatus(
    val state: EchoPlaybackState = EchoPlaybackState.Idle,
    val track: EchoTrackRef? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val repeatMode: EchoRepeatMode = EchoRepeatMode.Off,
    val shuffleEnabled: Boolean = false,
    val diagnostics: EchoPlaybackDiagnostics = EchoPlaybackDiagnostics(),
)

sealed interface EchoPlaybackCommand {
    data class PlayTrack(val track: EchoTrackRef, val startPositionMs: Long = 0L) : EchoPlaybackCommand
    data object PlayPause : EchoPlaybackCommand
    data object Pause : EchoPlaybackCommand
    data object Stop : EchoPlaybackCommand
    data object Next : EchoPlaybackCommand
    data object Previous : EchoPlaybackCommand
    data class SeekTo(val positionMs: Long) : EchoPlaybackCommand
}
