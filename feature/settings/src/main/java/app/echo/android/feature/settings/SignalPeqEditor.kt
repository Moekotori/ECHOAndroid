package app.echo.android.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.echo.android.model.playback.OpraEqBand

@Composable
internal fun SignalPeqEditor(filters: List<OpraEqBand>, enabled: Boolean, onChange: (List<OpraEqBand>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.dsp_peq_detail), style = MaterialTheme.typography.bodySmall)
        filters.forEachIndexed { index, band ->
            key(index, band) {
                PeqBandEditor(index, band, enabled, filters.size > 1,
                    onSave = { updated -> onChange(filters.toMutableList().also { it[index] = updated }) },
                    onDelete = { onChange(filters.filterIndexed { i, _ -> i != index }) })
            }
        }
        OutlinedButton(onClick = { onChange(filters + OpraEqBand("peak_dip", 1000f, 0f, 1f, null)) }, enabled = enabled && filters.size < 12) { Text(stringResource(R.string.dsp_add_band)) }
    }
}

@Composable
private fun PeqBandEditor(index: Int, band: OpraEqBand, enabled: Boolean, canDelete: Boolean, onSave: (OpraEqBand) -> Unit, onDelete: () -> Unit) {
    var frequency by remember { mutableStateOf(band.frequencyHz.toString()) }
    var gain by remember { mutableStateOf(band.gainDb.toString()) }
    var q by remember { mutableStateOf((band.q ?: 0.707f).toString()) }
    var type by remember { mutableStateOf(band.type) }
    var expanded by remember { mutableStateOf(false) }
    val types = listOf("peak_dip" to R.string.dsp_peak, "low_shelf" to R.string.dsp_low_shelf, "high_shelf" to R.string.dsp_high_shelf, "low_pass" to R.string.dsp_low_pass, "high_pass" to R.string.dsp_high_pass, "band_stop" to R.string.dsp_notch, "band_pass" to R.string.dsp_band_pass)
    val f = frequency.toFloatOrNull()
    val g = gain.toFloatOrNull()
    val quality = q.toFloatOrNull()
    val valid = f != null && f in 20f..20000f && g != null && g in -12f..12f && quality != null && quality in 0.1f..10f
    SignalEqWell(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.dsp_band, index + 1), style = MaterialTheme.typography.titleSmall)
            Box {
                TextButton(onClick = { expanded = true }, enabled = enabled) { Text(stringResource(types.firstOrNull { it.first == type }?.second ?: R.string.dsp_peak)) }
                DropdownMenu(expanded, { expanded = false }) {
                    types.forEach { (value, label) -> DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { type = value; expanded = false }) }
                }
            }
            OutlinedTextField(frequency, { frequency = it }, label = { Text(stringResource(R.string.dsp_frequency)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = enabled, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(gain, { gain = it }, label = { Text(stringResource(R.string.dsp_band_gain)) }, modifier = Modifier.weight(1f), singleLine = true, enabled = enabled, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(q, { q = it }, label = { Text("Q") }, modifier = Modifier.weight(1f), singleLine = true, enabled = enabled, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
            if (!valid) Text(stringResource(R.string.dsp_peq_range), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Row {
                TextButton(onClick = { onSave(band.copy(type = type, frequencyHz = f!!, gainDb = g!!, q = quality!!, slope = null)) }, enabled = enabled && valid) { Text(stringResource(R.string.dsp_apply)) }
                TextButton(onClick = onDelete, enabled = enabled && canDelete) { Text(stringResource(R.string.dsp_remove)) }
            }
        }
    }
}
