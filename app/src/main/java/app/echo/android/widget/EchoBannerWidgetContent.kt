package app.echo.android.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.*
import app.echo.android.R
import app.echo.android.playback.EchoPlaybackSurfaceSnapshot

/** Edge-to-edge artwork beside the controls, rather than a centered card or cover overlay. */
@Composable
internal fun EchoBannerWidgetContent(context: Context, snapshot: EchoPlaybackSurfaceSnapshot,
    artwork: Bitmap?, availableHeightDp: Float) {
    if (availableHeightDp < 110f) {
        EchoPlaybackWidgetContent(context, snapshot, artwork, null, EchoPlaybackWidgetMode.Wide, availableHeightDp)
        return
    }
    val intent = widgetLaunchIntent(context)
    val largeText = context.resources.configuration.fontScale > 1.25f
    val title = snapshot.title.ifBlank { context.getString(R.string.app_name) }
    val artist = snapshot.artist.ifBlank { if (snapshot.hasTrack) "" else context.getString(R.string.playback_widget_idle) }
    Row(GlanceModifier.fillMaxSize().clickable(actionStartActivity(intent))) {
        if (LocalWidgetOptions.current.showArtwork) WidgetArtworkWindow(artwork,
            GlanceModifier.width(availableHeightDp.coerceAtMost(128f).dp).fillMaxHeight())
        Column(GlanceModifier.defaultWeight().fillMaxHeight().padding(12.dp)) {
            WidgetTrackText(title, if (largeText) "" else artist, GlanceModifier.fillMaxWidth(), 17, 12)
            Spacer(GlanceModifier.defaultWeight())
            Row(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.End,
                verticalAlignment = Alignment.CenterVertically) {
                WidgetTransport(context, snapshot.isPlaying, snapshot.hasTrack, intent, 44.dp, gap = 2.dp)
            }
        }
    }
}
