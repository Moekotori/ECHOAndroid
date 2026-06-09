package app.echo.android.model.playback

data class PlaybackDiagnosticsState(
    val diagnostics: EchoPlaybackDiagnostics = EchoPlaybackDiagnostics(),
    val lastError: EchoPlaybackError? = null,
)
