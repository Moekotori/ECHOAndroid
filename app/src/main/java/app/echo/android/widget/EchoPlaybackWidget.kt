@file:OptIn(UnstableApi::class)

package app.echo.android.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.media3.common.util.UnstableApi
import app.echo.android.MainActivity
import app.echo.android.R
import app.echo.android.i18n.wrapEchoAppLocaleToMatchApplication
import app.echo.android.design.EchoArtworkUrlRewriteRegistry
import app.echo.android.playback.EchoPlaybackArtwork
import app.echo.android.playback.EchoPlaybackProcessRuntime
import app.echo.android.playback.EchoPlaybackSurfaceSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

class EchoPlaybackWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(
                EchoPlaybackWidgetLayout.CompactWidthDp.dp,
                EchoPlaybackWidgetLayout.CompactHeightDp.dp,
            ),
            DpSize(
                EchoPlaybackWidgetLayout.ExpandedWidthDp.dp,
                EchoPlaybackWidgetLayout.ExpandedHeightDp.dp,
            ),
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val localized = context.wrapEchoAppLocaleToMatchApplication()
        val snapshot = EchoPlaybackProcessRuntime.surfaceSnapshot
        val lyricLine = EchoPlaybackProcessRuntime.notificationLyricLine.value
        val artwork = withContext(Dispatchers.IO) {
            loadWidgetArtwork(localized, snapshot)
        }
        provideContent {
            val size = LocalSize.current
            EchoPlaybackWidgetContent(
                context = localized,
                snapshot = snapshot,
                artwork = artwork,
                lyricLine = lyricLine,
                expanded = EchoPlaybackWidgetLayout.isExpanded(
                    size.width.value.roundToInt(),
                    size.height.value.roundToInt(),
                ),
            )
        }
    }
}

class EchoPlaybackWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = EchoPlaybackWidget()
}

@Composable
private fun EchoPlaybackWidgetContent(
    context: Context,
    snapshot: EchoPlaybackSurfaceSnapshot,
    artwork: Bitmap?,
    lyricLine: String?,
    expanded: Boolean,
) {
    val title = snapshot.title.ifBlank { context.getString(R.string.app_name) }
    val artist = snapshot.artist.ifBlank {
        if (snapshot.hasTrack) "" else context.getString(R.string.playback_widget_idle)
    }
    val launchIntent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_MAIN
        addCategory(Intent.CATEGORY_LAUNCHER)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val root = GlanceModifier
        .fillMaxSize()
        .background(Color(0xE619191D))
        .cornerRadius(20.dp)
        .clickable(actionStartActivity(launchIntent))
    if (expanded) {
        ExpandedWidgetBody(context, title, artist, lyricLine, snapshot.isPlaying, artwork, root)
    } else {
        CompactWidgetBody(context, title, artist, snapshot.isPlaying, artwork, root)
    }
}

@Composable
private fun CompactWidgetBody(
    context: Context,
    title: String,
    artist: String,
    isPlaying: Boolean,
    artwork: Bitmap?,
    modifier: GlanceModifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WidgetArtwork(artwork, 48.dp)
        Spacer(modifier = GlanceModifier.width(12.dp))
        WidgetTrackText(
            title = title,
            artist = artist,
            lyricLine = null,
            modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
            titleSize = 15,
            secondarySize = 12,
        )
        Spacer(modifier = GlanceModifier.width(8.dp))
        WidgetTransport(context, isPlaying, iconSize = 36.dp)
    }
}

@Composable
private fun ExpandedWidgetBody(
    context: Context,
    title: String,
    artist: String,
    lyricLine: String?,
    isPlaying: Boolean,
    artwork: Bitmap?,
    modifier: GlanceModifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WidgetArtwork(artwork, 52.dp)
            Spacer(modifier = GlanceModifier.width(12.dp))
            WidgetTrackText(
                title = title,
                artist = artist,
                lyricLine = lyricLine,
                modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
                titleSize = 16,
                secondarySize = 12,
            )
        }
        Spacer(modifier = GlanceModifier.height(6.dp))
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WidgetTransport(context, isPlaying, iconSize = 36.dp)
        }
    }
}

@Composable
private fun WidgetArtwork(artwork: Bitmap?, size: Dp) {
    Image(
        provider = if (artwork != null) {
            ImageProvider(artwork)
        } else {
            ImageProvider(R.drawable.media3_notification_small_icon)
        },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = GlanceModifier.size(size).cornerRadius(10.dp),
    )
}

