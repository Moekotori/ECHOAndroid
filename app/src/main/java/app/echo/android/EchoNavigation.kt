package app.echo.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.echoPressFeedback
import app.echo.android.design.echoTheme
import kotlin.math.abs

private val DockItemShape = RoundedCornerShape(10.dp)

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
    val progress = remember {
        { progressState.value().coerceIn(0f, EchoTab.entries.lastIndex.toFloat()) }
    }
    val activeColor = if (onLightSurface) scheme.primary else accent
    val idleColor = if (onLightSurface) scheme.onSurfaceVariant else Color.White.copy(alpha = 0.68f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(gestureModifier)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        // 四个等宽页签。指示条在绘制时读 pager 进度，滑动不重组这一行。
        Box(
            Modifier.matchParentSize().drawWithCache {
                val tabCount = EchoTab.entries.size
                val tabWidth = size.width / tabCount
                val indicatorWidth = minOf(28.dp.toPx(), tabWidth - 8.dp.toPx())
                val indicatorHeight = 2.dp.toPx()
                val indicatorSize = Size(indicatorWidth, indicatorHeight)
                val corner = CornerRadius(indicatorHeight / 2f)
                val top = 0f
                onDrawBehind {
                    val pageProgress = progress()
                    val logicalCenter = (pageProgress + 0.5f) * tabWidth
                    val centerX = if (layoutDirection == LayoutDirection.Rtl) {
                        size.width - logicalCenter
                    } else {
                        logicalCenter
                    }
                    drawRoundRect(
                        color = activeColor,
                        topLeft = Offset(centerX - indicatorWidth / 2f, top),
                        size = indicatorSize,
                        cornerRadius = corner,
                    )
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
                    activeColor = activeColor,
                    idleColor = idleColor,
                    lightweight = lightweight,
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
    activeColor: Color,
    idleColor: Color,
    lightweight: Boolean,
    progress: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val painter = rememberVectorPainter(tab.icon)
    val label = tab.label()
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(DockItemShape)
            .echoPressFeedback(interactionSource)
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
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Canvas(Modifier.size(22.dp)) {
            val active = (1f - abs(progress() - tab.ordinal)).coerceIn(0f, 1f)
            val iconScale = if (lightweight) 1f else 0.94f + active * 0.06f
            scale(iconScale) {
                with(painter) {
                    draw(size, colorFilter = ColorFilter.tint(lerp(idleColor, activeColor, active)))
                }
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = if (selected) activeColor else idleColor, maxLines = 1,
            modifier = Modifier.clearAndSetSemantics { })
        }
    }
}
