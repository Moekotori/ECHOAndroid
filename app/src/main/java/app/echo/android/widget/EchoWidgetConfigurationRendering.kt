package app.echo.android.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.cornerRadius
import androidx.glance.text.FontWeight
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
internal fun widgetBackground(context: Context): androidx.glance.unit.ColorProvider {
    val opacity = LocalWidgetOptions.current.backgroundOpacity
    val base = GlanceTheme.colors.widgetBackground
    return if (opacity >= 1f) base else widgetSolidColor(base.getColor(context).copy(alpha = opacity))
}

@Composable
internal fun WidgetBoundPlaylist(context: Context, options: EchoWidgetOptions) {
    val id = options.playlistMediaId ?: return
    if (androidx.glance.LocalSize.current.height < 120.dp) return
    Box(GlanceModifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
        Row(GlanceModifier.fillMaxWidth().height(40.dp).background(widgetTonal).cornerRadius(20.dp)
            .padding(horizontal = 16.dp)
            .clickable(actionStartActivity(EchoLibraryShortcuts.intent(context, id, true))),
            verticalAlignment = Alignment.CenterVertically) {
            Text(context.getString(R.string.widget_bound_play, options.playlistName.orEmpty()), maxLines = 1,
                style = TextStyle(color = widgetOnTonal, fontSize = 13.sp, fontWeight = FontWeight.Medium))
        }
    }
}
