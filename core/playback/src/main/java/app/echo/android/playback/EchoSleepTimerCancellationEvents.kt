package app.echo.android.playback

import androidx.media3.common.Player
import app.echo.android.model.playback.EchoSleepTimerCancellationReason
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** No replay: returning to the foreground must not replay an old cancellation notice. */
internal class EchoSleepTimerCancellationEvents {
    private val events = MutableSharedFlow<EchoSleepTimerCancellationReason>(
        extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val notices = events.asSharedFlow()

    fun cancelIfFinishing(finishing: Boolean, reason: EchoSleepTimerCancellationReason, cancel: () -> Unit) {
        if (!finishing) return
        cancel()
        events.tryEmit(reason)
    }
}

internal fun sleepTimerCancellationForCommand(command: Int): EchoSleepTimerCancellationReason = when (command) {
    Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_PREVIOUS,
    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
    Player.COMMAND_SEEK_TO_MEDIA_ITEM, Player.COMMAND_SET_MEDIA_ITEM -> EchoSleepTimerCancellationReason.TrackChanged
    Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
    Player.COMMAND_SEEK_BACK, Player.COMMAND_SEEK_FORWARD -> EchoSleepTimerCancellationReason.Seeked
    Player.COMMAND_CHANGE_MEDIA_ITEMS -> EchoSleepTimerCancellationReason.QueueChanged
    else -> EchoSleepTimerCancellationReason.PlaybackChanged
}
