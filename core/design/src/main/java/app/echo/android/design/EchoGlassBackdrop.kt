package app.echo.android.design

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView

/** Separate sources keep the glass itself out of its input (no recursive capture). */
enum class EchoGlassSource { Background, Content }

internal class EchoGlassLayer(val layer: GraphicsLayer) {
    var origin by mutableStateOf(Offset.Zero)
    var ready by mutableStateOf(false)
}

internal class EchoGlassBackdrop(
    val background: EchoGlassLayer,
    val content: EchoGlassLayer,
)

internal val LocalEchoGlassBackdrop = staticCompositionLocalOf<EchoGlassBackdrop?> { null }

/** Pages may omit opaque readability scrims when the controls supply their own blur. */
@Composable
fun echoBackdropGlassActive(): Boolean = LocalEchoGlassBackdrop.current != null

/** Layers belong to this composition and are released when glass is disabled or it leaves. */
@Composable
fun EchoGlassBackdropProvider(enabled: Boolean = true, content: @Composable () -> Unit) {
    val supported = enabled && Build.VERSION.SDK_INT >= 31 &&
        !LocalEchoEffectivePerformanceMode.current.isLightweight && LocalView.current.isHardwareAccelerated
    val backdrop = if (supported) {
        val background = rememberGraphicsLayer()
        val page = rememberGraphicsLayer()
        remember(background, page) {
            EchoGlassBackdrop(EchoGlassLayer(background), EchoGlassLayer(page))
        }
    } else null
    CompositionLocalProvider(LocalEchoGlassBackdrop provides backdrop, content = content)
}

/** Records display lists, never screenshots/bitmaps. Child render nodes remain live. */
@Composable
fun Modifier.echoGlassSource(source: EchoGlassSource): Modifier {
    val backdrop = LocalEchoGlassBackdrop.current ?: return this
    val target = when (source) {
        EchoGlassSource.Background -> backdrop.background
        EchoGlassSource.Content -> backdrop.content
    }
    return this
        .onGloballyPositioned { target.origin = it.positionInRoot() }
        .drawWithContent {
            target.layer.record { this@drawWithContent.drawContent() }
            target.ready = true
            drawLayer(target.layer)
        }
}
