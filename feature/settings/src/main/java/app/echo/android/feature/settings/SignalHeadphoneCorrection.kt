package app.echo.android.feature.settings

import app.echo.android.feature.settings.R as L10nR

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.echo.android.model.playback.EchoEqualizerState
import app.echo.android.model.playback.OpraHeadphoneCorrectionState
import kotlin.math.abs

@Composable
internal fun SignalHeadphoneCorrection(
    state: OpraHeadphoneCorrectionState,
    equalizer: EchoEqualizerState,
    bypassed: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    onPresetSelected: (String) -> Unit,
    onApplySelected: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val uriHandler = LocalUriHandler.current
    val search = { if (!state.loading && state.query.isNotBlank()) { keyboard?.hide(); onSearch() } }
    var expandedProduct by remember(state.results) { mutableStateOf(state.results.firstOrNull()?.productId) }
    SignalSection(stringResource(L10nR.string.feature_settings_headphone_correction_491ce5), stringResource(L10nR.string.opra_workflow)) {
        if (equalizer.parametric) {
            Text(stringResource(L10nR.string.opra_current), style = MaterialTheme.typography.labelLarge)
            Text(equalizer.sourceLabel.orEmpty(), style = MaterialTheme.typography.bodyMedium)
        }
        if (bypassed) SignalNote(stringResource(L10nR.string.eq_bypassed))
        OutlinedTextField(value = state.query, onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(L10nR.string.diag_headphone_model)) },
            placeholder = { Text("HD 650 / IER-M9 / AirPods Max") }, shape = RoundedCornerShape(4.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { search() }))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = search, enabled = !state.loading && state.query.isNotBlank(), shape = RoundedCornerShape(4.dp)) {
                Text(stringResource(if (state.loading) L10nR.string.diag_searching else L10nR.string.diag_search))
            }
            TextButton(onClick = onRefresh, enabled = !state.loading) { Text(stringResource(L10nR.string.diag_refresh_library)) }
        }
        if (state.loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            SignalNote(stringResource(L10nR.string.opra_first_download))
        }
        state.message?.let { SignalNote(it) }
        if (state.status.eqCount > 0) {
            val source = stringResource(if (state.status.source == "network") L10nR.string.opra_online else L10nR.string.opra_cached)
            SignalNote(stringResource(L10nR.string.diag_opra_stats, state.status.vendorCount, state.status.productCount, state.status.eqCount, source))
        }
        // Keep the selected curve and apply action above the potentially long result list.
        state.selectedPreset?.let { preset ->
            val current = equalizer.parametric && equalizer.enabled && equalizer.sourceLabel == preset.displayName &&
                equalizer.filters == preset.bands && abs(equalizer.preampDb - preset.preampDb) < 0.05f
            HorizontalDivider()
            Text(preset.productName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(preset.author, style = MaterialTheme.typography.titleSmall)
            preset.details?.let { SignalNote(it) }
            SignalNote(stringResource(L10nR.string.opra_filter_summary, preset.bands.size, formatEqGain(preset.preampDb)))
            SignalEqCurve(state.previewCurve)
            Button(onClick = onApplySelected, enabled = !state.loading && !current,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(4.dp)) {
                Text(stringResource(if (current) L10nR.string.opra_applied else L10nR.string.diag_apply_approx))
            }
            preset.sourceUrl?.takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let { link ->
                TextButton(onClick = { runCatching { uriHandler.openUri(link) } }) { Text(stringResource(L10nR.string.opra_source)) }
            }
        }
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.results.forEach { product ->
                Column {
                    TextButton(onClick = { expandedProduct = if (expandedProduct == product.productId) null else product.productId },
                        modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(product.productName, style = MaterialTheme.typography.titleSmall)
                            Text("${product.vendorName} · ${product.presets.size}", style = MaterialTheme.typography.labelSmall)
                        }
                        Icon(if (expandedProduct == product.productId) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                    }
                    if (expandedProduct == product.productId) product.presets.forEach { preset ->
                        Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)
                            .selectable(selected = preset.eqId == state.selectedEqId, enabled = !state.loading,
                                role = Role.RadioButton, onClick = { onPresetSelected(preset.eqId) }).padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = preset.eqId == state.selectedEqId, onClick = null, enabled = !state.loading)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(preset.author, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                preset.details?.let { SignalNote(it) }
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(L10nR.drawable.opra_logo), contentDescription = "OPRA", modifier = Modifier.width(88.dp).height(32.dp).background(Color.White).padding(4.dp))
            SignalNote(stringResource(L10nR.string.opra_attribution))
        }
        TextButton(onClick = { uriHandler.openUri("https://github.com/opra-project/OPRA") }) {
            Text(stringResource(L10nR.string.opra_project))
        }
    }
}
