package app.echo.android.feature.settings

import app.echo.android.feature.settings.R as L10nR

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.echo.android.model.playback.OpraHeadphoneCorrectionState

@Composable
internal fun SignalHeadphoneCorrection(
    state: OpraHeadphoneCorrectionState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    onPresetSelected: (String) -> Unit,
    onApplySelected: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val search = { if (!state.loading && state.query.isNotBlank()) { keyboard?.hide(); onSearch() } }
    SignalSection(
        stringResource(L10nR.string.feature_settings_headphone_correction_491ce5),
        stringResource(L10nR.string.feature_settings_find_your_model_choose_a_curve_then_apply_0d8456),
    ) {
        OutlinedTextField(
            value = state.query, onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(L10nR.string.diag_headphone_model)) },
            placeholder = { Text("HD 650 / IER-M9 / AirPods Max") },
            shape = RoundedCornerShape(4.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { search() }),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = search, enabled = !state.loading && state.query.isNotBlank(), shape = RoundedCornerShape(4.dp)) {
                Text(stringResource(if (state.loading) L10nR.string.diag_searching else L10nR.string.diag_search))
            }
            TextButton(onClick = onRefresh, enabled = !state.loading) { Text(stringResource(L10nR.string.diag_refresh_library)) }
        }
        if (state.loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            SignalNote(stringResource(L10nR.string.diag_reading_opra))
        }
        state.message?.let { SignalNote(it) }
        if (state.status.eqCount > 0) {
            SignalNote(stringResource(L10nR.string.diag_opra_stats, state.status.vendorCount, state.status.productCount, state.status.eqCount, state.status.source))
        }
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            state.results.forEach { product ->
                Column {
                    Text(product.productName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    SignalNote(product.vendorName)
                    product.presets.forEach { preset ->
                        Row(
                            Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)
                                .selectable(selected = preset.eqId == state.selectedEqId, enabled = !state.loading, role = Role.RadioButton, onClick = { onPresetSelected(preset.eqId) })
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = preset.eqId == state.selectedEqId, onClick = null, enabled = !state.loading)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(preset.details ?: preset.author, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Normal)
                                if (preset.details != null) SignalNote(preset.author)
                            }
                        }
                    }
                }
            }
        }
        state.selectedPreset?.let { preset ->
            HorizontalDivider()
            Text(preset.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            SignalNote(stringResource(L10nR.string.diag_eq_preamp, formatEqGain(preset.preampDb)))
            Button(onClick = onApplySelected, enabled = !state.loading, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(4.dp)) {
                Text(stringResource(L10nR.string.diag_apply_approx))
            }
        }
    }
}
