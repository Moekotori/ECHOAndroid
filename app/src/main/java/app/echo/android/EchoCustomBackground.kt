package app.echo.android

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.media3.common.PlaybackException
import app.echo.android.design.EchoLegacyBackgroundBlur
import app.echo.android.design.backgroundMaxBlur
import androidx.core.net.toUri
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.echo.android.data.EchoAppSettings
import app.echo.android.data.EchoBackgroundMode
import app.echo.android.design.EchoGlassBackground
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.echoTheme
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun EchoCustomBackground(
    settings: EchoAppSettings,
    modifier: Modifier = Modifier,
    onLoadError: (String) -> Unit = {},
) {
    val mode = settings.customBackgroundMode
    val uri = settings.customBackgroundUri
    val effectivePerformanceMode = LocalEchoEffectivePerformanceMode.current
    val lightweight = effectivePerformanceMode.isLightweight
    val highPerformance = effectivePerformanceMode.isHighPerformance
    val customVideoDisabled = lightweight && mode == EchoBackgroundMode.Video
    val hasCustomBackground = mode != EchoBackgroundMode.Default && !uri.isNullOrBlank() && !customVideoDisabled
    val imageMaxPixelSize = when {
        lightweight -> 640
        highPerformance -> 1280
        else -> 1024
    }
    val maxBlur = effectivePerformanceMode.backgroundMaxBlur
    val blur = settings.customBackgroundBlur.coerceIn(0f, maxBlur).dp
    val brightness = settings.customBackgroundBrightness
    val glass = settings.customBackgroundGlass
    val backgroundScale = settings.customBackgroundScale.coerceIn(1.00f, 1.40f)

    var failed by remember(mode, uri) { mutableStateOf(false) }
    LaunchedEffect(failed, uri) {
        if (failed && uri != null) onLoadError(uri)
    }
    Box(modifier = modifier.fillMaxSize()) {
        EchoGlassBackground(Modifier.fillMaxSize())
        if (hasCustomBackground && !failed) {
            when (mode) {
                EchoBackgroundMode.Video -> EchoVideoWallpaper(
                    uri = uri,
                    brightness = brightness,
                    backgroundScale = backgroundScale,
                    onError = { failed = true },
                )

                EchoBackgroundMode.Image -> EchoImageWallpaper(
                    uri = uri,
                    blur = blur,
                    brightness = brightness,
                    backgroundScale = backgroundScale,
                    maxPixelSize = imageMaxPixelSize,
                    highQuality = highPerformance,
                    onError = { failed = true },
                )

                else -> Unit
            }
            EchoBackgroundGlassOverlay(glass = glass)
        }
    }
}

@Composable
private fun EchoImageWallpaper(
    uri: String,
    blur: Dp,
    brightness: Float,
    backgroundScale: Float,
    maxPixelSize: Int,
    highQuality: Boolean,
    onError: () -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val legacyBlur = if (Build.VERSION.SDK_INT < 31) blur.value else 0f
    val widthDp = configuration.screenWidthDp.coerceAtLeast(1)
    val heightDp = configuration.screenHeightDp.coerceAtLeast(1)
    val imageRequest = remember(context, uri, maxPixelSize, highQuality, legacyBlur, widthDp, heightDp) {
        val cacheKey = "$uri#px$maxPixelSize#${if (highQuality) "8888" else "565"}"
        ImageRequest.Builder(context)
            .data(uri)
            .size(maxPixelSize, maxPixelSize)
            .bitmapConfig(if (highQuality) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565)
            .memoryCacheKey("$cacheKey#blur$legacyBlur#${widthDp}x$heightDp")
            .diskCacheKey(cacheKey)
            .crossfade(false)
            .apply {
                if (legacyBlur > 0f) {
                    allowHardware(false)
                    transformations(EchoLegacyBackgroundBlur(legacyBlur, widthDp, heightDp))
                }
            }
            .build()
    }
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            onError = { onError() },
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .scale(backgroundScale)
                .then(if (Build.VERSION.SDK_INT >= 31 && blur > 0.dp) Modifier.blur(blur) else Modifier)
                .alpha(brightness.coerceIn(0.35f, 1.15f)),
        )
        EchoBrightnessOverlay(brightness)
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun EchoVideoWallpaper(
    uri: String,
    brightness: Float,
    backgroundScale: Float,
    onError: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    val currentOnError by androidx.compose.runtime.rememberUpdatedState(onError)
    DisposableEffect(uri, lifecycleOwner) {
        fun createPlayer(): ExoPlayer =
            ExoPlayer.Builder(context)
                .setLoadControl(
                    DefaultLoadControl.Builder()
                        .setBufferDurationsMs(1_000, 5_000, 500, 1_000)
                        .setTargetBufferBytes(2 * 1024 * 1024)
                        .build(),
                )
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .setUsage(C.USAGE_UNKNOWN)
                        .build(),
                    false,
                )
                .setHandleAudioBecomingNoisy(false)
                .setWakeMode(C.WAKE_MODE_NONE)
                .build()
                .apply {
                    repeatMode = Player.REPEAT_MODE_ONE
                    volume = 0f
                    playWhenReady = true
                    setMediaItem(MediaItem.fromUri(uri.toUri()))
                    prepare()
                }

        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                currentOnError()
            }
        }
        fun attach(next: ExoPlayer) {
            next.addListener(listener)
            if (next.playerError != null) currentOnError()
            player = next
        }
        fun detach() {
            val current = player
            player = null
            current?.removeListener(listener)
            current?.release()
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (player == null) attach(createPlayer())
                Lifecycle.Event.ON_STOP -> detach()
                else -> Unit
            }
        }
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            attach(createPlayer())
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            detach()
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
                .scale(backgroundScale)
                .alpha(brightness.coerceIn(0.35f, 1.15f)),
        )
        EchoBrightnessOverlay(brightness)
    }
}

@Composable
private fun EchoBrightnessOverlay(brightness: Float) {
    val dark = LocalEchoDarkTheme.current
    val clamped = brightness.coerceIn(0.35f, 1.15f)
    val overlay = if (dark) {
        val dim = if (clamped < 1f) {
            0.18f + (1f - clamped) * 0.82f
        } else {
            0.18f - (clamped - 1f) * 0.12f
        }
        Color.Black.copy(alpha = dim.coerceIn(0.10f, 0.62f))
    } else if (clamped < 1f) {
        Color.Black.copy(alpha = ((1f - clamped) * 0.72f).coerceIn(0f, 0.42f))
    } else {
        Color.White.copy(alpha = ((clamped - 1f) * 0.35f).coerceIn(0f, 0.12f))
    }
    Box(Modifier.fillMaxSize().background(overlay))
}

@Composable
private fun EchoBackgroundGlassOverlay(glass: Float) {
    val dark = LocalEchoDarkTheme.current
    val readableGlass = glass.coerceIn(0.08f, 0.90f)
    val colors = if (dark) {
        listOf(
            echoTheme().night.copy(alpha = (readableGlass * 0.90f)),
            echoTheme().ink.copy(alpha = (readableGlass * 0.82f)),
            echoTheme().night.copy(alpha = (readableGlass * 0.94f)),
        )
    } else {
        listOf(
            Color.White.copy(alpha = (0.68f + readableGlass * 0.28f).coerceAtMost(0.96f)),
            Color.White.copy(alpha = (0.60f + readableGlass * 0.30f).coerceAtMost(0.90f)),
            Color.White.copy(alpha = (0.74f + readableGlass * 0.24f).coerceAtMost(0.98f)),
        )
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(colors),
            ),
    )
}
