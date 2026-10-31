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
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import app.echo.android.MainActivity
import app.echo.android.R
import app.echo.android.design.R as DesignR
import app.echo.android.playback.EchoPlaybackSurfaceSnapshot

@Composable
internal fun widgetLaunchIntent(context: Context): Intent {
    LocalWidgetOptions.current.playlistMediaId?.let { return app.echo.android.library.EchoLibraryShortcuts.intent(context, it, true) }
    return Intent(context, MainActivity::class.java).apply {
    action = Intent.ACTION_MAIN
    addCategory(Intent.CATEGORY_LAUNCHER)
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
}
}

@Composable
internal fun WidgetArtwork(artwork: Bitmap?, size: Dp) {
    if (!LocalWidgetOptions.current.showArtwork) return
    Box(
        GlanceModifier.size(size).background(widgetArtworkPlaceholder).widgetInnerCorners(size),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = artwork?.let { ImageProvider(it) } ?: ImageProvider(R.drawable.media3_notification_small_icon),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = if (artwork == null) ColorFilter.tint(widgetSecondary) else null,
            modifier = if (artwork != null) GlanceModifier.fillMaxSize() else GlanceModifier.size(size * 0.45f),
        )
    }
}

/** Flexible cover window; RemoteViews scales the shared bounded bitmap without another decode. */
@Composable
internal fun WidgetArtworkWindow(artwork: Bitmap?, modifier: GlanceModifier) {
    Box(modifier.background(widgetArtworkPlaceholder), contentAlignment = Alignment.Center) {
        Image(
            provider = artwork?.let { ImageProvider(it) } ?: ImageProvider(R.drawable.media3_notification_small_icon),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = if (artwork == null) ColorFilter.tint(widgetSecondary) else null,
            modifier = if (artwork != null) GlanceModifier.fillMaxSize() else GlanceModifier.size(48.dp),
        )
    }
}

@Composable
internal fun WidgetTrackText(
    title: String,
    artist: String,
    modifier: GlanceModifier,
    titleSize: Int,
    secondarySize: Int,
    centered: Boolean = false,
) {
    val alignment = if (centered) TextAlign.Center else TextAlign.Start
    Column(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = GlanceModifier.fillMaxWidth(), maxLines = 1,
            style = TextStyle(color = widgetForeground, fontSize = titleSize.sp,
                fontWeight = FontWeight.Bold, textAlign = alignment))
        if (artist.isNotBlank()) {
            Spacer(GlanceModifier.height(2.dp))
            Text(artist, modifier = GlanceModifier.fillMaxWidth(), maxLines = 1,
                style = TextStyle(color = widgetSecondary, fontSize = secondarySize.sp,
                fontWeight = FontWeight.Medium, textAlign = alignment))
        }
    }
}

@Composable
internal fun WidgetLyric(lyric: String, centered: Boolean = false) {
    Text(lyric, modifier = GlanceModifier.fillMaxWidth(), maxLines = 1,
        style = TextStyle(color = widgetSecondary, fontSize = 12.sp,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start))
}

@Composable
internal fun WidgetTransport(
    context: Context,
    isPlaying: Boolean,
    hasTrack: Boolean,
    intent: Intent,
    size: Dp,
    full: Boolean = true,
    gap: Dp = 8.dp,
    onArtwork: Boolean = false,
) {
    if (full && LocalWidgetOptions.current.fullControls) {
        WidgetIconButton(DesignR.drawable.echo_widget_ic_previous, context.getString(R.string.playback_widget_previous),
            if (hasTrack) actionRunCallback<EchoPlaybackWidgetPreviousAction>() else null, size, onArtwork = onArtwork)
        Spacer(GlanceModifier.width(gap))
    }
    WidgetIconButton(if (isPlaying) DesignR.drawable.echo_widget_ic_pause else DesignR.drawable.echo_widget_ic_play,
        context.getString(if (!hasTrack) R.string.app_name else if (isPlaying) R.string.playback_widget_pause else R.string.playback_widget_play),
        if (hasTrack) actionRunCallback<EchoPlaybackWidgetPlayPauseAction>() else actionStartActivity(intent),
        size, primary = true, onArtwork = onArtwork)
    if (full && LocalWidgetOptions.current.fullControls) {
        Spacer(GlanceModifier.width(gap))
        WidgetIconButton(DesignR.drawable.echo_widget_ic_next, context.getString(R.string.playback_widget_next),
            if (hasTrack) actionRunCallback<EchoPlaybackWidgetNextAction>() else null, size, onArtwork = onArtwork)
    }
}

/** Unboxed transport glyphs; the larger play/pause icon keeps its full accessible hit target. */
@Composable
internal fun WidgetIconButton(resId: Int, description: String, action: Action?, size: Dp,
    primary: Boolean = false, onArtwork: Boolean = false) {
    val tint = if (onArtwork) widgetSolidColor(ArtworkOverlayForeground) else widgetForeground
    val hit = GlanceModifier.size(size).let { if (action != null) it.clickable(action) else it }
    Box(hit, contentAlignment = Alignment.Center) {
        Image(ImageProvider(resId), description,
            colorFilter = ColorFilter.tint(if (action != null) tint else if (onArtwork)
                widgetSolidColor(ArtworkOverlaySecondary) else widgetSecondary),
            modifier = GlanceModifier.size(if (primary) 32.dp else 26.dp))
    }
}
