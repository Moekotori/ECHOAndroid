package app.echo.android.ui.shell

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.echo.android.EchoNavigationRail
import app.echo.android.design.LocalEchoContentMaxWidth
import app.echo.android.design.LocalEchoWidthSizeClass
import app.echo.android.design.contentMaxWidth
import app.echo.android.model.settings.EchoWidthSizeClass

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
        val sideNavigation = maxWidth >= 600.dp && maxWidth > maxHeight && maxHeight < 600.dp
        val railWidth = if (sideNavigation) 56.dp else 0.dp
        val contentWidthClass = EchoWidthSizeClass.fromWidthDp((maxWidth - railWidth).value.toInt())
        Row(Modifier.fillMaxSize()) {
            // Keep the content slot in the same position when the window changes shape.
            Box(Modifier.width(railWidth).fillMaxHeight()) {
                if (sideNavigation) EchoNavigationRail(selectedTab, onSelectTab, selectedTabProgress)
            }
            CompositionLocalProvider(
                LocalEchoWidthSizeClass provides contentWidthClass,
                LocalEchoContentMaxWidth provides contentWidthClass.contentMaxWidth(),
            ) {
                Box(Modifier.weight(1f).fillMaxHeight()) { content(sideNavigation) }
            }
        }
    }
}
