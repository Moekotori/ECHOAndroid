package app.echo.android.feature.home

import androidx.compose.foundation.layout.height
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// Material date pickers speak UTC midnight; the history filter speaks local calendar days.
private fun Long.utcMillisToEpochDay(): Long = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
private fun Long.epochDayToUtcMillis(): Long = LocalDate.ofEpochDay(this).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryDayPickerDialog(initialEpochDay: Long?, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialEpochDay?.epochDayToUtcMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onPick(it.utcMillisToEpochDay()) } },
                enabled = state.selectedDateMillis != null,
            ) { Text(stringResource(R.string.playback_history_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.playback_history_cancel)) } },
    ) { DatePicker(state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryRangePickerDialog(
    initialFrom: Long?,
    initialTo: Long?,
    onDismiss: () -> Unit,
    onPick: (from: Long, to: Long) -> Unit,
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialFrom?.epochDayToUtcMillis(),
        initialSelectedEndDateMillis = initialTo?.epochDayToUtcMillis(),
    )
    val from = state.selectedStartDateMillis
    val to = state.selectedEndDateMillis
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                // A single tapped day is a valid one-day range.
                onClick = { if (from != null) onPick(from.utcMillisToEpochDay(), (to ?: from).utcMillisToEpochDay()) },
                enabled = from != null,
            ) { Text(stringResource(R.string.playback_history_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.playback_history_cancel)) } },
    ) { DateRangePicker(state, Modifier.height(500.dp)) }
}
