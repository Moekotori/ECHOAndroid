package app.echo.android.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CancellationException

/** A bounded, sequential refresh shared by all styles and instances. */
internal object EchoPlaybackWidgets {
    private val widgets = listOf(
        EchoPlaybackWidget(), EchoMiniPlaybackWidget(), EchoCoverPlaybackWidget(), EchoLyricsPlaybackWidget(),
    )

    suspend fun updateChanged(context: Context, previous: EchoPlaybackWidgetChrome?, next: EchoPlaybackWidgetChrome) {
        for (widget in widgets) {
            if (!widget.style.needsRefresh(previous, next)) continue
            try {
                widget.updateAll(context)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A removed/unsupported host must not prevent other styles from updating.
            }
        }
    }

    suspend fun updateInstance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val receiver = AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId)?.provider?.className
        val widget = when (receiver) {
            EchoPlaybackWidgetReceiver::class.java.name -> widgets[0]
            EchoMiniPlaybackWidgetReceiver::class.java.name -> widgets[1]
            EchoCoverPlaybackWidgetReceiver::class.java.name -> widgets[2]
            EchoLyricsPlaybackWidgetReceiver::class.java.name -> widgets[3]
            else -> return
        }
        widget.update(context, id)
    }
}
