package app.echo.android.playback

import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** The session exposes the user's repeat choice. The engine traverses the scheduler's actual plan. */
@UnstableApi
internal class EchoQueueSessionPlayer(
    player: Player,
    private val queue: NextUpQueueController,
    private val onRepeatChanged: () -> Unit,
) : ForwardingSimpleBasePlayer(player) {
    override fun getState(): State = super.getState().buildUpon().setRepeatMode(queue.requestedRepeatMode).build()

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        if (repeatMode != queue.requestedRepeatMode) {
            queue.setRepeatMode(repeatMode)
            onRepeatChanged()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> queue.beforeManualNext()
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                if (queue.seekToHistoryPrevious(restartCurrent = seekCommand == Player.COMMAND_SEEK_TO_PREVIOUS)) {
                    return Futures.immediateVoidFuture()
                }
            }
        }
        return super.handleSeek(mediaItemIndex, positionMs, seekCommand)
    }
}
