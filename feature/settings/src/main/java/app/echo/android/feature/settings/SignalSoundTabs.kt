package app.echo.android.feature.settings

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Keep all sound tools reachable without competing with the app's horizontal page gesture. */
@Composable
internal fun SignalSoundTabs(selectedIndex: Int, labels: List<String>, onSelect: (Int) -> Unit) {
    TabRow(selectedTabIndex = selectedIndex, containerColor = Color.Transparent) {
        labels.forEachIndexed { index, label ->
            Tab(
                selected = selectedIndex == index,
                onClick = { onSelect(index) },
                modifier = Modifier.heightIn(min = 48.dp),
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Text(label, Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
