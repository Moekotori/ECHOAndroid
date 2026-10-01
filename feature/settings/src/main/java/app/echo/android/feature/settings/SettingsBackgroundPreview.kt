package app.echo.android.feature.settings

import app.echo.android.design.EchoIcon

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoLegacyBackgroundBlur
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.LocalEchoPlatformCapabilities
import app.echo.android.design.echoTheme
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class VideoPreviewPoster(val bitmap: Bitmap? = null, val loading: Boolean = true)

/** One bounded image (or video still) is reused throughout dragging; no second video player. */
@Composable
internal fun SettingsBackgroundPreview(
    uri: String,
    isVideo: Boolean,
    blur: Float,
    brightness: Float,
    glass: Float,
    scale: Float,
    appliedBlur: Float,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val renderBlur = LocalEchoPlatformCapabilities.current.renderEffectBlur
    val dark = LocalEchoDarkTheme.current
    val tokens = echoTheme()
    val poster by produceState(VideoPreviewPoster(), context, uri, isVideo) {
        if (!isVideo) { value = VideoPreviewPoster(loading = false); return@produceState }
        val bitmap = withContext(Dispatchers.IO) {
            try {
                val source = Uri.parse(uri)
                if (Build.VERSION.SDK_INT >= 29) {
                    context.contentResolver.loadThumbnail(source, Size(768, 432), null)
                } else {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, source)
                        if (Build.VERSION.SDK_INT >= 27) {
                            retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 768, 432)
                        } else {
                            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { frame ->
                                val ratio = minOf(768f / frame.width, 432f / frame.height, 1f)
                                val resized = Bitmap.createScaledBitmap(frame,
                                    (frame.width * ratio).toInt().coerceAtLeast(1),
                                    (frame.height * ratio).toInt().coerceAtLeast(1), true)
                                if (resized !== frame) frame.recycle()
                                resized
                            }
                        }
                    } finally { retriever.release() }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) { null }
        }
        value = VideoPreviewPoster(bitmap, loading = false)
    }
    val legacyBlur = if (!renderBlur && !isVideo) appliedBlur else 0f
    val viewportWidth = configuration.screenWidthDp
    val viewportHeight = configuration.screenHeightDp
    val request = remember(context, uri, isVideo, poster.bitmap, legacyBlur, viewportWidth, viewportHeight) {
        ImageRequest.Builder(context).data(if (isVideo) poster.bitmap else uri)
            .size(768, 432).crossfade(false)
            .apply {
                if (legacyBlur > 0f) {
                    allowHardware(false)
                    transformations(EchoLegacyBackgroundBlur(legacyBlur, viewportWidth, viewportHeight))
                }
            }.build()
    }
    var imageFailed by remember(uri, isVideo) { mutableStateOf(false) }
    val unavailable = imageFailed || (isVideo && !poster.loading && poster.bitmap == null)
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.settings_appearance_preview),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.fillMaxWidth().height(180.dp).clip(SettingsShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)) {
            if (!unavailable && (!isVideo || poster.bitmap != null)) {
                AsyncImage(model = request,
                    contentDescription = stringResource(R.string.settings_appearance_preview),
                    contentScale = ContentScale.Crop, onError = { imageFailed = true },
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = scale; scaleY = scale
                    }.then(if (renderBlur && !isVideo) Modifier.blur(blur.dp) else Modifier))
            }
            // Match the app wallpaper's brightness and readability overlays.
            val overlay = if (dark) {
                val dim = if (brightness < 1f) 0.18f + (1f - brightness) * 0.82f
                    else 0.18f - (brightness - 1f) * 0.12f
                Color.Black.copy(alpha = dim.coerceIn(0.10f, 0.62f))
            } else if (brightness < 1f) {
                Color.Black.copy(alpha = ((1f - brightness) * 0.72f).coerceIn(0f, 0.42f))
            } else Color.White.copy(alpha = ((brightness - 1f) * 0.35f).coerceIn(0f, 0.12f))
            Box(Modifier.fillMaxSize().background(overlay))
            val glassColors = if (dark) listOf(
                tokens.night.copy(alpha = 0.06f + glass * 0.58f),
                tokens.ink.copy(alpha = 0.04f + glass * 0.42f),
                tokens.night.copy(alpha = 0.12f + glass * 0.68f),
            ) else listOf(
                Color.White.copy(alpha = 0.12f + glass * 0.78f),
                Color.White.copy(alpha = 0.12f + glass * 0.68f),
                Color.White.copy(alpha = 0.18f + glass * 0.86f),
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(glassColors)))
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                EchoIcon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(if (unavailable) R.string.settings_background_preview_unavailable
                    else if (isVideo && poster.loading) R.string.settings_background_preview_loading
                    else R.string.settings_font_preview_ui),
                    style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        if (isVideo) Text(stringResource(R.string.settings_background_preview_still),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!renderBlur && !isVideo) Text(stringResource(R.string.settings_background_preview_legacy_blur),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