@Composable
private fun WidgetTrackText(
    title: String,
    artist: String,
    lyricLine: String?,
    modifier: GlanceModifier,
    titleSize: Int,
    secondarySize: Int,
) {
    Column(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            maxLines = 1,
            style = TextStyle(
                color = ColorProvider(Color.White),
                fontSize = titleSize.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        if (artist.isNotBlank()) {
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = artist,
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(Color(0xB3FFFFFF)),
                    fontSize = secondarySize.sp,
                ),
            )
        }
        if (!lyricLine.isNullOrBlank()) {
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = lyricLine,
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(Color(0xE6FFFFFF)),
                    fontSize = secondarySize.sp,
                ),
            )
        }
    }
}

@Composable
private fun WidgetTransport(context: Context, isPlaying: Boolean, iconSize: Dp) {
    WidgetIconButton(
        resId = R.drawable.echo_ic_skip_previous,
        contentDescription = context.getString(R.string.playback_widget_previous),
        action = EchoPlaybackWidgetPreviousAction::class.java,
        size = iconSize,
    )
    Spacer(modifier = GlanceModifier.width(4.dp))
    WidgetIconButton(
        resId = if (isPlaying) R.drawable.echo_ic_pause else R.drawable.echo_ic_play,
        contentDescription = context.getString(
            if (isPlaying) R.string.playback_widget_pause else R.string.playback_widget_play,
        ),
        action = EchoPlaybackWidgetPlayPauseAction::class.java,
        size = iconSize,
    )
    Spacer(modifier = GlanceModifier.width(4.dp))
    WidgetIconButton(
        resId = R.drawable.echo_ic_skip_next,
        contentDescription = context.getString(R.string.playback_widget_next),
        action = EchoPlaybackWidgetNextAction::class.java,
        size = iconSize,
    )
}

@Composable
private fun WidgetIconButton(
    resId: Int,
    contentDescription: String,
    action: Class<out ActionCallback>,
    size: androidx.compose.ui.unit.Dp,
) {
    Image(
        provider = ImageProvider(resId),
        contentDescription = contentDescription,
        modifier = GlanceModifier
            .size(size)
            .clickable(actionRunCallback(action)),
    )
}

@UnstableApi
class EchoPlaybackWidgetPlayPauseAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        EchoPlaybackRemote.togglePlayPause(context)
    }
}

@UnstableApi
class EchoPlaybackWidgetPreviousAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        EchoPlaybackRemote.skipToPrevious(context)
    }
}

@UnstableApi
class EchoPlaybackWidgetNextAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        EchoPlaybackRemote.skipToNext(context)
    }
}

private data class CachedWidgetArtwork(val key: String, val bitmap: Bitmap?)

private val cachedWidgetArtwork = AtomicReference<CachedWidgetArtwork?>(null)

private fun loadWidgetArtwork(
    context: Context,
    snapshot: EchoPlaybackSurfaceSnapshot,
): Bitmap? {
    val key = "${snapshot.artworkUri.orEmpty()}|${snapshot.playUri.orEmpty()}"
    cachedWidgetArtwork.get()?.takeIf { it.key == key }?.let { return it.bitmap }
    val loaded = loadWidgetArtworkUncached(context, snapshot)
    cachedWidgetArtwork.set(CachedWidgetArtwork(key, loaded))
    return loaded
}

private fun loadWidgetArtworkUncached(
    context: Context,
    snapshot: EchoPlaybackSurfaceSnapshot,
): Bitmap? {
    EchoPlaybackArtwork.load(
        context = context,
        artworkUri = snapshot.artworkUri,
        embeddedSourceUri = snapshot.playUri,
        maxEdgePx = EchoPlaybackArtwork.WidgetMaxEdgePx,
    )?.let { return it }
    val remote = EchoArtworkUrlRewriteRegistry.rewrite(snapshot.artworkUri)
        ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        ?: return null
    return runCatching {
        val connection = URL(remote).openConnection() as HttpURLConnection
        connection.connectTimeout = 2_500
        connection.readTimeout = 2_500
        connection.instanceFollowRedirects = true
        connection.inputStream.use { input ->
            EchoPlaybackArtwork.decodeCapped(
                input = input,
                maxEdgePx = EchoPlaybackArtwork.WidgetMaxEdgePx,
                maxBytes = 2 * 1024 * 1024,
            )
        }
    }.getOrNull()
}
