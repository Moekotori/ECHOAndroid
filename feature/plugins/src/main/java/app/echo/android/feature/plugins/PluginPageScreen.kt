package app.echo.android.feature.plugins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.plugin.PluginPageDocument
import app.echo.android.plugin.PluginPageItem

@Composable
internal fun PluginPageScreen(
    title: String,
    page: PluginPageDocument?,
    onBack: () -> Unit,
    onAction: (String) -> Unit,
) {
    PluginChrome(title = page?.title ?: title, onBack = onBack) {
        val items = page?.items.orEmpty()
        if (items.isEmpty()) {
            Text(stringResource(R.string.plugins_page_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items.forEach { item ->
            when (item) {
                is PluginPageItem.Text -> Text(item.text, style = MaterialTheme.typography.bodyLarge)
                is PluginPageItem.Button -> Button(onClick = { onAction(item.id) }) { Text(item.label) }
                is PluginPageItem.Rows -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item.rows.forEach { row ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(row.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                if (row.subtitle.isNotBlank()) {
                                    Text(
                                        row.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
