package app.echo.android.ui.shell

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.echo.android.EchoNavigationRail
import app.echo.android.design.EchoLayoutInfo
import app.echo.android.design.EchoLayoutBounds
import androidx.compose.ui.platform.LocalDensity

/** Reserve horizontal space for navigation, leaving short windows more room for content. */
@Composable
internal fun EchoAdaptiveShell(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selectedTabProgress: () -> Float = { selectedTab.toFloat() },
    content: @Composable BoxScope.(sideNavigation: Boolean) -> Unit,
) {
    BoxWithConstraints(modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
        val layout = EchoLayoutInfo(maxWidth.value, maxHeight.value, LocalDensity.current.fontScale)
        val railWidth = layout.navigationRailWidthDp.dp
        val sideNavigation = railWidth > 0.dp
        Row(Modifier.fillMaxSize()) {
            // Keep the content slot in the same position when the window changes shape.
            Box(Modifier.width(railWidth).fillMaxHeight()) {
                if (sideNavigation) {
                    EchoNavigationRail(selectedTab, onSelectTab, selectedTabProgress,
                        showLabels = !layout.shortLandscape)
                }
            }
            EchoLayoutBounds(Modifier.weight(1f).fillMaxHeight()) { content(sideNavigation) }
        }
    }
}
