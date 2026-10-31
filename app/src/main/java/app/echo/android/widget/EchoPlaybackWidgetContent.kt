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

/** One renderer for both widget hosts; lock-screen launches use normal system authentication. */
@Composable
internal fun EchoPlaybackWidgetContent(
    context: Context,
    snapshot: EchoPlaybackSurfaceSnapshot,
    artwork: Bitmap?,
    lyricLine: String?,
    mode: EchoPlaybackWidgetMode,
    availableHeightDp: Float,
) {
    val largeText = context.resources.configuration.fontScale > 1.25f
    val title = snapshot.title.ifBlank { context.getString(R.string.app_name) }
    val artist = snapshot.artist.ifBlank {
        if (snapshot.hasTrack) "" else context.getString(R.string.playback_widget_idle)
    }
    val lyric = lyricLine.takeIf { snapshot.hasTrack && !largeText && !it.isNullOrBlank() }
    val intent = widgetLaunchIntent(context)
    val root = GlanceModifier.fillMaxSize().background(widgetBackground(context))
        .widgetSurface().clickable(actionStartActivity(intent))
    when (mode) {
        EchoPlaybackWidgetMode.Compact, EchoPlaybackWidgetMode.Wide -> Row(
            root.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WidgetArtwork(artwork, 32.dp)
            Spacer(GlanceModifier.width(8.dp))
            WidgetTrackText(title, if (largeText) "" else artist, GlanceModifier.defaultWeight(), 14, 11)
            Spacer(GlanceModifier.width(8.dp))
            WidgetTransport(context, snapshot.isPlaying, snapshot.hasTrack, intent, 44.dp,
                full = mode == EchoPlaybackWidgetMode.Wide)
        }
        EchoPlaybackWidgetMode.Expanded -> Row(
            root.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WidgetArtwork(artwork, 60.dp)
            Spacer(GlanceModifier.width(12.dp))
            Column(GlanceModifier.defaultWeight()) {
                WidgetTrackText(title, if (largeText) "" else artist, GlanceModifier.fillMaxWidth(), 15, 12)
                Spacer(GlanceModifier.height(4.dp))
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    WidgetTransport(context, snapshot.isPlaying, snapshot.hasTrack, intent, 44.dp, gap = 4.dp)
                }
            }
        }
        EchoPlaybackWidgetMode.Tall -> Column(
            root.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                WidgetArtwork(artwork, 68.dp)
                Spacer(GlanceModifier.width(12.dp))
                WidgetTrackText(title, artist, GlanceModifier.defaultWeight(), 17, 13)
            }
            if (lyric != null) {
                Spacer(GlanceModifier.height(8.dp))
                WidgetLyric(lyric)
            }
            Spacer(GlanceModifier.height(10.dp))
            Row(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically) {
                WidgetTransport(context, snapshot.isPlaying, snapshot.hasTrack, intent, 48.dp, gap = 16.dp)
            }
        }
        EchoPlaybackWidgetMode.Square -> Column(
            root.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Responsive sizes are finite; artwork reuses the existing capped bitmap, with no extra decode.
            val coverSize = (availableHeightDp - 164f).coerceIn(72f, 136f).dp
            WidgetArtwork(artwork, coverSize)
            Spacer(GlanceModifier.height(8.dp))
            WidgetTrackText(title, artist, GlanceModifier.fillMaxWidth(), 18, 13, centered = true)
            if (lyric != null) {
                Spacer(GlanceModifier.height(8.dp))
                WidgetLyric(lyric, centered = true)
            }
            Spacer(GlanceModifier.height(12.dp))
            Row(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically) {
                WidgetTransport(context, snapshot.isPlaying, snapshot.hasTrack, intent, 52.dp, gap = 20.dp)
            }
        }
    }
}
