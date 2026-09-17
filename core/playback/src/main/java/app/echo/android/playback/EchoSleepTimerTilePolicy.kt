package app.echo.android.playback

import app.echo.android.model.playback.EchoSleepTimerMode

enum class EchoSleepTimerTileStep {
    Minutes15,
    Minutes30,
    Minutes60,
    EndOfTrack,
    Off,
}

object EchoSleepTimerTilePolicy {
    fun next(
        mode: EchoSleepTimerMode,
        requestedMinutes: Int?,
    ): EchoSleepTimerTileStep = when (mode) {
        EchoSleepTimerMode.Off -> EchoSleepTimerTileStep.Minutes15
        EchoSleepTimerMode.EndOfTrack -> EchoSleepTimerTileStep.Off
        EchoSleepTimerMode.Timed -> when (requestedMinutes) {
            15 -> EchoSleepTimerTileStep.Minutes30
            30 -> EchoSleepTimerTileStep.Minutes60
            else -> EchoSleepTimerTileStep.EndOfTrack
        }
    }
}
