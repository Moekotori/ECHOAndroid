package app.echo.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.text.input.ImeAction
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
import androidx.compose.runtime.saveable.rememberSaveable
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
    var editing by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) editing = false }
    val scheme = MaterialTheme.colorScheme
    TextButton(
        onClick = { editing = true }, enabled = enabled,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.textButtonColors(containerColor = scheme.surfaceContainerHigh),
        modifier = modifier.heightIn(min = 44.dp).semantics { contentDescription = label; stateDescription = valueLabel },
    ) {
        Text(valueLabel, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium, color = if (enabled) scheme.onSurface else scheme.onSurfaceVariant)
    }
    if (editing) {
        val initial = remember { signalNumber(value * scale, decimals) }
        var raw by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
        val focus = remember { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current
        LaunchedEffect(Unit) { focus.requestFocus(); keyboard?.show() }
        val parsed = raw.text.replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() }?.div(scale)
        val valid = parsed != null && parsed in valueRange
        val rangeLabel = stringResource(R.string.signal_value_range,
            signalNumber(valueRange.start * scale, decimals), signalNumber(valueRange.endInclusive * scale, decimals), unit)
        val contextSignLabel = stringResource(R.string.signal_value_change_sign)
        val apply = {
            if (valid && enabled) {
                parsed?.let {
                    val factor = 10f.pow(decimals) * scale
                    onValueChange((round(it * factor) / factor).coerceIn(valueRange))
                }
                keyboard?.hide()
                editing = false
            }
        }
        AlertDialog(
            onDismissRequest = { keyboard?.hide(); editing = false }, title = { Text(label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = raw, onValueChange = { raw = it }, singleLine = true,
                        label = { Text(label) }, isError = !valid,
                        suffix = if (unit.isBlank()) null else ({ Text(unit) }),
                        leadingIcon = if (valueRange.start >= 0f) null else ({
                            TextButton(onClick = {
                                val next = if (raw.text.startsWith("-")) raw.text.drop(1) else "-" + raw.text.removePrefix("+")
                                raw = TextFieldValue(next, TextRange(next.length))
                            }, modifier = Modifier.semantics { contentDescription = contextSignLabel }) { Text("±") }
                        }),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { apply() }),
                        textStyle = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
                        shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                    Text(rangeLabel, style = MaterialTheme.typography.bodySmall,
                        color = if (valid) scheme.onSurfaceVariant else scheme.error)
                }
            },
            confirmButton = {
                TextButton(enabled = valid && enabled, onClick = apply) { Text(stringResource(R.string.dsp_apply)) }
            },
            dismissButton = { TextButton(onClick = { keyboard?.hide(); editing = false }) { Text(stringResource(R.string.error_log_clear_cancel)) } },
        )
    }
}

internal fun signalNumber(value: Float, decimals: Int = 1): String =
    String.format(Locale.ROOT, "%.${decimals}f", value)
