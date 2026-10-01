package app.echo.android.feature.plugins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import app.echo.android.design.EchoIcon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.plugin.PluginInstallResult
import app.echo.android.plugin.PluginsSnapshot

@Composable
internal fun PluginsScreen(
    snapshot: PluginsSnapshot,
    notice: PluginInstallResult?,
    onImport: () -> Unit,
    onOpenTutorial: () -> Unit,
    onOpenPlugin: (String) -> Unit,
    onBack: () -> Unit,
    onDismissNotice: () -> Unit,
) {
    PluginChrome(title = stringResource(R.string.plugins_title), onBack = onBack) {
        Text(
            stringResource(R.string.plugins_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (notice != null) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(notice.noticeText(), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onDismissNotice) { Text(stringResource(R.string.plugins_dismiss_notice)) }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.plugins_import)) }
            OutlinedButton(onClick = onOpenTutorial, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.plugins_tutorial))
            }
        }
        when {
            !snapshot.loaded -> Text(stringResource(R.string.plugins_loading), style = MaterialTheme.typography.bodyMedium)
            snapshot.plugins.isEmpty() -> Text(
                stringResource(R.string.plugins_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                snapshot.plugins.forEach { plugin ->
                    Surface(
                        onClick = { onOpenPlugin(plugin.id) },
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Surface(
                                modifier = Modifier.size(8.dp),
                                shape = CircleShape,
                                color = if (plugin.enabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                            ) {}
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(plugin.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text(
                                    stringResource(
                                        R.string.plugins_row_meta,
                                        stringResource(if (plugin.enabled) R.string.plugins_state_on else R.string.plugins_state_off),
                                        stringResource(R.string.plugins_version, plugin.version),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (plugin.summary.isNotBlank()) {
                                    Text(
                                        plugin.summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                plugin.failureText()?.let { failure ->
                                    Text(failure, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                            EchoIcon(
                                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
