package app.echo.android.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.echo.android.R
import app.echo.android.playback.EchoPlaybackSurfaceSnapshot

/** Large current-line typography without parsing, scrolling, animation, or position polling. */
@Composable
internal fun EchoLyricsWidgetContent(context: Context, snapshot: EchoPlaybackSurfaceSnapshot, artwork: Bitmap?, lyricLine: String?, availableHeightDp: Float) {
    val size = LocalSize.current
    val tall = availableHeightDp >= 260
    val header = availableHeightDp >= 180
    val largeText = context.resources.configuration.fontScale > 1.25f
    val intent = widgetLaunchIntent(context)
    val title = snapshot.title.ifBlank { context.getString(R.string.app_name) }
    val artist = snapshot.artist.ifBlank {
        if (snapshot.hasTrack) "" else context.getString(R.string.playback_widget_idle)
    }
    val lyric = lyricLine?.takeIf { snapshot.hasTrack && it.isNotBlank() }
    val text = if (!LocalWidgetOptions.current.showLyrics) title else lyric ?: context.getString(if (snapshot.hasTrack) R.string.widget_lyrics_unavailable else R.string.widget_lyrics_hint)
    Column(
        GlanceModifier.fillMaxSize().background(widgetBackground(context))
            .cornerRadius(24.dp).clickable(actionStartActivity(intent)).padding(if (header) 16.dp else 12.dp),
    ) {
        if (header && !largeText) {
            Text(context.getString(R.string.widget_lyrics_label), maxLines = 1,
                style = TextStyle(color = widgetSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium))
            Spacer(GlanceModifier.height(8.dp))
        }
        Box(GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.CenterStart) {
            Text(text, maxLines = if (largeText) 1 else if (tall) 3 else 2,
                style = TextStyle(color = if (lyric != null) widgetForeground else widgetSecondary,
                    fontSize = (if (largeText || lyric == null) 17 else if (tall) 26 else 20).sp,
                    fontWeight = if (lyric != null) FontWeight.Bold else FontWeight.Normal))
        }
        Spacer(GlanceModifier.height(12.dp))
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            WidgetArtwork(artwork, 32.dp)
            Spacer(GlanceModifier.width(8.dp))
            WidgetTrackText(title, if (largeText) "" else artist, GlanceModifier.defaultWeight(), 12, 11)
            Spacer(GlanceModifier.width(8.dp))
            WidgetTransport(context, snapshot.isPlaying, snapshot.hasTrack, intent, 44.dp,
                full = size.width >= 300.dp && !largeText, gap = 4.dp)
        }
    }
}
