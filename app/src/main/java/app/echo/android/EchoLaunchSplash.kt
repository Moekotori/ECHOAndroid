package app.echo.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import app.echo.android.design.R as DesignR

private val LandX = floatArrayOf(0f, 0.52f, 0.72f, 0.88f, 1f)
private val LandTranslateDp = floatArrayOf(70f, -16f, 2f, -2f, 0f)
private val LandScaleX = floatArrayOf(0.72f, 0.97f, 1.04f, 0.995f, 1f)
private val LandScaleY = floatArrayOf(0.72f, 1.05f, 0.95f, 1.01f, 1f)
private val LandAlphaX = floatArrayOf(0f, 0.22f, 1f)
private val LandAlphaY = floatArrayOf(0f, 1f, 1f)

private val IdleX = floatArrayOf(0f, 0.3f, 0.5f, 0.8f, 1f)
private val IdleTranslateDp = floatArrayOf(0f, -4f, -9f, -2f, 0f)
private val IdleTiltDegrees = floatArrayOf(-1.1f, 0f, 1.1f, -0.3f, -1.1f)
private val IdleScaleX = floatArrayOf(1f, 0.995f, 0.99f, 1.004f, 1f)
private val IdleScaleY = floatArrayOf(1f, 1.012f, 1.02f, 0.996f, 1f)

/**
 * In-app launch splash drawn over the app while the system splash hands off. It is a plain
 * background until [EchoLaunchSplashState.started]; lightweight mode skips it entirely.
 */
@Composable
internal fun EchoLaunchSplash(state: EchoLaunchSplashState, dark: Boolean) {
    if (state.finished) return
    val running = state.started && state.lightweight == false
    val background = state.background
    val palette = remember(dark, background != null) { launchSplashPalette(dark, overImage = background != null) }
    val backgroundImage = remember(background) { background?.asImageBitmap() }
    val fx = remember { EchoLaunchSplashFx() }
    val fontFamily = remember {
        FontFamily(Font(DesignR.font.outfit_500, FontWeight.Medium))
    }
    fx.density = LocalDensity.current.density

    LaunchedEffect(state.started, state.lightweight) {
        if (state.started && state.lightweight == true) state.finished = true
    }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            fx.advance(((now - last) / 1_000_000_000f).coerceAtMost(0.05f))
            last = now
            val time = fx.time.floatValue
            if (fx.exitAt.floatValue < 0f && time >= LaunchSplashTiming.HoldSeconds) fx.exitAt.floatValue = time
            if (fx.exitAt.floatValue >= 0f && time - fx.exitAt.floatValue >= LaunchSplashTiming.ExitSeconds) {
                state.finished = true
                return@LaunchedEffect
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            }
            .clearAndSetSemantics { }
            .graphicsLayer { alpha = 1f - ExitEasing.transform(fx.exitProgress(0.12f, 0.6f)) },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val wash = Brush.verticalGradient(listOf(palette.skyTop, palette.skyMid, palette.skyLow))
                    onDrawBehind {
                        if (backgroundImage != null) drawCenterCrop(backgroundImage) else drawRect(wash)
                        fx.draw(this, palette)
                    }
                },
        )
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            Mascot(fx)
            Column(
                Modifier.graphicsLayer {
                    val p = ExitEasing.transform(fx.exitProgress(0.06f, 0.45f))
                    alpha = 1f - p
                    translationY = -12.dp.toPx() * p
                },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
            ) {
                Floor(fx, palette)
                Spacer(Modifier.height(22.dp))
                EchoLaunchWordmark(fx, palette, fontFamily)
                Spacer(Modifier.height(30.dp))
                Loader(fx, palette)
            }
            Spacer(Modifier.weight(1.3f))
        }
    }
}

