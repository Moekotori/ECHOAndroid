package app.echo.android.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.echo.android.R
import app.echo.android.playback.EchoPlaybackSurfaceSnapshot

/** Type supplies the hierarchy. No artwork requests, decorative rules, or progress timers. */
@Composable
internal fun EchoTypographyWidgetContent(context: Context, snapshot: EchoPlaybackSurfaceSnapshot,
    lyricLine: String?, availableHeightDp: Float) {
    val intent = widgetLaunchIntent(context)
    val largeText = context.resources.configuration.fontScale > 1.25f
    val title = snapshot.title.ifBlank { context.getString(R.string.app_name) }
    val artist = snapshot.artist.ifBlank { if (snapshot.hasTrack) "" else context.getString(R.string.playback_widget_idle) }
    val tall = availableHeightDp >= 220f
    // A bound-playlist footer takes 48dp. Keep the track and controls visible in the remaining strip.
    if (availableHeightDp < 120f) {
        Row(GlanceModifier.fillMaxSize().clickable(actionStartActivity(intent)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            WidgetTrackText(title, if (largeText) "" else artist, GlanceModifier.defaultWeight(), 15, 11)
            Spacer(GlanceModifier.width(8.dp))
            WidgetTransport(context, snapshot.isPlaying, snapshot.hasTrack, intent, 44.dp,
                full = LocalSize.current.width >= 300.dp && !largeText, gap = 2.dp)
        }
        return
    }
    Column(GlanceModifier.fillMaxSize().clickable(actionStartActivity(intent)).padding(16.dp)) {
        Box(GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.CenterStart) {
            Text(title, maxLines = if (!largeText && availableHeightDp >= 180f) 2 else 1,
                style = TextStyle(color = widgetForeground, fontSize = (if (tall && !largeText) 30 else 22).sp,
                    fontWeight = FontWeight.Bold))
        }
        if (tall && !largeText && snapshot.hasTrack && !lyricLine.isNullOrBlank()) {
            WidgetLyric(lyricLine)
            Spacer(GlanceModifier.height(8.dp))
        }
        if (artist.isNotBlank() && !largeText) {
            Text(artist, maxLines = 1, style = TextStyle(color = widgetSecondary, fontSize = 13.sp))
        }
        Spacer(GlanceModifier.height(8.dp))
        Row(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.End,
            verticalAlignment = Alignment.CenterVertically) {
            WidgetTransport(context, snapshot.isPlaying, snapshot.hasTrack, intent, 44.dp, gap = 8.dp)
        }
    }
}
