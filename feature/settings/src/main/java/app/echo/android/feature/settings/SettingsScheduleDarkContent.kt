package app.echo.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** Switch plus two tappable times; the system-style time picker replaces two 96-step sliders. */
@Composable
internal fun SettingsScheduleDarkContent(
    enabled: Boolean,
    startMinute: Int,
    endMinute: Int,
    onEnabledChange: (Boolean) -> Unit,
    onStartMinuteChange: (Int) -> Unit,
    onEndMinuteChange: (Int) -> Unit,
) {
    // 0 = closed, 1 = editing start, 2 = editing end. Survives rotation while the dialog is open.
    var editing by rememberSaveable { mutableStateOf(0) }
    val startTitle = stringResource(R.string.settings_dark_start)
    val endTitle = stringResource(R.string.settings_dark_end)
    Column(Modifier.fillMaxWidth().settingsSearchAnchor(stringResource(R.string.settings_section_schedule))) {
        SettingsSwitchRow(
            title = stringResource(R.string.settings_scheduled_dark),
            detail = stringResource(
                R.string.settings_scheduled_dark_detail,
                formatMinuteOfDay(startMinute),
                formatMinuteOfDay(endMinute),
            ),
            checked = enabled,
            onCheckedChange = onEnabledChange,
        )
        if (enabled) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ScheduleTimeTile(startTitle, startMinute, Modifier.weight(1f)) { editing = 1 }
                ScheduleTimeTile(endTitle, endMinute, Modifier.weight(1f)) { editing = 2 }
            }
        }
    }
    if (enabled && editing != 0) {
        val start = editing == 1
        ScheduleTimeDialog(
            title = if (start) startTitle else endTitle,
            initialMinute = if (start) startMinute else endMinute,
            onDismiss = { editing = 0 },
            onConfirm = { minute ->
                if (start) onStartMinuteChange(minute) else onEndMinuteChange(minute)
                editing = 0
            },
        )
    }
}

@Composable
private fun ScheduleTimeTile(
    label: String,
    minute: Int,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.settingsSearchAnchor(label),
        shape = RoundedCornerShape(14.dp),
        color = scheme.surfaceContainerHigh,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(label, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            Text(
                formatMinuteOfDay(minute),
                color = scheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleTimeDialog(
    title: String,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val minuteOfDay = initialMinute.coerceIn(0, 23 * 60 + 59)
    val state = rememberTimePickerState(minuteOfDay / 60, minuteOfDay % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}
