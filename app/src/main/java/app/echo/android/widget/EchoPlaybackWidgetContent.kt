@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package app.echo.android.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.action.Action
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.echo.android.MainActivity
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
) {
    GlanceTheme {
        val largeText = context.resources.configuration.fontScale > 1.25f
        val title = snapshot.title.ifBlank { context.getString(R.string.app_name) }
        val artist = snapshot.artist.ifBlank {
            if (snapshot.hasTrack) "" else context.getString(R.string.playback_widget_idle)
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val root = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(20.dp).clickable(actionStartActivity(intent))
        when (mode) {
            EchoPlaybackWidgetMode.Compact, EchoPlaybackWidgetMode.Wide -> Row(
                root.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WidgetArtwork(artwork, 32.dp)
                Spacer(GlanceModifier.width(8.dp))
                WidgetTrackText(title, if (largeText) "" else artist, GlanceModifier.defaultWeight(), 14, 11)
                Spacer(GlanceModifier.width(8.dp))
                WidgetTransport(context, snapshot.isPlaying, snapshot.hasTrack, intent, 44.dp, full = mode == EchoPlaybackWidgetMode.Wide)
            }
            EchoPlaybackWidgetMode.Expanded -> Column(
                root.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                    WidgetArtwork(artwork, 44.dp)
                    Spacer(GlanceModifier.width(10.dp))
                    WidgetTrackText(title, if (largeText) "" else artist, GlanceModifier.defaultWeight(), 15, 12,
                        lyricLine = lyricLine.takeIf { snapshot.hasTrack && !largeText })
                }
                Row(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically) {
                    WidgetTransport(context, snapshot.isPlaying, snapshot.hasTrack, intent, 44.dp)
                }
            }
            EchoPlaybackWidgetMode.Tall -> Column(
                root.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                    WidgetArtwork(artwork, 64.dp)
                    Spacer(GlanceModifier.width(12.dp))
                    WidgetTrackText(title, artist, GlanceModifier.defaultWeight(), 17, 13)
                }
                if (snapshot.hasTrack && !lyricLine.isNullOrBlank() && !largeText) {
                    Spacer(GlanceModifier.height(6.dp))
                    Text(lyricLine, maxLines = 1, style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp,
                    ))
                }
                Spacer(GlanceModifier.height(8.dp))
                Row(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically) {
                    WidgetTransport(context, snapshot.isPlaying, snapshot.hasTrack, intent, 48.dp)
                }
            }
        }
    }
}

@Composable
private fun WidgetArtwork(artwork: Bitmap?, size: Dp) {
    Image(
        provider = artwork?.let { ImageProvider(it) } ?: ImageProvider(R.drawable.media3_notification_small_icon),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        colorFilter = if (artwork == null) ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant) else null,
        modifier = GlanceModifier.size(size).cornerRadius(8.dp),
    )
}

@Composable
private fun WidgetTrackText(title: String, artist: String, modifier: GlanceModifier, titleSize: Int, secondarySize: Int,
                            lyricLine: String? = null) {
    Column(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(title, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurface,
            fontSize = titleSize.sp, fontWeight = FontWeight.Medium))
        if (artist.isNotBlank()) {
            Spacer(GlanceModifier.height(2.dp))
            Text(artist, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = secondarySize.sp))
        }
        if (!lyricLine.isNullOrBlank()) {
            Spacer(GlanceModifier.height(2.dp))
            Text(lyricLine, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = secondarySize.sp))
        }
    }
}

@Composable
private fun WidgetTransport(context: Context, isPlaying: Boolean, hasTrack: Boolean, intent: Intent, size: Dp, full: Boolean = true) {
    if (full) {
        WidgetIconButton(R.drawable.echo_ic_skip_previous, context.getString(R.string.playback_widget_previous),
            if (hasTrack) actionRunCallback<EchoPlaybackWidgetPreviousAction>() else null, size)
        Spacer(GlanceModifier.width(8.dp))
    }
    WidgetIconButton(if (isPlaying) R.drawable.echo_ic_pause else R.drawable.echo_ic_play,
        context.getString(if (!hasTrack) R.string.app_name else if (isPlaying) R.string.playback_widget_pause else R.string.playback_widget_play),
        if (hasTrack) actionRunCallback<EchoPlaybackWidgetPlayPauseAction>() else actionStartActivity(intent), size)
    if (full) {
        Spacer(GlanceModifier.width(8.dp))
        WidgetIconButton(R.drawable.echo_ic_skip_next, context.getString(R.string.playback_widget_next),
            if (hasTrack) actionRunCallback<EchoPlaybackWidgetNextAction>() else null, size)
    }
}

@Composable
private fun WidgetIconButton(resId: Int, description: String, action: Action?, size: Dp) {
    val target = GlanceModifier.size(size).let { if (action != null) it.clickable(action) else it }
    Box(target, contentAlignment = Alignment.Center) {
        Image(ImageProvider(resId), description, colorFilter = ColorFilter.tint(
            if (action != null) GlanceTheme.colors.onSurface else GlanceTheme.colors.onSurfaceVariant),
            modifier = GlanceModifier.size(24.dp))
    }
}
