package app.echo.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.echoPressFeedback
import app.echo.android.design.echoChromeColors
import app.echo.android.design.rememberEchoHapticPerformer
import kotlin.math.abs

private val DockItemShape = RoundedCornerShape(10.dp)

enum class EchoTab(
    val icon: ImageVector,
) {
    Now(EchoNavigationIcons.Home),
    Library(EchoNavigationIcons.Library),
    Connect(EchoNavigationIcons.Connect),
    Diagnostics(EchoNavigationIcons.Diagnostics),
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
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selectedTabProgress: () -> Float = { selectedTab.toFloat() },
    gestureModifier: Modifier = Modifier,
) {
    val chrome = echoChromeColors()
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val progressState = rememberUpdatedState(selectedTabProgress)
    val progress = remember {
        { progressState.value().coerceIn(0f, EchoTab.entries.lastIndex.toFloat()) }
    }
    val activeColor = chrome.content
    val idleColor = chrome.secondary
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(gestureModifier)
            .padding(horizontal = 12.dp),
    ) {
        // Equal targets; pager motion only changes the icons' drawing, never the row's layout.
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
internal fun EchoNavigationRail(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    selectedTabProgress: () -> Float = { selectedTab.toFloat() },
) {
    val chrome = echoChromeColors()
    Column(
        modifier = Modifier.fillMaxHeight().safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 12.dp)
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        EchoTab.entries.forEach { tab ->
            RailItem(
                tab = tab,
                selected = selectedTab == tab.ordinal,
                activeColor = chrome.content,
                idleColor = chrome.secondary,
                progress = selectedTabProgress,
                onClick = { onSelectTab(tab.ordinal) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Landscape navigation is a quiet icon strip; labels remain available to accessibility. */
@Composable
private fun RailItem(
    tab: EchoTab,
    selected: Boolean,
    activeColor: Color,
    idleColor: Color,
    progress: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = tab.label()
    val painter = rememberVectorPainter(tab.icon)
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberEchoHapticPerformer()
    Box(
        modifier.height(48.dp).clip(DockItemShape)
            .echoPressFeedback(interaction)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = ripple(color = activeColor),
                role = Role.Tab,
                onClick = { if (!selected) haptics.tick(); onClick() },
            ).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().height(48.dp)) {
            val active = (1f - abs(progress() - tab.ordinal)).coerceIn(0f, 1f)
            val iconSize = 22.dp.toPx()
            val color = lerp(idleColor.copy(alpha = 0.7f), activeColor, active)
            drawRoundRect(
                color = activeColor.copy(alpha = active),
                topLeft = androidx.compose.ui.geometry.Offset(0f, (size.height - 16.dp.toPx()) / 2f),
                size = androidx.compose.ui.geometry.Size(2.dp.toPx(), 16.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()),
            )
            withTransform({
                translate((size.width - iconSize) / 2f, (size.height - iconSize) / 2f)
            }) {
                with(painter) {
                    draw(androidx.compose.ui.geometry.Size(iconSize, iconSize), colorFilter = ColorFilter.tint(color))
                }
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
    val haptics = rememberEchoHapticPerformer()
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(DockItemShape)
            .echoPressFeedback(interactionSource)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = ripple(color = activeColor),
                role = Role.Tab,
                onClick = {
                    if (!selected) haptics.tick()
                    onClick()
                },
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Canvas(Modifier.size(24.dp)) {
                val active = (1f - abs(progress() - tab.ordinal)).coerceIn(0f, 1f)
                val iconScale = if (lightweight) 1f else 0.94f + active * 0.06f
                scale(iconScale) {
                    with(painter) {
                        draw(size, colorFilter = ColorFilter.tint(lerp(idleColor, activeColor, active)))
                    }
                }
            }
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = activeColor, maxLines = 1,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clearAndSetSemantics { }.graphicsLayer {
                    val active = (1f - abs(progress() - tab.ordinal)).coerceIn(0f, 1f)
                    alpha = 0.58f + 0.42f * active
                })
        }
    }
}
