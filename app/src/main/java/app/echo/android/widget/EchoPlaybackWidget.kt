@file:OptIn(UnstableApi::class)

package app.echo.android.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
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
import app.echo.android.design.EchoArtworkUrlRewriteRegistry
import app.echo.android.playback.EchoPlaybackArtwork
import app.echo.android.playback.EchoPlaybackProcessRuntime
import app.echo.android.playback.EchoPlaybackSurfaceSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class EchoPlaybackWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = EchoPlaybackProcessRuntime.surfaceSnapshot
        val artwork = withContext(Dispatchers.IO) {
            loadWidgetArtwork(context, snapshot)
        }
        provideContent {
            EchoPlaybackWidgetContent(snapshot, artwork)
        }
    }
}

class EchoPlaybackWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = EchoPlaybackWidget()
}

@Composable
private fun EchoPlaybackWidgetContent(
    snapshot: EchoPlaybackSurfaceSnapshot,
    artwork: Bitmap?,
) {
    val context = LocalContext.current
    val title = snapshot.title.ifBlank { context.getString(R.string.app_name) }
    val artist = snapshot.artist.ifBlank {
        if (snapshot.hasTrack) "" else context.getString(R.string.playback_widget_idle)
    }
    val launchIntent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_MAIN
        addCategory(Intent.CATEGORY_LAUNCHER)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Color(0xE6181923))
            .cornerRadius(20.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clickable(actionStartActivity(launchIntent)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = if (artwork != null) {
                ImageProvider(artwork)
            } else {
                ImageProvider(R.drawable.media3_notification_small_icon)
            },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = GlanceModifier
                .size(48.dp)
                .cornerRadius(10.dp),
        )
        Spacer(modifier = GlanceModifier.width(12.dp))
        Column(modifier = GlanceModifier.defaultWeight().fillMaxWidth()) {
            Text(
                text = title,
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 15.sp,
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
                        fontSize = 12.sp,
                    ),
                )
            }
        }
        Spacer(modifier = GlanceModifier.width(8.dp))
        WidgetIconButton(
            resId = R.drawable.echo_ic_skip_previous,
            contentDescription = "Previous",
            action = EchoPlaybackWidgetPreviousAction::class.java,
        )
        Spacer(modifier = GlanceModifier.width(4.dp))
        WidgetIconButton(
            resId = if (snapshot.isPlaying) R.drawable.echo_ic_pause else R.drawable.echo_ic_play,
            contentDescription = if (snapshot.isPlaying) "Pause" else "Play",
            action = EchoPlaybackWidgetPlayPauseAction::class.java,
        )
        Spacer(modifier = GlanceModifier.width(4.dp))
        WidgetIconButton(
            resId = R.drawable.echo_ic_skip_next,
            contentDescription = "Next",
            action = EchoPlaybackWidgetNextAction::class.java,
        )
    }
}

@Composable
private fun WidgetIconButton(
    resId: Int,
    contentDescription: String,
    action: Class<out ActionCallback>,
) {
    Image(
        provider = ImageProvider(resId),
        contentDescription = contentDescription,
        modifier = GlanceModifier
            .size(36.dp)
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

private fun loadWidgetArtwork(
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
            val data = input.readBytes()
            if (data.size > 2 * 1024 * 1024) return@runCatching null
            BitmapFactory.decodeByteArray(data, 0, data.size)
        }
    }.getOrNull()
}
