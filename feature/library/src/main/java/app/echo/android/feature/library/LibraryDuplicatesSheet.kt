package app.echo.android.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.*
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryDuplicatesSheet(status: LibraryDuplicatesState, onAnalyze: () -> Unit, onCancel: () -> Unit,
    onEnabled: (Boolean) -> Unit, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val height = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val locale = LocalResources.current.configuration.locales[0]
    val updated = remember(status.updatedAt, locale) { if (status.updatedAt > 0)
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale).format(Date(status.updatedAt)) else "" }
    var rules by rememberSaveable { mutableStateOf(false) }
    EchoModalSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = if (height > 0.dp) minOf(600.dp, height * 0.86f) else 600.dp)) {
            EchoSheetHeader(stringResource(R.string.duplicates_title), subtitle = stringResource(R.string.duplicates_preserved), trailing = {
                IconButton(onClick = onDismiss) { EchoIcon(Icons.Rounded.Close, stringResource(R.string.track_versions_close)) }
            })
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (status.updatedAt > 0) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        DuplicateMetric(status.groups, stringResource(R.string.duplicates_groups_label), Modifier.weight(1f))
                        VerticalDivider(Modifier.height(34.dp).padding(horizontal = 20.dp), color = scheme.outlineVariant)
                        DuplicateMetric(status.hidden, stringResource(R.string.duplicates_folded_label), Modifier.weight(1f))
                    }
                    Text(stringResource(R.string.duplicates_updated, updated), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                } else Text(stringResource(R.string.duplicates_not_analyzed), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                HorizontalDivider(color = scheme.outlineVariant)
                val canToggle = !status.working && status.updatedAt > 0
                Row(Modifier.fillMaxWidth().toggleable(status.enabled, enabled = canToggle, role = Role.Switch, onValueChange = onEnabled)
                    .padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.duplicates_enabled), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.duplicates_short_scope), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                    EchoSwitch(status.enabled, null, enabled = canToggle)
                }
                if (status.working) {
                    if (!LocalEchoEffectivePerformanceMode.current.isLightweight) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(if (status.cancelling) stringResource(R.string.duplicates_cancelling) else stringResource(R.string.duplicates_progress, status.checked),
                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                if (status.scanBlocked) Text(stringResource(R.string.duplicates_scan_blocked), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                if (status.error) Text(stringResource(R.string.duplicates_failed), style = MaterialTheme.typography.bodySmall, color = scheme.error)
                if (status.skipped > 0) Text(stringResource(R.string.duplicates_skipped, status.skipped), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                TextButton(onClick = { rules = !rules }, contentPadding = PaddingValues(0.dp), colors = ButtonDefaults.textButtonColors(contentColor = scheme.onSurface)) {
                    Text(stringResource(R.string.duplicates_rules))
                    EchoIcon(if (rules) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, Modifier.padding(start = 6.dp).size(18.dp))
                }
                if (rules) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(R.string.duplicates_scope, R.string.duplicates_quality_note, R.string.duplicates_refresh_note).forEach {
                        Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                }
            }
            Button(onClick = if (status.working) onCancel else onAnalyze, enabled = !status.cancelling,
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 20.dp).heightIn(min = 48.dp)) {
                Text(stringResource(if (status.working) R.string.duplicates_cancel else R.string.duplicates_analyze))
            }
        }
    }
}

@Composable
private fun DuplicateMetric(value: Int, label: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value.toString(), style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
