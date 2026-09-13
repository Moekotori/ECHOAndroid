package app.echo.android.feature.settings

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** A quieter second navigation level beneath the page's main segmented control. */
@Composable
internal fun SignalSoundTabs(selectedIndex: Int, labels: List<String>, onSelect: (Int) -> Unit) {
    TabRow(selectedTabIndex = selectedIndex, containerColor = Color.Transparent) {
        labels.forEachIndexed { index, label ->
            Tab(
                selected = selectedIndex == index,
                onClick = { onSelect(index) },
                text = { Text(label, maxLines = 2) },
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
