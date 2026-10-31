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

/** A small record sleeve with a static circular label and a quiet single play control. */
@Composable
internal fun EchoRecordWidgetContent(context: Context, snapshot: EchoPlaybackSurfaceSnapshot,
    artwork: Bitmap?, availableHeightDp: Float) {
    val intent = widgetLaunchIntent(context)
    val largeText = context.resources.configuration.fontScale > 1.25f
    val title = snapshot.title.ifBlank { context.getString(R.string.app_name) }
    val artist = snapshot.artist.ifBlank { if (snapshot.hasTrack) "" else context.getString(R.string.playback_widget_idle) }
    Column(GlanceModifier.fillMaxSize().clickable(actionStartActivity(intent)).padding(12.dp)) {
        Box(GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.Center) {
            if (LocalWidgetOptions.current.showArtwork) Image(
                artwork?.let { ImageProvider(it) } ?: ImageProvider(R.drawable.echo_widget_record), null,
                modifier = GlanceModifier.size((availableHeightDp - 88f).coerceIn(24f, 168f).dp),
                contentScale = ContentScale.Fit,
            )
        }
        Spacer(GlanceModifier.height(8.dp))
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            WidgetTrackText(title, if (largeText) "" else artist, GlanceModifier.defaultWeight(), 15, 12)
            Spacer(GlanceModifier.width(4.dp))
            WidgetTransport(context, snapshot.isPlaying, snapshot.hasTrack, intent, 44.dp, full = false)
        }
    }
}
