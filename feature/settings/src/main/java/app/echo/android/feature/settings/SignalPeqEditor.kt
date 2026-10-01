package app.echo.android.feature.settings

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import app.echo.android.design.EchoIcon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.model.playback.EchoEqFilterType
import app.echo.android.model.playback.EchoParametricEq
import app.echo.android.model.playback.OpraEqBand
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
internal fun SignalPeqEditor(
    filters: List<OpraEqBand>,
    enabled: Boolean,
    onChange: (List<OpraEqBand>) -> Unit,
    selectedBand: Int,
    onSelectBand: (Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val stripState = rememberLazyListState()
    LaunchedEffect(selectedBand, filters.size) {
        if (filters.isNotEmpty()) stripState.scrollToItem(selectedBand.coerceIn(filters.indices))
    }
    val addLabel = stringResource(R.string.dsp_add_band)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LazyRow(modifier = Modifier.weight(1f), state = stripState, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(filters, key = { index, _ -> index }) { index, band ->
                    val selected = selectedBand == index
                    Column(
                        Modifier.width(76.dp)
                            .background(if (selected) scheme.primary.copy(alpha = 0.08f) else scheme.surfaceContainerLow, RoundedCornerShape(6.dp))
                            .selectable(selected = selected) { onSelectBand(index) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text((index + 1).toString().padStart(2, '0'), style = MaterialTheme.typography.labelSmall, color = if (selected) scheme.primary else scheme.onSurfaceVariant)
                            Box(Modifier.size(4.dp).background(if (selected) scheme.primary else scheme.outlineVariant, RoundedCornerShape(2.dp)))
                        }
                        Text(formatEqFrequency(band.frequencyHz.toInt()), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(formatEqGain(band.gainDb), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
            IconButton(
                onClick = {
                    onSelectBand(filters.size)
                    onChange(filters + OpraEqBand(EchoEqFilterType.PeakDip, 1000f, 0f, 1f, null))
                },
                enabled = enabled && filters.size < EchoParametricEq.MaxBands,
                modifier = Modifier.height(68.dp).semantics { contentDescription = addLabel },
            ) { EchoIcon(Icons.Outlined.Add, null) }
        }
        filters.getOrNull(selectedBand)?.let { band ->
            key(selectedBand) {
                PeqBandEditor(
                    band = band,
                    enabled = enabled,
                    canDelete = filters.size > 1,
                    onCommit = { updated -> onChange(filters.toMutableList().also { it[selectedBand] = updated }) },
                    onDelete = {
                        onSelectBand((selectedBand - 1).coerceAtLeast(0))
                        onChange(filters.filterIndexed { i, _ -> i != selectedBand })
                    },
                )
            }
        }
    }
}

@Composable
private fun PeqBandEditor(
    band: OpraEqBand,
    enabled: Boolean,
    canDelete: Boolean,
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
    val commit = rememberUpdatedState(onCommit)
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
            frequencyHz = if (frequency == formatPeqField(band.frequencyHz)) band.frequencyHz else f!!,
            gainDb = if (gain == formatPeqField(band.gainDb)) band.gainDb else g!!,
            q = if (pass) null else if (q == formatPeqField(band.q ?: 0.707f)) band.q ?: 0.707f else quality,
            slope = if (pass) slope else null,
        )
    }
    LaunchedEffect(draft, enabled, band, editing) {
        if (!editing || !enabled || draft == null || draft == band) return@LaunchedEffect
        delay(220)
        commit.value(draft)
        editing = false
    }
    val removeLabel = stringResource(R.string.dsp_remove)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                TextButton(onClick = { typeMenu = true }, enabled = enabled, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(types.firstOrNull { it.first == type }?.second ?: R.string.dsp_peak), color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(6.dp))
                    EchoIcon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(typeMenu, { typeMenu = false }) {
                    types.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(stringResource(label)) },
                            onClick = { editing = true; type = value; typeMenu = false },
                        )
                    }
                }
            }
            IconButton(onClick = onDelete, enabled = enabled && canDelete, modifier = Modifier.semantics { contentDescription = removeLabel }) {
                EchoIcon(Icons.Outlined.Delete, null, Modifier.size(18.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PeqValueField(frequency, { editing = true; frequency = it }, "Hz", enabled,
                f == null || f !in EchoParametricEq.MinFrequencyHz..EchoParametricEq.MaxFrequencyHz, Modifier.weight(1.2f))
            PeqValueField(gain, { editing = true; gain = it }, "dB", enabled,
                g == null || g !in EchoParametricEq.MinGainDb..EchoParametricEq.MaxGainDb, Modifier.weight(1f))
            if (!pass) {
                PeqValueField(q, { editing = true; q = it }, "Q", enabled,
                    quality == null || quality !in EchoParametricEq.MinQ..EchoParametricEq.MaxQ, Modifier.weight(1f))
            }
        }
        if (pass) {
            var slopeMenu by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { slopeMenu = true }, enabled = enabled, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.dsp_slope_oct, slope.toInt().toString()))
                    Spacer(Modifier.width(6.dp))
                    EchoIcon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(slopeMenu, { slopeMenu = false }) {
                    EchoParametricEq.PassSlopesDb.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.dsp_slope_oct, option.toInt().toString())) },
                            onClick = {
                                editing = true
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
    }
}

@Composable
private fun PeqValueField(
    value: String,
    onChange: (String) -> Unit,
    unit: String,
    enabled: Boolean,
    error: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    Column(
        modifier.background(scheme.surfaceContainerLow, RoundedCornerShape(6.dp))
            .clickable(enabled = enabled) { focus.requestFocus(); keyboard?.show() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(unit, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge.copy(
                fontFamily = FontFamily.Monospace,
                color = when { error -> scheme.error; enabled -> scheme.onSurface; else -> scheme.onSurfaceVariant },
            ),
            cursorBrush = SolidColor(scheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = unit },
        )
    }
}

private fun formatPeqField(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString()
    else String.format(Locale.ROOT, "%.2f", value).trimEnd('0').trimEnd('.')
