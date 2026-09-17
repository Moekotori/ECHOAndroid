package app.echo.android.playback

import android.content.Context
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand

object EchoPlaybackIntents {
    const val ACTION_OPEN_LYRICS = "app.echo.android.playback.OPEN_LYRICS"
    const val ACTION_OPEN_LOCK_LYRICS = "app.echo.android.playback.OPEN_LOCK_LYRICS"
    const val EXTRA_OPEN_LYRICS = "app.echo.android.playback.EXTRA_OPEN_LYRICS"
    const val ACTION_PLAY_LAST = "app.echo.android.action.PLAY_LAST"
    const val ACTION_OPEN_LIBRARY = "app.echo.android.action.OPEN_LIBRARY"

    fun isPlayLast(action: String?): Boolean = action == ACTION_PLAY_LAST

    fun isOpenLibrary(action: String?): Boolean = action == ACTION_OPEN_LIBRARY
}

@UnstableApi
object EchoPlaybackSessionCommands {
    const val TOGGLE_FAVORITE = "app.echo.android.playback.TOGGLE_FAVORITE"
    const val CYCLE_REPEAT = "app.echo.android.playback.CYCLE_REPEAT"
    const val OPEN_LYRICS = "app.echo.android.playback.OPEN_LYRICS"
    const val OPEN_LOCK_LYRICS = "app.echo.android.playback.OPEN_LOCK_LYRICS"

    val toggleFavorite = SessionCommand(TOGGLE_FAVORITE, Bundle.EMPTY)
    val cycleRepeat = SessionCommand(CYCLE_REPEAT, Bundle.EMPTY)
    val openLyrics = SessionCommand(OPEN_LYRICS, Bundle.EMPTY)
    val openLockLyrics = SessionCommand(OPEN_LOCK_LYRICS, Bundle.EMPTY)
}

fun nextPlayerRepeatMode(current: Int): Int = when (current) {
    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
    else -> Player.REPEAT_MODE_OFF
}

@UnstableApi
fun echoPlaybackCommandButtons(
    context: Context,
    favorite: Boolean,
    repeatMode: Int,
): List<CommandButton> = listOf(
    CommandButton.Builder(
        if (favorite) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED,
    )
        .setSessionCommand(EchoPlaybackSessionCommands.toggleFavorite)
        .setDisplayName(
            context.getString(
                if (favorite) R.string.playback_command_unfavorite else R.string.playback_command_favorite,
            ),
        )
        .setSlots(CommandButton.SLOT_OVERFLOW)
        .setEnabled(true)
        .build(),
    CommandButton.Builder(
        when (repeatMode) {
            Player.REPEAT_MODE_ALL -> CommandButton.ICON_REPEAT_ALL
            Player.REPEAT_MODE_ONE -> CommandButton.ICON_REPEAT_ONE
            else -> CommandButton.ICON_REPEAT_OFF
        },
    )
        .setSessionCommand(EchoPlaybackSessionCommands.cycleRepeat)
        .setDisplayName(
            context.getString(
                when (repeatMode) {
                    Player.REPEAT_MODE_ALL -> R.string.playback_command_repeat_all
                    Player.REPEAT_MODE_ONE -> R.string.playback_command_repeat_one
                    else -> R.string.playback_command_repeat_off
                },
            ),
        )
        .setSlots(CommandButton.SLOT_OVERFLOW)
        .setEnabled(true)
        .build(),
    CommandButton.Builder(CommandButton.ICON_SUBTITLES)
        .setSessionCommand(EchoPlaybackSessionCommands.openLyrics)
        .setDisplayName(context.getString(R.string.playback_command_lyrics))
        .setSlots(CommandButton.SLOT_OVERFLOW)
        .setEnabled(true)
        .build(),
    CommandButton.Builder(CommandButton.ICON_SUBTITLES)
        .setSessionCommand(EchoPlaybackSessionCommands.openLockLyrics)
        .setDisplayName(context.getString(R.string.playback_command_lock_lyrics))
        .setSlots(CommandButton.SLOT_OVERFLOW)
        .setEnabled(true)
        .build(),
)
