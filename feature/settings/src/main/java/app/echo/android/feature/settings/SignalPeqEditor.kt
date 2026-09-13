package app.echo.android.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import app.echo.android.design.EchoExpand
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
    var openBand by remember { mutableIntStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.dsp_peq_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        filters.forEachIndexed { index, band ->
            key(index, band) {
                PeqBandEditor(index, band, enabled, filters.size > 1, expandedRow = openBand == index,
                    onToggle = { openBand = if (openBand == index) -1 else index },
                    onSave = { updated -> onChange(filters.toMutableList().also { it[index] = updated }) },
                    onDelete = { onChange(filters.filterIndexed { i, _ -> i != index }) })
            }
        }
        OutlinedButton(onClick = { openBand = filters.size; onChange(filters + OpraEqBand("peak_dip", 1000f, 0f, 1f, null)) }, enabled = enabled && filters.size < 12) { Text(stringResource(R.string.dsp_add_band)) }
    }
}

@Composable
private fun PeqBandEditor(index: Int, band: OpraEqBand, enabled: Boolean, canDelete: Boolean, expandedRow: Boolean, onToggle: () -> Unit, onSave: (OpraEqBand) -> Unit, onDelete: () -> Unit) {
    var frequency by remember { mutableStateOf(if (band.frequencyHz % 1f == 0f) band.frequencyHz.toInt().toString() else band.frequencyHz.toString()) }
    var gain by remember { mutableStateOf(band.gainDb.toString()) }
    var q by remember { mutableStateOf((band.q ?: 0.707f).toString()) }
    var type by remember { mutableStateOf(band.type) }
    var expanded by remember { mutableStateOf(false) }
    val types = listOf("peak_dip" to R.string.dsp_peak, "low_shelf" to R.string.dsp_low_shelf, "high_shelf" to R.string.dsp_high_shelf, "low_pass" to R.string.dsp_low_pass, "high_pass" to R.string.dsp_high_pass, "band_stop" to R.string.dsp_notch, "band_pass" to R.string.dsp_band_pass)
    val f = frequency.toFloatOrNull()
    val g = gain.toFloatOrNull()
    val quality = q.toFloatOrNull()
    val valid = f != null && f in 20f..20000f && g != null && g in -12f..12f && quality != null && quality in 0.1f..10f
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column {
            Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text((index + 1).toString().padStart(2, '0'), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(types.firstOrNull { it.first == band.type }?.second ?: R.string.dsp_peak), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${formatEqFrequency(band.frequencyHz.toInt())} · Q ${band.q ?: 0.707f}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(formatEqGain(band.gainDb), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                Icon(if (expandedRow) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            EchoExpand(expandedRow) {
                Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    Box {
                        TextButton(onClick = { expanded = true }, enabled = enabled, contentPadding = PaddingValues(0.dp)) {
                            Text(stringResource(types.firstOrNull { it.first == type }?.second ?: R.string.dsp_peak))
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(expanded, { expanded = false }) {
                            types.forEach { (value, label) -> DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { type = value; expanded = false }) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(frequency, { frequency = it }, label = { Text("Hz") }, modifier = Modifier.weight(1.2f), textStyle = MaterialTheme.typography.bodyMedium, singleLine = true, enabled = enabled, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        OutlinedTextField(gain, { gain = it }, label = { Text("dB") }, modifier = Modifier.weight(1f), textStyle = MaterialTheme.typography.bodyMedium, singleLine = true, enabled = enabled, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        OutlinedTextField(q, { q = it }, label = { Text("Q") }, modifier = Modifier.weight(1f), textStyle = MaterialTheme.typography.bodyMedium, singleLine = true, enabled = enabled, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    }
                    if (!valid) Text(stringResource(R.string.dsp_peq_range), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onDelete, enabled = enabled && canDelete) { Text(stringResource(R.string.dsp_remove), color = if (enabled && canDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
                        FilledTonalButton(onClick = { onSave(band.copy(type = type, frequencyHz = f!!, gainDb = g!!, q = quality!!, slope = null)) }, enabled = enabled && valid, shape = RoundedCornerShape(10.dp)) { Text(stringResource(R.string.dsp_apply)) }
                    }
                }
            }
        }
    }
}
