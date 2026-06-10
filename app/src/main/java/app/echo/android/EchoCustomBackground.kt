package app.echo.android

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.echo.android.data.EchoAppSettings
import app.echo.android.data.EchoBackgroundMode
import app.echo.android.design.EchoGlassCyan
import app.echo.android.design.EchoGlassInk
import app.echo.android.design.EchoGlassNight
import app.echo.android.design.EchoGlassPanel
import app.echo.android.design.EchoGlassViolet
import app.echo.android.design.EchoGlassBackground
import app.echo.android.design.LocalEchoDarkTheme
import coil.compose.AsyncImage

@Composable
fun EchoCustomBackground(
    settings: EchoAppSettings,
    modifier: Modifier = Modifier,
) {
    val mode = settings.customBackgroundMode
    val uri = settings.customBackgroundUri
    val hasCustomBackground = mode != EchoBackgroundMode.Default && !uri.isNullOrBlank()
    val blur = settings.customBackgroundBlur.dp
    val brightness = settings.customBackgroundBrightness
    val glass = settings.customBackgroundGlass

    Box(modifier = modifier.fillMaxSize()) {
        if (hasCustomBackground) {
            when (mode) {
                EchoBackgroundMode.Video -> EchoVideoWallpaper(
                    uri = uri.orEmpty(),
                    blur = blur,
                    brightness = brightness,
                )

                EchoBackgroundMode.Image -> EchoImageWallpaper(
                    uri = uri.orEmpty(),
                    blur = blur,
                    brightness = brightness,
                )

                else -> EchoGlassBackground(Modifier.fillMaxSize())
            }
            EchoBackgroundGlassOverlay(glass = glass)
        } else {
            EchoGlassBackground(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun EchoImageWallpaper(
    uri: String,
    blur: androidx.compose.ui.unit.Dp,
    brightness: Float,
) {
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .scale(1.05f)
                .blur(blur)
                .alpha(brightness.coerceIn(0.35f, 1.15f)),
        )
        EchoBrightnessOverlay(brightness)
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun EchoVideoWallpaper(
    uri: String,
    blur: androidx.compose.ui.unit.Dp,
    brightness: Float,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            playWhenReady = true
            setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
            prepare()
        }
    }
    DisposableEffect(player, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> player.play()
                Lifecycle.Event.ON_STOP -> player.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    this.player = player
                }
            },
            update = { view ->
                if (view.player !== player) view.player = player
            },
            modifier = Modifier
                .fillMaxSize()
                .scale(1.05f)
                .blur(blur)
                .alpha(brightness.coerceIn(0.35f, 1.15f)),
        )
        EchoBrightnessOverlay(brightness)
    }
}

@Composable
private fun EchoBrightnessOverlay(brightness: Float) {
    val clamped = brightness.coerceIn(0.35f, 1.15f)
    val overlay = if (clamped < 1f) {
        Color.Black.copy(alpha = ((1f - clamped) * 0.72f).coerceIn(0f, 0.42f))
    } else {
        Color.White.copy(alpha = ((clamped - 1f) * 0.35f).coerceIn(0f, 0.12f))
    }
    Box(Modifier.fillMaxSize().background(overlay))
}

@Composable
private fun EchoBackgroundGlassOverlay(glass: Float) {
    val dark = LocalEchoDarkTheme.current
    val readableGlass = if (dark) glass.coerceAtLeast(0.58f) else glass
    val colors = if (dark) {
        listOf(
            EchoGlassNight.copy(alpha = (readableGlass * 0.50f).coerceIn(0.28f, 0.60f)),
            EchoGlassInk.copy(alpha = (readableGlass * 0.42f).coerceIn(0.24f, 0.52f)),
            EchoGlassPanel.copy(alpha = (readableGlass * 0.54f).coerceIn(0.32f, 0.64f)),
        )
    } else {
        listOf(
            Color.White.copy(alpha = (glass * 0.68f).coerceIn(0f, 0.72f)),
            Color.White.copy(alpha = (glass * 0.42f).coerceIn(0f, 0.54f)),
            Color.White.copy(alpha = (glass * 0.74f).coerceIn(0f, 0.78f)),
        )
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(colors),
            ),
    )
    if (dark) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            EchoGlassCyan.copy(alpha = (glass * 0.04f).coerceIn(0f, 0.08f)),
                            Color.Transparent,
                            EchoGlassViolet.copy(alpha = (glass * 0.05f).coerceIn(0f, 0.09f)),
                        ),
                    ),
                ),
        )
    }
}
