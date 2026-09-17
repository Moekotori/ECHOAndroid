package app.echo.android.model.playback

data class EchoAudioRoutePlaybackOptions(
    val pauseOnDisconnect: Boolean = true,
    val resumeOnReconnect: Boolean = false,
)

object EchoAudioRoutePlaybackPolicy {
    fun isExternalOutput(kind: EchoOutputDeviceKind): Boolean = when (kind) {
        EchoOutputDeviceKind.Wired,
        EchoOutputDeviceKind.Bluetooth,
        EchoOutputDeviceKind.Usb,
        EchoOutputDeviceKind.Other,
        -> true
        EchoOutputDeviceKind.Speaker,
        EchoOutputDeviceKind.System,
        -> false
    }

    fun shouldPauseForBecomingNoisy(
        options: EchoAudioRoutePlaybackOptions,
        playWhenReady: Boolean,
    ): Boolean = options.pauseOnDisconnect && playWhenReady

    fun shouldPauseForRouteLoss(
        options: EchoAudioRoutePlaybackOptions,
        playWhenReady: Boolean,
        previous: EchoOutputDeviceKind,
        current: EchoOutputDeviceKind,
    ): Boolean = options.pauseOnDisconnect &&
        playWhenReady &&
        isExternalOutput(previous) &&
        !isExternalOutput(current)

    fun shouldResumeForRouteGain(
        options: EchoAudioRoutePlaybackOptions,
        pausedByDisconnect: Boolean,
        previous: EchoOutputDeviceKind,
        current: EchoOutputDeviceKind,
        canResume: Boolean,
    ): Boolean = options.resumeOnReconnect &&
        pausedByDisconnect &&
        canResume &&
        !isExternalOutput(previous) &&
        isExternalOutput(current)
}
