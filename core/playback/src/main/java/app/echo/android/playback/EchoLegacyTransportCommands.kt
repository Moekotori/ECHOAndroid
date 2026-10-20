package app.echo.android.playback

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/** Controls the existing queue without granting catalog queries or media/URI replacement. */
@UnstableApi
internal fun echoLegacyTransportCommands(): Player.Commands = Player.Commands.Builder()
    .addAll(*EchoLegacyTransportCommandIds.toIntArray())
    .build()

internal val EchoLegacyTransportCommandIds = setOf(
    Player.COMMAND_PLAY_PAUSE,
    Player.COMMAND_PREPARE,
    Player.COMMAND_STOP,
    Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
    Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
    Player.COMMAND_SEEK_TO_PREVIOUS,
    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
    Player.COMMAND_SEEK_TO_NEXT,
    Player.COMMAND_SEEK_BACK,
    Player.COMMAND_SEEK_FORWARD,
    Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
    Player.COMMAND_GET_TIMELINE,
    Player.COMMAND_GET_METADATA,
)
