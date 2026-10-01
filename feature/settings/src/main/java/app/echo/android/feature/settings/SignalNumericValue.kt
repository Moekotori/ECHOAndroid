package app.echo.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.pow
import kotlin.math.round

/** Numeric entry complements the slider; no work is committed until the user applies it. */
@Composable
internal fun SignalNumericValue(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueLabel: String = formatEqGain(value),
    unit: String = "dB",
    scale: Float = 1f,
    decimals: Int = 1,
) {
    var editing by remember { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) editing = false }
    val scheme = MaterialTheme.colorScheme
    TextButton(
        onClick = { editing = true }, enabled = enabled,
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
        modifier = modifier.semantics { contentDescription = label; stateDescription = valueLabel },
    ) {
        Text(valueLabel, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium, color = if (enabled) scheme.onSurface else scheme.onSurfaceVariant)
    }
    if (editing) {
        val initial = remember { signalNumber(value * scale, decimals) }
        var raw by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
        val focus = remember { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current
        LaunchedEffect(Unit) { focus.requestFocus(); keyboard?.show() }
        val parsed = raw.text.replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() }?.div(scale)
        val valid = parsed != null && parsed in valueRange
        val rangeLabel = stringResource(R.string.signal_value_range,
            signalNumber(valueRange.start * scale, decimals), signalNumber(valueRange.endInclusive * scale, decimals), unit)
        AlertDialog(
            onDismissRequest = { editing = false }, title = { Text(label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = raw, onValueChange = { raw = it }, singleLine = true,
                        label = { Text(unit) }, isError = !valid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
                        shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                    Text(rangeLabel, style = MaterialTheme.typography.bodySmall,
                        color = if (valid) scheme.onSurfaceVariant else scheme.error)
                }
            },
            confirmButton = {
                TextButton(enabled = valid && enabled, onClick = {
                    parsed?.let {
                        val factor = 10f.pow(decimals) * scale
                        onValueChange((round(it * factor) / factor).coerceIn(valueRange))
                    }
                    editing = false
                }) { Text(stringResource(R.string.dsp_apply)) }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text(stringResource(R.string.error_log_clear_cancel)) } },
        )
    }
}

internal fun signalNumber(value: Float, decimals: Int = 1): String =
    String.format(Locale.ROOT, "%.${decimals}f", value)
