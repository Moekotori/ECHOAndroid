package app.echo.android.widget

import android.content.Context
import android.graphics.Bitmap
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

/** Album artwork is the surface; a fixed scrim keeps all covers and idle state readable. */
@Composable
internal fun EchoCoverWidgetContent(context: Context, snapshot: EchoPlaybackSurfaceSnapshot, artwork: Bitmap?) {
    val intent = widgetLaunchIntent(context)
    val wide = LocalSize.current.width >= 220.dp
    val largeText = context.resources.configuration.fontScale > 1.25f
    val title = snapshot.title.ifBlank { context.getString(R.string.app_name) }
    val artist = snapshot.artist.ifBlank {
        if (snapshot.hasTrack) "" else context.getString(R.string.playback_widget_idle)
    }
    val foreground = if (artwork != null) widgetSolidColor(ArtworkOverlayForeground) else widgetForeground
    val secondary = if (artwork != null) widgetSolidColor(ArtworkOverlaySecondary) else widgetSecondary
    val root = GlanceModifier.fillMaxSize().background(widgetBackground(context)).widgetSurface()
        .clickable(actionStartActivity(intent))
    Box(root, contentAlignment = Alignment.BottomStart) {
        if (artwork != null) {
            Image(ImageProvider(artwork), null, modifier = GlanceModifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else if (LocalWidgetOptions.current.showArtwork) {
            Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Image(ImageProvider(R.drawable.media3_notification_small_icon), null,
                    modifier = GlanceModifier.size(56.dp), colorFilter = ColorFilter.tint(secondary))
            }
        }
        if (artwork != null) Box(GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.echo_widget_artwork_scrim))) {}
        Column(GlanceModifier.fillMaxWidth().padding(if (wide) 16.dp else 12.dp)) {
            Text(title, maxLines = if (wide || largeText) 1 else 2,
                style = TextStyle(color = foreground, fontSize = (if (wide) 19 else 16).sp,
                    fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.height(4.dp))
            if (wide && LocalWidgetOptions.current.fullControls) {
                Text(artist, maxLines = 1, style = TextStyle(color = secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                Spacer(GlanceModifier.height(8.dp))
                Row(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically) {
                    WidgetTransport(context, snapshot.isPlaying, snapshot.hasTrack, intent, 44.dp,
                        full = true, onArtwork = artwork != null)
                }
            } else {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(artist, modifier = GlanceModifier.defaultWeight(), maxLines = 1,
                    style = TextStyle(color = secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                Spacer(GlanceModifier.width(8.dp))
                WidgetTransport(context, snapshot.isPlaying, snapshot.hasTrack, intent,
                    if (wide) 48.dp else 44.dp, full = false, onArtwork = artwork != null)
            }
            }
        }
    }
}