@Composable
private fun Mascot(fx: EchoLaunchSplashFx) {
    Image(
        painter = painterResource(R.drawable.echo_mascot),
        contentDescription = null,
        modifier = Modifier
            .fillMaxWidth(0.62f)
            .widthIn(max = 260.dp)
            .aspectRatio(1f)
            .onGloballyPositioned {
                val position = it.positionInRoot()
                fx.originX = position.x + it.size.width / 2f
                fx.originY = position.y + it.size.height * 0.3f
                fx.mascotWidth = it.size.width.toFloat()
            }
            .graphicsLayer {
                val t = fx.time.floatValue
                val land = launchSplashProgress(t, LaunchSplashTiming.LandStart, LaunchSplashTiming.LandDuration)
                var ty = interpolateKeyframes(land, LandX, LandTranslateDp, LandEasing).dp.toPx()
                var sx = interpolateKeyframes(land, LandX, LandScaleX, LandEasing)
                var sy = interpolateKeyframes(land, LandX, LandScaleY, LandEasing)
                var tilt = 0f

                val idleTime = t - LaunchSplashTiming.IdleStart
                if (idleTime > 0f) {
                    val gain = (idleTime / 0.5f).coerceIn(0f, 1f)
                    val phase = fractionOf(idleTime / LaunchSplashTiming.IdleCycle)
                    ty += interpolateKeyframes(phase, IdleX, IdleTranslateDp, EaseInOut).dp.toPx() * gain
                    tilt = interpolateKeyframes(phase, IdleX, IdleTiltDegrees, EaseInOut) * gain
                    sx *= 1f + (interpolateKeyframes(phase, IdleX, IdleScaleX, EaseInOut) - 1f) * gain
                    sy *= 1f + (interpolateKeyframes(phase, IdleX, IdleScaleY, EaseInOut) - 1f) * gain
                }

                val exit = ExitEasing.transform(fx.exitProgress(0f, 0.55f))
                transformOrigin = TransformOrigin(0.5f, 0.98f)
                alpha = interpolateKeyframes(land, LandAlphaX, LandAlphaY, LandEasing) * (1f - exit)
                translationY = ty - 24.dp.toPx() * exit
                scaleX = sx * (1f + 0.1f * exit)
                scaleY = sy * (1f + 0.1f * exit)
                rotationZ = tilt
            },
    )
}

@Composable
private fun Floor(fx: EchoLaunchSplashFx, palette: LaunchSplashPalette) {
    Canvas(
        Modifier
            .size(150.dp, 30.dp)
            .graphicsLayer {
                val t = fx.time.floatValue
                val enter = ExitEasing.transform(launchSplashProgress(t, 0.55f, 0.7f))
                val idleTime = t - LaunchSplashTiming.IdleStart
                val breathe = if (idleTime > 0f) {
                    val wave = 0.5f - 0.5f * cos(2f * PI.toFloat() * idleTime / LaunchSplashTiming.IdleCycle)
                    1f - 0.22f * wave
                } else {
                    1f
                }
                val s = (0.3f + 0.7f * enter) * breathe
                alpha = enter * (0.55f + 0.45f * breathe)
                scaleX = s
                scaleY = s
            },
    ) {
        scaleOval(palette.shadow)
    }
}

private fun DrawScope.scaleOval(color: Color) {
    val radius = size.width / 2f
    scale(1f, 0.2f, pivot = center) {
        drawCircle(
            brush = Brush.radialGradient(listOf(color, Color.Transparent), center = center, radius = radius),
            radius = radius,
        )
    }
}

@Composable
private fun Loader(fx: EchoLaunchSplashFx, palette: LaunchSplashPalette) {
    Canvas(
        Modifier
            .size(112.dp, 3.dp)
            .graphicsLayer { alpha = launchSplashProgress(fx.time.floatValue, LaunchSplashTiming.LoaderStart, 0.6f) },
    ) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(palette.track, cornerRadius = radius)
        val cycle = (fx.time.floatValue - LaunchSplashTiming.LoaderStart) / 1.7f
        if (cycle < 0f) return@Canvas
        val barWidth = size.width * 0.42f
        val x = barWidth * (-1.05f + 3.45f * SweepEasing.transform(fractionOf(cycle)))
        clipRect {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(palette.accent, palette.accent2, palette.accent3),
                    startX = x,
                    endX = x + barWidth,
                ),
                topLeft = Offset(x, 0f),
                size = Size(barWidth, size.height),
                cornerRadius = radius,
            )
        }
    }
}

private fun DrawScope.drawCenterCrop(image: ImageBitmap) {
    val sourceRatio = image.width.toFloat() / image.height
    val targetRatio = size.width / size.height
    val sourceWidth: Int
    val sourceHeight: Int
    if (sourceRatio > targetRatio) {
        sourceHeight = image.height
        sourceWidth = (sourceHeight * targetRatio).toInt().coerceAtLeast(1)
    } else {
        sourceWidth = image.width
        sourceHeight = (sourceWidth / targetRatio).toInt().coerceAtLeast(1)
    }
    drawImage(
        image = image,
        srcOffset = IntOffset((image.width - sourceWidth) / 2, (image.height - sourceHeight) / 2),
        srcSize = IntSize(sourceWidth, sourceHeight),
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        filterQuality = FilterQuality.Medium,
    )
    drawRect(Color.Black.copy(alpha = 0.125f))
}
