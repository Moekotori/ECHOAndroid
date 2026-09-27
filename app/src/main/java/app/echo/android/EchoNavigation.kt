package app.echo.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.echoBackdropGlass
import app.echo.android.design.echoPressFeedback
import app.echo.android.design.echoEdgeLight
import app.echo.android.design.echoTheme
import kotlin.math.abs

private val DockItemShape = RoundedCornerShape(24.dp)

enum class EchoTab(
    val icon: ImageVector,
) {
    Now(Icons.Rounded.Home),
    Library(Icons.Rounded.LibraryMusic),
    Connect(Icons.Rounded.Devices),
    Diagnostics(Icons.Rounded.GraphicEq),
}

@Composable
private fun EchoTab.label(): String =
    stringResource(
        when (this) {
            EchoTab.Now -> R.string.tab_home
            EchoTab.Library -> R.string.tab_library
            EchoTab.Connect -> R.string.tab_connect
            EchoTab.Diagnostics -> R.string.tab_diagnostics
        },
    )

@Composable
fun BottomDock(
    selectedTab: Int,
    onLightSurface: Boolean,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selectedTabProgress: () -> Float = { selectedTab.toFloat() },
    gestureModifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = echoTheme().accent
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val progressState = rememberUpdatedState(selectedTabProgress)
    val progress = remember { { progressState.value().coerceIn(0f, EchoTab.entries.lastIndex.toFloat()) } }
    val indicatorColor = if (onLightSurface) scheme.primary else accent
    val indicatorBrush = remember(indicatorColor, onLightSurface) {
        Brush.verticalGradient(
            listOf(
                indicatorColor.copy(alpha = if (onLightSurface) 0.18f else 0.24f),
                indicatorColor.copy(alpha = if (onLightSurface) 0.10f else 0.12f),
            ),
        )
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .echoBackdropGlass(cornerRadius = 28.dp, elevation = 6.dp)
            .padding(4.dp)
            .then(gestureModifier),
    ) {
        // Equal-width tabs share this local coordinate space. No global layout callbacks
        // or second animation are needed: the pager already animates taps and swipes.
        Box(
            Modifier.matchParentSize().drawWithCache {
                val tabWidth = size.width / EchoTab.entries.size
                val indicatorWidth = minOf(56.dp.toPx(), tabWidth)
                val indicatorHeight = 40.dp.toPx()
                val indicatorSize = Size(indicatorWidth, indicatorHeight)
                val corner = CornerRadius(20.dp.toPx())
                val glintRadius = 24.dp.toPx()
                val glint = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = if (onLightSurface) 0.18f else 0.12f), Color.Transparent),
                    center = Offset.Zero, radius = glintRadius,
                )
                onDrawBehind {
                    val pageProgress = progress()
                    val logicalCenter = (pageProgress + 0.5f) * tabWidth
                    val centerX = if (layoutDirection == LayoutDirection.Rtl) {
                        size.width - logicalCenter
                    } else {
                        logicalCenter
                    }
                    drawRoundRect(
                        brush = indicatorBrush,
                        topLeft = Offset(centerX - indicatorWidth / 2f, (size.height - indicatorHeight) / 2f),
                        size = indicatorSize,
                        cornerRadius = corner,
                    )
                    if (!lightweight) {
                        val fraction = pageProgress - kotlin.math.floor(pageProgress)
                        val motionLight = 4f * fraction * (1f - fraction)
                        translate(left = centerX, top = size.height * 0.3f) {
                            drawCircle(glint, glintRadius, Offset.Zero, alpha = motionLight)
                        }
                    }
                }
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EchoTab.entries.forEach { tab ->
                DockItem(
                    tab = tab,
                    selected = selectedTab == tab.ordinal,
                    onLightSurface = onLightSurface,
                    progress = progress,
                    onClick = { onSelectTab(tab.ordinal) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DockItem(
    tab: EchoTab,
    selected: Boolean,
    onLightSurface: Boolean,
    progress: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = echoTheme().accent
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val activeColor = if (onLightSurface) scheme.primary else accent
    val idleColor = if (onLightSurface) scheme.onSurfaceVariant else Color.White.copy(alpha = 0.70f)
    val painter = rememberVectorPainter(tab.icon)
    val label = tab.label()
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(DockItemShape)
            .echoPressFeedback(interactionSource)
            .echoEdgeLight(interactionSource, activeColor, 24.dp, drawEdge = false)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        // Tint and scale follow the same fractional page position as the highlight.
        // Read progress only while drawing, so swipes do not recompose the tab row.
        Canvas(Modifier.size(24.dp)) {
            val active = (1f - abs(progress() - tab.ordinal)).coerceIn(0f, 1f)
            val iconScale = if (lightweight) 1f else 0.94f + active * 0.06f
            scale(iconScale) {
                with(painter) {
                    draw(size, colorFilter = ColorFilter.tint(lerp(idleColor, activeColor, active)))
                }
            }
        }
    }
}
