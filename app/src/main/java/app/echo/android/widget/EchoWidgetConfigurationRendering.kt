package app.echo.android.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.background
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.*
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.echo.android.R
import app.echo.android.library.EchoLibraryShortcuts
import app.echo.android.model.settings.EchoWidgetOptions

internal val LocalWidgetOptions = staticCompositionLocalOf { EchoWidgetOptions() }

@Composable
internal fun widgetBackground(context: Context) = widgetSolidColor(
    Color(context.getColor(R.color.playback_widget_background)).copy(alpha = LocalWidgetOptions.current.backgroundOpacity))

@Composable
internal fun WidgetBoundPlaylist(context: Context, options: EchoWidgetOptions) {
    val id = options.playlistMediaId ?: return
    if (androidx.glance.LocalSize.current.height < 120.dp) return
    Row(GlanceModifier.fillMaxWidth().height(40.dp).background(widgetBackground(context)).padding(horizontal = 12.dp)
        .clickable(actionStartActivity(EchoLibraryShortcuts.intent(context, id, true))),
        verticalAlignment = Alignment.CenterVertically) {
        Text(context.getString(R.string.widget_bound_play, options.playlistName.orEmpty()), maxLines = 1,
            style = TextStyle(color = widgetForeground))
    }
}
