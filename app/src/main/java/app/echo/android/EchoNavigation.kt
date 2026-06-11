package app.echo.android

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoAccent
import app.echo.android.design.EchoGlassPanel
import app.echo.android.design.echoDarkGlassBorder
import app.echo.android.design.LocalEchoDarkTheme
import kotlin.math.abs

private val DockItemMotionEasing = CubicBezierEasing(0.16f, 1f, 0.30f, 1f)
private val DockGlassShape = RoundedCornerShape(31.dp)
private val DockItemShape = RoundedCornerShape(22.dp)
private val DockSelectedBlue = Color(0xFF7DD3FC)

enum class EchoTab(
    val label: String,
    val icon: ImageVector,
) {
    Now("我的音乐", Icons.Rounded.Home),
    Library("曲库", Icons.Rounded.LibraryMusic),
    Connect("互联", Icons.Rounded.Devices),
    Diagnostics("信号", Icons.Rounded.GraphicEq),
}

@Composable
fun BottomDock(
    selectedTab: Int,
    selectedTabProgress: Float = selectedTab.toFloat(),
    onLightSurface: Boolean,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = LocalEchoDarkTheme.current
    val density = LocalDensity.current
    val scheme = MaterialTheme.colorScheme
    val tabCount = EchoTab.entries.size
    val swipeThresholdPx = with(density) { 46.dp.toPx() }
    var dragOffsetX by remember { mutableStateOf(0f) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 5.dp)
                .shadow(
                    elevation = if (dark) 18.dp else 8.dp,
                    shape = DockGlassShape,
                    ambientColor = if (dark) Color.Black.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.05f),
                    spotColor = if (dark) EchoAccent.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.06f),
                )
                .clip(DockGlassShape)
                .background(if (dark) scheme.surface.copy(alpha = 0.94f) else Color.White.copy(alpha = 0.72f))
                .background(
                    if (dark) {
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.07f),
                                scheme.surfaceVariant.copy(alpha = 0.72f),
                                scheme.surface.copy(alpha = 0.96f),
                                EchoGlassPanel.copy(alpha = 0.88f),
                            ),
                        )
                    } else {
                        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.72f), Color(0xFFEAF2FF).copy(alpha = 0.86f)))
                    },
                )
                .border(
                    if (dark) echoDarkGlassBorder() else BorderStroke(1.dp, Color.White.copy(alpha = 0.82f)),
                    DockGlassShape,
                )
                .pointerInput(selectedTab, swipeThresholdPx) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragOffsetX = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            dragOffsetX += dragAmount
                        },
                        onDragCancel = { dragOffsetX = 0f },
                        onDragEnd = {
                            if (abs(dragOffsetX) >= swipeThresholdPx) {
                                val targetTab = if (dragOffsetX < 0f) {
                                    (selectedTab + 1).coerceAtMost(EchoTab.entries.lastIndex)
                                } else {
                                    (selectedTab - 1).coerceAtLeast(0)
                                }
                                if (targetTab != selectedTab) {
                                    onSelectTab(targetTab)
                                }
                            }
                            dragOffsetX = 0f
                        },
                    )
                }
                .padding(horizontal = 2.dp, vertical = 3.dp),
        ) {
            if (dark) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.22f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
            }
            val tabWidth = maxWidth / tabCount
            val tabWidthPx = with(density) { tabWidth.toPx() }.coerceAtLeast(1f)
            val dragProgress = (-dragOffsetX / tabWidthPx).coerceIn(-1f, 1f)
            val targetIndicatorProgress = (selectedTabProgress + dragProgress)
                .coerceIn(0f, EchoTab.entries.lastIndex.toFloat())
            val indicatorProgress by animateFloatAsState(
                targetValue = targetIndicatorProgress,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "dock-selected-pill-position",
            )
            val indicatorBrush = when {
                onLightSurface -> Brush.horizontalGradient(
                    listOf(
                        scheme.primary.copy(alpha = 0.10f),
                        scheme.primary.copy(alpha = 0.16f),
                    ),
                )
                else -> Brush.horizontalGradient(
                    listOf(
                        DockSelectedBlue.copy(alpha = 0.20f),
                        scheme.surfaceVariant.copy(alpha = 0.72f),
                        Color.White.copy(alpha = 0.06f),
                    ),
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = tabWidth * indicatorProgress)
                    .width(tabWidth)
                    .height(50.dp)
                    .padding(horizontal = 3.dp, vertical = 2.dp)
                    .clip(DockItemShape)
                    .background(indicatorBrush),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EchoTab.entries.forEach { tab ->
                    DockItem(
                        tab = tab,
                        selected = selectedTab == tab.ordinal,
                        onLightSurface = onLightSurface,
                        onClick = { onSelectTab(tab.ordinal) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DockItem(
    tab: EchoTab,
    selected: Boolean,
    onLightSurface: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val targetIconColor = when {
        selected && onLightSurface -> scheme.onSurface
        selected -> DockSelectedBlue
        onLightSurface -> scheme.onSurfaceVariant
        else -> Color.White.copy(alpha = 0.70f)
    }
    val targetLabelColor = when {
        selected && onLightSurface -> scheme.onSurface
        selected -> Color.White.copy(alpha = 0.96f)
        onLightSurface -> scheme.onSurfaceVariant
        else -> Color.White.copy(alpha = 0.72f)
    }
    val iconColor by animateColorAsState(
        targetValue = targetIconColor,
        animationSpec = tween(durationMillis = 180, easing = DockItemMotionEasing),
        label = "dock-icon-color",
    )
    val labelColor by animateColorAsState(
        targetValue = targetLabelColor,
        animationSpec = tween(durationMillis = 180, easing = DockItemMotionEasing),
        label = "dock-label-color",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.04f else 0.90f,
        animationSpec = tween(durationMillis = 220, easing = DockItemMotionEasing),
        label = "dock-icon-scale",
    )
    Box(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .defaultMinSize(minWidth = 56.dp, minHeight = 48.dp)
                .clip(DockItemShape)
                .padding(horizontal = 2.dp, vertical = 1.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                tab.icon,
                contentDescription = tab.label,
                tint = iconColor,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
            )
            Text(
                text = tab.label,
                color = labelColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
