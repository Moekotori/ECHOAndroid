package app.echo.android.widget

import android.content.Context
import androidx.media3.common.util.UnstableApi
import app.echo.android.playback.EchoPlaybackProcessRuntime
import kotlinx.coroutines.launch

/** Tile callers retain their non-suspending API; widget callbacks await the actual command. */
@UnstableApi
object EchoPlaybackRemote {
    fun play(context: Context) = dispatch(context, EchoWidgetPlaybackCommand.Play)
    fun togglePlayPause(context: Context) = dispatch(context, EchoWidgetPlaybackCommand.Toggle)
    fun skipToNext(context: Context) = dispatch(context, EchoWidgetPlaybackCommand.Next)
    fun skipToPrevious(context: Context) = dispatch(context, EchoWidgetPlaybackCommand.Previous)

    private fun dispatch(context: Context, command: EchoWidgetPlaybackCommand) {
        val app = context.applicationContext
        EchoPlaybackProcessRuntime.scope.launch { executeWidgetPlaybackCommand(app, command) }
    }
}
