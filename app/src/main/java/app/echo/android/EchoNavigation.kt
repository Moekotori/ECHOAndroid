package app.echo.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoAccent
import app.echo.android.design.EchoAccentDeep
import app.echo.android.design.EchoAccentText

enum class EchoTab(
    val label: String,
    val icon: ImageVector,
) {
    Now("主页", Icons.Rounded.Home),
    Library("曲库", Icons.Rounded.LibraryMusic),
    Connect("连接", Icons.Rounded.Devices),
    Diagnostics("状态", Icons.Rounded.GraphicEq),
}

@Composable
fun BottomDock(
    selectedTab: Int,
    onLightSurface: Boolean,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(32.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.05f),
                spotColor = Color.Black.copy(alpha = 0.09f),
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White,
                        Color(0xFFFAFAFA),
                        Color(0xFFF5F5F6),
                    ),
                ),
            )
            .border(BorderStroke(1.dp, Color(0xFFE9E9EC)), shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 9.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
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

@Composable
private fun DockItem(
    tab: EchoTab,
    selected: Boolean,
    onLightSurface: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = when {
        selected && onLightSurface -> Color(0xFFFF2D55)
        selected -> EchoAccentText
        onLightSurface -> Color.Black.copy(alpha = 0.56f)
        else -> Color.White.copy(alpha = 0.68f)
    }
    val selectedBackground = if (onLightSurface) {
        Brush.verticalGradient(listOf(Color(0xFFF1F1F2), Color(0xFFF8F8F9)))
    } else {
        Brush.verticalGradient(listOf(EchoAccent.copy(alpha = 0.42f), EchoAccentDeep.copy(alpha = 0.28f)))
    }
    val selectedBorder = if (onLightSurface) Color.White.copy(alpha = 0.92f) else EchoAccent.copy(alpha = 0.50f)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .defaultMinSize(minWidth = 56.dp, minHeight = 48.dp)
                .clip(RoundedCornerShape(24.dp))
                .then(
                    if (selected) {
                        Modifier
                            .background(selectedBackground)
                            .border(BorderStroke(1.dp, selectedBorder), RoundedCornerShape(24.dp))
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 7.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Icon(tab.icon, contentDescription = tab.label, tint = contentColor, modifier = Modifier.size(21.dp))
            Text(
                text = tab.label,
                color = contentColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
