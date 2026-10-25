package app.echo.android.model.playback

/** Why a timer waiting for the current song was cancelled, rather than normally completed. */
enum class EchoSleepTimerCancellationReason {
    PlaybackChanged, TrackChanged, Seeked, QueueChanged, AudioFocusLost, OutputChanged, PlaybackError,
}
