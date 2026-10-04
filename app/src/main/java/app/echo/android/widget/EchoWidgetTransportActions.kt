package app.echo.android.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.media3.common.util.UnstableApi

@UnstableApi
class EchoPlaybackWidgetPlayPauseAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        executeWidgetPlaybackCommand(context, EchoWidgetPlaybackCommand.Toggle)
        EchoPlaybackWidgets.updateInstance(context, glanceId)
    }
}

@UnstableApi
class EchoPlaybackWidgetPreviousAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        executeWidgetPlaybackCommand(context, EchoWidgetPlaybackCommand.Previous)
        EchoPlaybackWidgets.updateInstance(context, glanceId)
    }
}

@UnstableApi
class EchoPlaybackWidgetNextAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        executeWidgetPlaybackCommand(context, EchoWidgetPlaybackCommand.Next)
        EchoPlaybackWidgets.updateInstance(context, glanceId)
    }
}
