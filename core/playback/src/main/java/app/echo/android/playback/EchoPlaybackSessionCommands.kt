package app.echo.android.playback

import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import app.echo.android.model.i18n.echoText

object EchoPlaybackIntents {
    const val ACTION_OPEN_LYRICS = "app.echo.android.playback.OPEN_LYRICS"
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

    val toggleFavorite = SessionCommand(TOGGLE_FAVORITE, Bundle.EMPTY)
    val cycleRepeat = SessionCommand(CYCLE_REPEAT, Bundle.EMPTY)
    val openLyrics = SessionCommand(OPEN_LYRICS, Bundle.EMPTY)
}

fun nextPlayerRepeatMode(current: Int): Int = when (current) {
    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
    else -> Player.REPEAT_MODE_OFF
}

@UnstableApi
fun echoPlaybackCommandButtons(
    favorite: Boolean,
    repeatMode: Int,
): List<CommandButton> = listOf(
    CommandButton.Builder(
        if (favorite) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED,
    )
        .setSessionCommand(EchoPlaybackSessionCommands.toggleFavorite)
        .setDisplayName(
            if (favorite) {
                echoText(en = "Remove favorite", zh = "取消喜欢", ja = "お気に入り解除")
            } else {
                echoText(en = "Favorite", zh = "喜欢", ja = "お気に入り")
            },
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
            when (repeatMode) {
                Player.REPEAT_MODE_ALL -> echoText(en = "Repeat all", zh = "列表循环", ja = "全曲リピート")
                Player.REPEAT_MODE_ONE -> echoText(en = "Repeat one", zh = "单曲循环", ja = "1曲リピート")
                else -> echoText(en = "Repeat off", zh = "循环关闭", ja = "リピートオフ")
            },
        )
        .setSlots(CommandButton.SLOT_OVERFLOW)
        .setEnabled(true)
        .build(),
    CommandButton.Builder(CommandButton.ICON_SUBTITLES)
        .setSessionCommand(EchoPlaybackSessionCommands.openLyrics)
        .setDisplayName(echoText(en = "Lyrics", zh = "歌词", ja = "歌詞"))
        .setSlots(CommandButton.SLOT_OVERFLOW)
        .setEnabled(true)
        .build(),
)
