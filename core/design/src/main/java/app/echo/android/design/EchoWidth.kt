package app.echo.android.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.echo.android.model.settings.EchoWidthSizeClass

val LocalEchoWidthSizeClass = staticCompositionLocalOf { EchoWidthSizeClass.Compact }

val LocalEchoContentMaxWidth = staticCompositionLocalOf { EchoContentMaxWidth }

@Composable
fun rememberEchoWidthSizeClass(): EchoWidthSizeClass {
    val window = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current.density
    val widthDp = if (window.width > 0) (window.width / density).toInt() else LocalConfiguration.current.screenWidthDp
    return remember(widthDp) { EchoWidthSizeClass.fromWidthDp(widthDp) }
}

fun EchoWidthSizeClass.contentMaxWidth(): Dp = contentMaxWidthDp().dp

/** Window bounds also cover split-screen and freeform resizing, without an orientation lock. */
@Composable
fun echoShortLandscape(): Boolean {
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current.density
    return size.width > size.height && size.width / density >= 480f && size.height / density < 600f
}
