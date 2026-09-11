package app.echo.android

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import app.echo.android.design.echoClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.echoTheme
import app.echo.android.design.EchoMotion
import app.echo.android.design.LocalEchoDarkTheme
import kotlin.math.abs
import kotlinx.coroutines.flow.collectLatest

private val DockItemMotionEasing = EchoMotion.Silk
private val DockGlassShape = RoundedCornerShape(26.dp)
private val DockItemShape = RoundedCornerShape(22.dp)


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
    progressLive: Boolean = false,
) {
    val dark = LocalEchoDarkTheme.current
    val density = LocalDensity.current
    val scheme = MaterialTheme.colorScheme
    val theme = echoTheme()
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
                .padding(horizontal = 10.dp, vertical = 5.dp)
                .shadow(
                    elevation = 10.dp,
                    shape = DockGlassShape,
                    ambientColor = if (dark) Color.Black.copy(alpha = 0.18f) else scheme.onSurface.copy(alpha = 0.08f),
                    spotColor = if (dark) Color.Black.copy(alpha = 0.10f) else scheme.onSurface.copy(alpha = 0.10f),
                )
                .clip(DockGlassShape)
                .background(if (dark) scheme.surface.copy(alpha = 0.92f) else scheme.surface.copy(alpha = 0.94f))
                .background(
                    if (dark) {
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.07f),
                                Color.White.copy(alpha = 0.02f),
                                Color.Transparent,
                            ),
                        )
                    } else {
                        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.80f), theme.mist.copy(alpha = 0.30f)))
                    },
                )
                .border(
                    BorderStroke(
                        0.75.dp,
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = if (dark) 0.18f else 0.95f),
                                scheme.onSurface.copy(alpha = if (dark) 0.05f else 0.07f),
                            ),
                        ),
                    ),
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
                .padding(horizontal = 4.dp, vertical = 5.dp),
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
                                    Color.White.copy(alpha = 0.08f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
            }
            val tabWidth = maxWidth / tabCount
            val tabWidthPx = with(density) { tabWidth.toPx() }.coerceAtLeast(1f)
            val maxIndicatorIndex = EchoTab.entries.lastIndex.toFloat()
            val progressLiveState = rememberUpdatedState(progressLive)
            val selectedTabProgressState = rememberUpdatedState(selectedTabProgress)
            val indicatorAnim = remember { Animatable(selectedTabProgress().coerceIn(0f, maxIndicatorIndex)) }
            // 手指驱动(pager 滑动 / dock 拖拽)时指示条 1:1 直跟,离散跳转(点按)才走弹簧,
            // 避免弹簧追赶连续目标带来的滞后感。
            LaunchedEffect(tabWidthPx) {
                snapshotFlow {
                    val dragProgress = (-dragOffsetX / tabWidthPx).coerceIn(-1f, 1f)
                    val target = (selectedTabProgressState.value() + dragProgress)
                        .coerceIn(0f, maxIndicatorIndex)
                    target to (progressLiveState.value || dragOffsetX != 0f)
                }.collectLatest { (target, live) ->
                    if (live) {
                        indicatorAnim.snapTo(target)
                    } else if (target != indicatorAnim.targetValue || target != indicatorAnim.value) {
                        indicatorAnim.animateTo(
                            targetValue = target,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = 420f,
                            ),
                        )
                    }
                }
            }
            val indicatorBrush = when {
                onLightSurface -> Brush.horizontalGradient(
                    listOf(
                        scheme.primary.copy(alpha = 0.09f),
                        scheme.primary.copy(alpha = 0.05f),
                    ),
                )
                else -> Brush.horizontalGradient(
                    listOf(
                        theme.accent.copy(alpha = 0.19f),
                        theme.accent.copy(alpha = 0.08f),
                    ),
                )
            }
            // Measure the actual item content, including font scale and RTL placement.
            // Read animation progress during drawing so every tick does not recompose the dock.
            var rowOrigin by remember { mutableStateOf(Offset.Zero) }
            val itemBounds = remember { mutableStateMapOf<Int, Rect>() }
            Box(Modifier.fillMaxWidth()) {
                Canvas(Modifier.matchParentSize()) {
                    val progress = indicatorAnim.value.coerceIn(0f, maxIndicatorIndex)
                    val from = progress.toInt()
                    val to = (from + 1).coerceAtMost(EchoTab.entries.lastIndex)
                    val fromBounds = itemBounds[from] ?: return@Canvas
                    val toBounds = itemBounds[to] ?: fromBounds
                    val bounds = lerp(fromBounds, toBounds, progress - from)
                    drawRoundRect(
                        brush = indicatorBrush,
                        topLeft = Offset(
                            bounds.center.x - rowOrigin.x - 21.dp.toPx(),
                            bounds.top - rowOrigin.y,
                        ),
                        size = Size(42.dp.toPx(), 30.dp.toPx()),
                        cornerRadius = CornerRadius(12.dp.toPx()),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { rowOrigin = it.positionInRoot() },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EchoTab.entries.forEach { tab ->
                        DockItem(
                            tab = tab,
                            selected = selectedTab == tab.ordinal,
                            onLightSurface = onLightSurface,
                            onClick = { onSelectTab(tab.ordinal) },
                            onContentBounds = { itemBounds[tab.ordinal] = it },
                            modifier = Modifier.weight(1f),
                        )
                    }
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
    onContentBounds: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = echoTheme().accent
    val targetIconColor = when {
        selected && onLightSurface -> scheme.onSurface
        selected -> accent
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
        targetValue = if (selected) 1f else 0.94f,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "dock-icon-scale",
    )
    Box(
        modifier = modifier
            .echoClickable(onClick = onClick)
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .onGloballyPositioned {
                    onContentBounds(
                        Rect(it.positionInRoot(), Size(it.size.width.toFloat(), it.size.height.toFloat())),
                    )
                }
                .defaultMinSize(minWidth = 56.dp, minHeight = 52.dp)
                .clip(DockItemShape)
                .padding(horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier.size(width = 42.dp, height = 30.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    tab.icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        },
                )
            }
            Text(
                text = tab.label(),
                color = labelColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
