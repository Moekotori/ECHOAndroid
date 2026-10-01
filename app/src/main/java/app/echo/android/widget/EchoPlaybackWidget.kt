@file:OptIn(UnstableApi::class)

package app.echo.android.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.provideContent
import androidx.media3.common.util.UnstableApi
import app.echo.android.i18n.wrapEchoAppLocaleToMatchApplication
import app.echo.android.playback.EchoPlaybackProcessRuntime
import kotlin.math.roundToInt

class EchoPlaybackWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(
                EchoPlaybackWidgetLayout.CompactWidthDp.dp,
                EchoPlaybackWidgetLayout.CompactHeightDp.dp,
            ),
            DpSize(
                EchoPlaybackWidgetLayout.ExpandedWidthDp.dp,
                EchoPlaybackWidgetLayout.ExpandedHeightDp.dp,
            ),
            DpSize(EchoPlaybackWidgetLayout.WideWidthDp.dp, EchoPlaybackWidgetLayout.WideHeightDp.dp),
            DpSize(EchoPlaybackWidgetLayout.TallWidthDp.dp, EchoPlaybackWidgetLayout.TallHeightDp.dp),
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val localized = context.wrapEchoAppLocaleToMatchApplication()
        val snapshot = readWidgetPlaybackSnapshot(localized)
        val live = EchoPlaybackProcessRuntime.surfaceSnapshot
        val lyricLine = EchoPlaybackProcessRuntime.notificationLyricLine.value
            .takeIf { live.hasTrack && live.mediaId == snapshot.mediaId }
        val artwork = EchoWidgetArtwork.load(localized, snapshot, allowRemote = live.hasTrack)
        provideContent {
            val size = LocalSize.current
            EchoPlaybackWidgetContent(
                context = localized,
                snapshot = snapshot,
                artwork = artwork,
                lyricLine = lyricLine,
                mode = EchoPlaybackWidgetLayout.mode(
                    size.width.value.roundToInt(),
                    size.height.value.roundToInt(),
                ),
            )
        }
    }
}

class EchoPlaybackWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = EchoPlaybackWidget()
}

@UnstableApi
class EchoPlaybackWidgetPlayPauseAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        executeWidgetPlaybackCommand(context, EchoWidgetPlaybackCommand.Toggle)
        EchoPlaybackWidget().update(context, glanceId)
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
        EchoPlaybackWidget().update(context, glanceId)
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
        EchoPlaybackWidget().update(context, glanceId)
    }
}
