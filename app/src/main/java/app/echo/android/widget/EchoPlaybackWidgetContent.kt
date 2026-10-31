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
    val root = GlanceModifier.fillMaxSize().clickable(actionStartActivity(intent))
    when (mode) {
        EchoPlaybackWidgetMode.Compact, EchoPlaybackWidgetMode.Wide -> Row(
            root.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (LocalWidgetOptions.current.showArtwork) {
                WidgetArtwork(artwork, 36.dp)
                Spacer(GlanceModifier.width(10.dp))
            }
            WidgetTrackText(title, if (largeText) "" else artist, GlanceModifier.defaultWeight(), 14, 11)
            Spacer(GlanceModifier.width(8.dp))
            WidgetTransport(context, snapshot.isPlaying, snapshot.hasTrack, intent, 44.dp,
                full = mode == EchoPlaybackWidgetMode.Wide)
        }
        EchoPlaybackWidgetMode.Expanded -> Row(
            root.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (LocalWidgetOptions.current.showArtwork) {
                WidgetArtwork(artwork, 72.dp)
                Spacer(GlanceModifier.width(14.dp))
            }
            Column(GlanceModifier.defaultWeight()) {
                WidgetTrackText(title, if (largeText) "" else artist, GlanceModifier.fillMaxWidth(), 16, 12)
                Spacer(GlanceModifier.height(4.dp))
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    WidgetTransport(context, snapshot.isPlaying, snapshot.hasTrack, intent, 44.dp, gap = 2.dp,
                        full = !largeText)
                }
            }
        }
        EchoPlaybackWidgetMode.Tall -> Column(
            root.padding(16.dp),
        ) {
            Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                if (LocalWidgetOptions.current.showArtwork) {
                    WidgetArtwork(artwork, 76.dp)
                    Spacer(GlanceModifier.width(14.dp))
                }
                WidgetTrackText(title, if (largeText) "" else artist, GlanceModifier.defaultWeight(), 19, 13)
            }
            if (lyric != null && availableHeightDp >= 210f) {
                Spacer(GlanceModifier.height(8.dp))
                WidgetLyric(lyric)
            }
            Spacer(GlanceModifier.height(12.dp))
            Row(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.End,
                verticalAlignment = Alignment.CenterVertically) {
                WidgetTransport(context, snapshot.isPlaying, snapshot.hasTrack, intent, 48.dp, gap = 12.dp)
            }
        }
        EchoPlaybackWidgetMode.Square -> Column(
            root,
        ) {
            val showArtwork = LocalWidgetOptions.current.showArtwork
            if (showArtwork) {
                WidgetArtworkWindow(artwork, GlanceModifier.fillMaxWidth().defaultWeight())
            }
            val footer = GlanceModifier.fillMaxWidth().let { if (showArtwork) it else it.defaultWeight() }
            Column(footer.padding(16.dp)) {
                WidgetTrackText(title, if (largeText) "" else artist, GlanceModifier.fillMaxWidth(), if (showArtwork) 20 else 26, 13)
                if (lyric != null && (!showArtwork || availableHeightDp >= 300f)) {
                    Spacer(GlanceModifier.height(6.dp))
                    WidgetLyric(lyric)
                }
                if (!showArtwork) Spacer(GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.height(12.dp))
                Row(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.End,
                    verticalAlignment = Alignment.CenterVertically) {
                    WidgetTransport(context, snapshot.isPlaying, snapshot.hasTrack, intent, 48.dp, gap = 12.dp)
                }
            }
        }
    }
}
