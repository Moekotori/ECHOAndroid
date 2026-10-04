package app.echo.android.feature.settings

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.echo.android.model.playback.EchoEqFilterType
import app.echo.android.model.playback.EchoParametricEq
import app.echo.android.model.playback.OpraEqBand

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
    var typeMenu by remember { mutableStateOf(false) }
    var slopeMenu by remember { mutableStateOf(false) }
    val types = listOf(
        EchoEqFilterType.PeakDip to R.string.dsp_peak,
        EchoEqFilterType.LowShelf to R.string.dsp_low_shelf,
        EchoEqFilterType.HighShelf to R.string.dsp_high_shelf,
        EchoEqFilterType.LowPass to R.string.dsp_low_pass,
        EchoEqFilterType.HighPass to R.string.dsp_high_pass,
        EchoEqFilterType.BandStop to R.string.dsp_notch,
        EchoEqFilterType.BandPass to R.string.dsp_band_pass,
    )
    val type = app.echo.android.model.playback.EchoEqFilterType.normalize(band.type)
    val pass = EchoParametricEq.isPass(type)
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
                        DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
                            typeMenu = false
                            val nextPass = EchoParametricEq.isPass(value)
                            val hasGain = value == EchoEqFilterType.PeakDip || value == EchoEqFilterType.LowShelf || value == EchoEqFilterType.HighShelf
                            onCommit(band.copy(type = value, gainDb = if (hasGain) band.gainDb else 0f,
                                q = if (nextPass) null else band.q ?: 0.707f,
                                slope = if (nextPass) EchoParametricEq.snapSlope(band.slope) else null))
                        })
                    }
                }
            }
            IconButton(onClick = onDelete, enabled = enabled && canDelete,
                modifier = Modifier.semantics { contentDescription = removeLabel }) {
                EchoIcon(Icons.Outlined.Delete, null, Modifier.size(18.dp))
            }
        }
        PeqBandControls(band, enabled, onCommit)
        if (pass) {
            Box {
                TextButton(onClick = { slopeMenu = true }, enabled = enabled, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.dsp_slope_oct, EchoParametricEq.snapSlope(band.slope).toInt().toString()))
                    Spacer(Modifier.width(6.dp))
                    EchoIcon(Icons.Default.KeyboardArrowDown, null, Modifier.size(18.dp))
                }
                DropdownMenu(slopeMenu, { slopeMenu = false }) {
                    EchoParametricEq.PassSlopesDb.forEach { option ->
                        DropdownMenuItem(text = { Text(stringResource(R.string.dsp_slope_oct, option.toInt().toString())) },
                            onClick = { slopeMenu = false; onCommit(band.copy(slope = option)) })
                    }
                }
            }
        }
    }
}
