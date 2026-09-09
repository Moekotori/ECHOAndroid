package app.echo.android.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.LocalEchoContentMaxWidth
import app.echo.android.design.echoClickable
import app.echo.android.model.error.EchoErrorRecord
import app.echo.android.model.error.EchoErrorSource
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay

@Composable
fun ErrorLogScreen(
    records: List<EchoErrorRecord>,
    onClear: () -> Unit,
    onDelete: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var sourceFilter by rememberSaveable { mutableStateOf(ErrorLogSourceFilterAll) }
    var expandedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var copiedNotice by rememberSaveable { mutableStateOf(false) }
    val dateFormat = remember {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
    }
    val sourceCounts = remember(records) {
        records.groupingBy { it.source }.eachCount()
    }
    val filtered = remember(records, query, sourceFilter) {
        val needle = query.trim()
        records.filter { record ->
            val sourceMatches = sourceFilter == ErrorLogSourceFilterAll ||
                record.source.name == sourceFilter
            sourceMatches && record.matchesQuery(needle)
        }
    }
    LaunchedEffect(copiedNotice) {
        if (!copiedNotice) return@LaunchedEffect
        delay(2_000)
        copiedNotice = false
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = LocalEchoContentMaxWidth.current)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.error_log_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.error_log_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = {
                        shareErrorLog(context, filtered)
                    },
                    enabled = filtered.isNotEmpty(),
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.error_log_share))
                }
                IconButton(
                    onClick = {
                        copyErrorLog(context, filtered)
                        copiedNotice = true
                    },
                    enabled = filtered.isNotEmpty(),
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.error_log_copy_all))
                }
                IconButton(
                    onClick = { confirmClear = true },
                    enabled = records.isNotEmpty(),
                ) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = stringResource(R.string.error_log_clear))
                }
            }
            if (copiedNotice) {
                Text(
                    stringResource(R.string.error_log_copied),
                    color = scheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 12.dp, bottom = 4.dp),
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.error_log_search)) },
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                errorLogSourceFilters().forEach { option ->
                    val count = if (option.id == ErrorLogSourceFilterAll) {
                        records.size
                    } else {
                        sourceCounts[EchoErrorSource.fromId(option.id)] ?: 0
                    }
                    FilterChip(
                        selected = sourceFilter == option.id,
                        onClick = { sourceFilter = option.id },
                        label = {
                            Text(
                                if (count > 0) {
                                    stringResource(R.string.error_log_source_count, stringResource(option.label), count)
                                } else {
                                    stringResource(option.label)
                                },
                            )
                        },
                        shape = RoundedCornerShape(4.dp),
                        border = null,
                    )
                }
            }
        }
        if (filtered.isEmpty()) {
            Text(
                if (records.isEmpty()) {
                    stringResource(R.string.error_log_empty)
                } else {
                    stringResource(R.string.error_log_empty_filtered)
                },
                color = scheme.onSurfaceVariant,
                modifier = Modifier
                    .widthIn(max = LocalEchoContentMaxWidth.current)
                    .fillMaxWidth()
                    .padding(24.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = LocalEchoContentMaxWidth.current)
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filtered, key = { it.id }) { record ->
                    ErrorLogRow(
                        record = record,
                        timestamp = dateFormat.format(Date(record.occurredAtEpochMs)),
                        firstTimestamp = dateFormat.format(Date(record.firstOccurredAtEpochMs)),
                        expanded = expandedId == record.id,
                        onToggle = {
                            expandedId = if (expandedId == record.id) null else record.id
                        },
                        onCopy = {
                            copyErrorLog(context, listOf(record))
                            copiedNotice = true
                        },
                        onShare = { shareErrorLog(context, listOf(record)) },
                        onDelete = {
                            if (expandedId == record.id) expandedId = null
                            onDelete(record.id)
                        },
                    )
                }
                item { Spacer(Modifier.height(156.dp)) }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.error_log_clear_confirm_title)) },
            text = { Text(stringResource(R.string.error_log_clear_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        onClear()
                    },
                ) {
                    Text(stringResource(R.string.error_log_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.error_log_clear_cancel))
                }
            },
        )
    }
}

