package app.echo.android.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoExpand
import app.echo.android.model.playback.EchoEqFilterType
import app.echo.android.model.playback.EchoParametricEq
import app.echo.android.model.playback.OpraEqBand
import kotlinx.coroutines.delay

@Composable
internal fun SignalPeqEditor(filters: List<OpraEqBand>, enabled: Boolean, onChange: (List<OpraEqBand>) -> Unit) {
    var openBand by remember { mutableIntStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.dsp_peq_detail),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        filters.forEachIndexed { index, band ->
            key(index) {
                PeqBandEditor(
                    index = index,
                    band = band,
                    enabled = enabled,
                    canDelete = filters.size > 1,
                    expandedRow = openBand == index,
                    onToggle = { openBand = if (openBand == index) -1 else index },
                    onCommit = { updated -> onChange(filters.toMutableList().also { it[index] = updated }) },
                    onDelete = { onChange(filters.filterIndexed { i, _ -> i != index }) },
                )
            }
        }
        TextButton(
            onClick = {
                openBand = filters.size
                onChange(filters + OpraEqBand(EchoEqFilterType.PeakDip, 1000f, 0f, 1f, null))
            },
            enabled = enabled && filters.size < EchoParametricEq.MaxBands,
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) { Text(stringResource(R.string.dsp_add_band)) }
    }
}

@Composable
private fun PeqBandEditor(
    index: Int,
    band: OpraEqBand,
    enabled: Boolean,
    canDelete: Boolean,
    expandedRow: Boolean,
    onToggle: () -> Unit,
    onCommit: (OpraEqBand) -> Unit,
    onDelete: () -> Unit,
) {
    var frequency by remember { mutableStateOf(formatPeqField(band.frequencyHz)) }
    var gain by remember { mutableStateOf(formatPeqField(band.gainDb)) }
    var q by remember { mutableStateOf(formatPeqField(band.q ?: 0.707f)) }
    var type by remember { mutableStateOf(band.type) }
    var slope by remember { mutableFloatStateOf(EchoParametricEq.snapSlope(band.slope)) }
    var typeMenu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    LaunchedEffect(band) {
        if (editing) return@LaunchedEffect
        frequency = formatPeqField(band.frequencyHz)
        gain = formatPeqField(band.gainDb)
        q = formatPeqField(band.q ?: 0.707f)
        type = band.type
        slope = EchoParametricEq.snapSlope(band.slope)
    }
    val types = listOf(
        EchoEqFilterType.PeakDip to R.string.dsp_peak,
        EchoEqFilterType.LowShelf to R.string.dsp_low_shelf,
        EchoEqFilterType.HighShelf to R.string.dsp_high_shelf,
        EchoEqFilterType.LowPass to R.string.dsp_low_pass,
        EchoEqFilterType.HighPass to R.string.dsp_high_pass,
        EchoEqFilterType.BandStop to R.string.dsp_notch,
        EchoEqFilterType.BandPass to R.string.dsp_band_pass,
    )
    val pass = EchoParametricEq.isPass(type)
    val f = frequency.toFloatOrNull()
    val g = gain.toFloatOrNull()
    val quality = q.toFloatOrNull()
    val valid = f != null && f in EchoParametricEq.MinFrequencyHz..EchoParametricEq.MaxFrequencyHz &&
        g != null && g in EchoParametricEq.MinGainDb..EchoParametricEq.MaxGainDb &&
        (pass || (quality != null && quality in EchoParametricEq.MinQ..EchoParametricEq.MaxQ))
    val draft = if (!valid) {
        null
    } else {
        band.copy(
            type = type,
            frequencyHz = f!!,
            gainDb = g!!,
            q = if (pass) null else quality,
            slope = if (pass) slope else null,
        )
    }
    LaunchedEffect(draft, enabled) {
        if (!enabled || draft == null || draft == band) return@LaunchedEffect
        delay(220)
        onCommit(draft)
        editing = false
    }
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                (index + 1).toString().padStart(2, '0'),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(types.firstOrNull { it.first == band.type }?.second ?: R.string.dsp_peak),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (EchoParametricEq.isPass(band.type)) {
                        "${formatEqFrequency(band.frequencyHz.toInt())} · ${stringResource(R.string.dsp_slope_oct, EchoParametricEq.snapSlope(band.slope).toInt().toString())}"
                    } else {
                        "${formatEqFrequency(band.frequencyHz.toInt())} · Q ${band.q ?: 0.707f}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                formatEqGain(band.gainDb),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
            Icon(
                if (expandedRow) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        EchoExpand(expandedRow) {
            Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box {
                    TextButton(onClick = { typeMenu = true }, enabled = enabled, contentPadding = PaddingValues(0.dp)) {
                        Text(stringResource(types.firstOrNull { it.first == type }?.second ?: R.string.dsp_peak))
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(typeMenu, { typeMenu = false }) {
                        types.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(stringResource(label)) },
                                onClick = {
                                    type = value
                                    typeMenu = false
                                },
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        frequency,
                        {
                            editing = true
                            frequency = it
                        },
                        label = { Text("Hz") },
                        modifier = Modifier.weight(1.2f),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        singleLine = true,
                        enabled = enabled,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    OutlinedTextField(
                        gain,
                        {
                            editing = true
                            gain = it
                        },
                        label = { Text("dB") },
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        singleLine = true,
                        enabled = enabled,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    if (!pass) {
                        OutlinedTextField(
                            q,
                            {
                                editing = true
                                q = it
                            },
                            label = { Text("Q") },
                            modifier = Modifier.weight(1f),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            singleLine = true,
                            enabled = enabled,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                }
                if (pass) {
                    var slopeMenu by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { slopeMenu = true }, enabled = enabled, contentPadding = PaddingValues(0.dp)) {
                            Text(stringResource(R.string.dsp_slope_oct, slope.toInt().toString()))
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(slopeMenu, { slopeMenu = false }) {
                            EchoParametricEq.PassSlopesDb.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.dsp_slope_oct, option.toInt().toString())) },
                                    onClick = {
                                        slope = option
                                        slopeMenu = false
                                    },
                                )
                            }
                        }
                    }
                }
                if (!valid) {
                    Text(
                        stringResource(R.string.dsp_peq_range),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(
                    onClick = onDelete,
                    enabled = enabled && canDelete,
                    contentPadding = PaddingValues(horizontal = 0.dp),
                ) {
                    Text(
                        stringResource(R.string.dsp_remove),
                        color = if (enabled && canDelete) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

private fun formatPeqField(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else value.toString()
