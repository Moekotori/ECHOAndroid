package app.echo.android

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import app.echo.android.design.R as DesignR

private const val SplashDurationMillis = 600L
private const val FadeDurationMillis = 140f
private val SplashMarkSize = 128.dp
private val WordmarkFont = FontFamily(Font(DesignR.font.outfit_500, FontWeight.Medium))

@Composable
internal fun EchoLaunchSplash(state: EchoLaunchSplashState, dark: Boolean) {
    if (state.finished || !state.started || state.lightweight != false) return

    val elapsed = remember { mutableFloatStateOf(0f) }
    val background = state.background
    val backgroundImage = remember(background) { background?.asImageBitmap() }
    val palette = when {
        backgroundImage != null -> ImagePalette
        dark -> DarkPalette
        else -> LightPalette
    }
    val wordmarkSize = with(LocalDensity.current) { 13.dp.toSp() }

    LaunchedEffect(state) {
        val start = withFrameNanos { it }
        while (true) {
            val millis = (withFrameNanos { it } - start) / 1_000_000f
            if (millis >= SplashDurationMillis) {
                state.finish()
                break
            }
            elapsed.floatValue = millis
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
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.ModulateAlpha
                alpha = ((SplashDurationMillis - elapsed.floatValue) / FadeDurationMillis).coerceIn(0f, 1f)
            }
            .drawWithCache {
                val glow = Brush.radialGradient(
                    colors = listOf(palette.glow, palette.surface),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = 220.dp.toPx(),
                )
                onDrawBehind {
                    if (backgroundImage != null) drawCenterCrop(backgroundImage)
                    drawRect(glow)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Balance the label and spinner so the badge stays centered over the system icon.
            Spacer(Modifier.height(58.dp))
            Image(
                painter = painterResource(R.drawable.echo_desktop_launch_mark),
                contentDescription = null,
                modifier = Modifier.size(SplashMarkSize),
            )
            Spacer(Modifier.height(14.dp))
            Box(Modifier.height(18.dp), contentAlignment = Alignment.Center) {
                BasicText(
                    text = "ECHO",
                    style = TextStyle(
                        color = palette.ink,
                        fontFamily = WordmarkFont,
                        fontSize = wordmarkSize,
                        letterSpacing = wordmarkSize * 0.26f,
                    ),
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(8.dp))
            EchoLaunchSpinner(elapsed, palette.ink)
        }
    }
}

@Composable
private fun EchoLaunchSpinner(elapsed: FloatState, color: Color) {
    Spacer(
        Modifier
            .size(18.dp)
            .drawWithCache {
                val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                val diameter = (size.minDimension - stroke.width).coerceAtLeast(0f)
                val arcSize = Size(diameter, diameter)
                val inset = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                val trackColor = color.copy(alpha = 0.18f)
                onDrawBehind {
                    drawCircle(trackColor, radius = diameter / 2f, style = stroke)
                    drawArc(
                        color = color,
                        startAngle = -90f + elapsed.floatValue * (360f / 640f),
                        sweepAngle = 96f,
                        useCenter = false,
                        topLeft = inset,
                        size = arcSize,
                        style = stroke,
                    )
                }
            },
    )
}

private class SplashPalette(
    val surface: Color,
    val glow: Color,
    val ink: Color,
)

private val LightPalette = SplashPalette(
    surface = Color(0xFFEAF1FB),
    glow = Color(0xFFF8FBFF),
    ink = Color(0xFF324865),
)

private val DarkPalette = SplashPalette(
    surface = Color(0xFF0F1830),
    glow = Color(0xFF192A43),
    ink = Color(0xFFE2EFFB),
)

private val ImagePalette = SplashPalette(
    surface = Color(0x20000000),
    glow = Color(0x800B1424),
    ink = Color.White,
)

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
}