@Composable
private fun ErrorLogRow(
    record: EchoErrorRecord,
    timestamp: String,
    firstTimestamp: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val crash = record.source == EchoErrorSource.Crash
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (crash) scheme.errorContainer.copy(alpha = 0.72f)
                else scheme.surface.copy(alpha = 0.72f),
            )
            .echoClickable(onClick = onToggle)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(sourceLabelRes(record.source)),
                color = if (crash) scheme.onErrorContainer else scheme.primary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                timestamp,
                color = if (crash) scheme.onErrorContainer.copy(alpha = 0.8f) else scheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Text(
            record.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = if (crash) scheme.onErrorContainer else scheme.onSurface,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
        )
        val meta = buildList {
            if (record.count > 1) add(stringResource(R.string.error_log_count, record.count))
            record.throwableName?.substringAfterLast('.')?.let(::add)
            record.threadName?.let(::add)
            record.appVersion?.let(::add)
        }
        if (meta.isNotEmpty()) {
            Text(
                meta.joinToString(" · "),
                color = if (crash) scheme.onErrorContainer.copy(alpha = 0.8f) else scheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (expanded) {
            if (record.count > 1 && record.firstOccurredAtEpochMs != record.occurredAtEpochMs) {
                Text(
                    stringResource(R.string.error_log_first_seen, firstTimestamp),
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            record.detail?.let { detail ->
                Text(detail, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            record.stackTrace?.let { stack ->
                Text(
                    stack,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = scheme.onSurfaceVariant,
                )
            }
            Row {
                TextButton(onClick = onCopy) { Text(stringResource(R.string.error_log_copy)) }
                TextButton(onClick = onShare) { Text(stringResource(R.string.error_log_share)) }
                TextButton(onClick = onDelete) {
                    Icon(Icons.Rounded.Delete, contentDescription = null)
                    Text(stringResource(R.string.error_log_delete))
                }
            }
        }
    }
}

private fun sourceLabelRes(source: EchoErrorSource): Int =
    when (source) {
        EchoErrorSource.Crash -> R.string.error_log_source_crash
        EchoErrorSource.Playback -> R.string.error_log_source_playback
        EchoErrorSource.Library -> R.string.error_log_source_library
        EchoErrorSource.Connect -> R.string.error_log_source_connect
        EchoErrorSource.Network -> R.string.error_log_source_network
        EchoErrorSource.Usb -> R.string.error_log_source_usb
        EchoErrorSource.Lyrics -> R.string.error_log_source_lyrics
        EchoErrorSource.Other -> R.string.error_log_source_other
    }

private data class ErrorLogSourceFilter(
    val id: String,
    val label: Int,
)

private const val ErrorLogSourceFilterAll = "ALL"

private fun errorLogSourceFilters(): List<ErrorLogSourceFilter> = listOf(
    ErrorLogSourceFilter(ErrorLogSourceFilterAll, R.string.error_log_source_all),
) + EchoErrorSource.entries.map { source ->
    ErrorLogSourceFilter(id = source.name, label = sourceLabelRes(source))
}

private fun copyErrorLog(context: Context, records: List<EchoErrorRecord>) {
    val text = diagnosticDump(records) ?: return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("ECHO errors", text))
}

private fun shareErrorLog(context: Context, records: List<EchoErrorRecord>) {
    val text = diagnosticDump(records) ?: return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.error_log_title))
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.error_log_share)))
    }
}

private fun diagnosticDump(records: List<EchoErrorRecord>): String? {
    if (records.isEmpty()) return null
    return records.joinToString("\n\n") { it.toDiagnosticText() }
}
