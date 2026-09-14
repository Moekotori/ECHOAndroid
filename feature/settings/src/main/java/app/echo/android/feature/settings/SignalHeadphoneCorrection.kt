package app.echo.android.feature.settings

import app.echo.android.feature.settings.R as L10nR

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoExpand
import app.echo.android.design.echoClickable
import app.echo.android.model.playback.EchoEqualizerState
import app.echo.android.model.playback.EchoEqualizerUserPresets
import app.echo.android.model.playback.OpraHeadphoneCorrectionState
import kotlin.math.abs

@Composable
internal fun SignalHeadphoneCorrection(
    state: OpraHeadphoneCorrectionState,
    equalizer: EchoEqualizerState,
    bypassed: Boolean,
    userPresets: List<app.echo.android.model.playback.EchoEqualizerUserPreset>,
    lastQuery: String,
    onBrandSelected: (String?) -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    onPresetSelected: (String) -> Unit,
    onApplySelected: () -> Unit,
    onToggleFavorite: () -> Unit,
    onApplyUserPreset: (String) -> Unit,
) {
    LaunchedEffect(Unit) {
        if (state.status.source == "empty" && !state.loading) onBrandSelected(null)
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val uriHandler = LocalUriHandler.current
    val scheme = MaterialTheme.colorScheme
    val search = { if (!state.loading && state.query.isNotBlank()) { keyboard?.hide(); onSearch() } }
    val favorites = remember(userPresets) { EchoEqualizerUserPresets.opraFavorites(userPresets) }
    val starredEqId = state.selectedEqId?.let { eqId -> favorites.firstOrNull { it.opraEqId == eqId }?.opraEqId }
    var expandedProduct by remember(state.results) { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SignalLiveDot(active = equalizer.parametric && equalizer.enabled && !bypassed)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    if (equalizer.parametric) {
                        equalizer.sourceLabel ?: stringResource(L10nR.string.opra_current)
                    } else {
                        stringResource(L10nR.string.feature_settings_headphone_correction_491ce5)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                SignalNote(stringResource(L10nR.string.opra_browse_workflow))
                if (bypassed) SignalNote(stringResource(L10nR.string.eq_bypassed), error = true)
            }
        }
        OpraBrandBrowser(state, onBrandSelected)
        SignalEqWell(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = { onQueryChange(it); if (it.isBlank()) onSearch() },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !state.loading,
                    label = { Text(stringResource(L10nR.string.diag_headphone_model)) },
                    placeholder = { Text("HD 650 / IER-M9 / AirPods Max") },
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                    trailingIcon = {
                        IconButton(onClick = search, enabled = !state.loading && state.query.isNotBlank()) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(L10nR.string.diag_search))
                        }
                    },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).padding(end = 8.dp)) {
                        if (state.status.eqCount > 0) {
                            val source = stringResource(if (state.status.source == "network") L10nR.string.opra_online else L10nR.string.opra_cached)
                            SignalNote(stringResource(L10nR.string.diag_opra_stats, state.status.vendorCount, state.status.productCount, state.status.eqCount, source))
                        }
                    }
                    TextButton(onClick = onRefresh, enabled = !state.loading) { Text(stringResource(L10nR.string.diag_refresh_library)) }
                }
                if (lastQuery.isNotBlank()) {
                    TextButton(
                        onClick = {
                            if (state.query != lastQuery) onQueryChange(lastQuery)
                            onSearch()
                        },
                        enabled = !state.loading,
                        contentPadding = PaddingValues(horizontal = 0.dp),
                    ) { Text(stringResource(L10nR.string.opra_last_used, lastQuery)) }
                }
                if (state.loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = scheme.primary, trackColor = scheme.outlineVariant)
                    SignalNote(stringResource(L10nR.string.opra_first_download))
                }
                state.message?.let { SignalNote(it) }
            }
        }
        if (favorites.isNotEmpty()) {
            SignalEqWell(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(L10nR.string.opra_favorites), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    favorites.forEach { preset ->
                        SignalEqUserPresetRow(
                            preset = preset,
                            active = equalizer.sourceLabel == preset.sourceLabel && equalizer.parametric,
                            enabled = !state.loading,
                            onApply = { onApplyUserPreset(preset.id) },
                        )
                    }
                }
            }
        }
        // Keep the selected curve and apply action above the potentially long result list.
        state.selectedPreset?.let { preset ->
            val current = equalizer.parametric && equalizer.enabled && equalizer.sourceLabel == preset.displayName &&
                equalizer.filters == preset.bands && abs(equalizer.preampDb - preset.preampDb) < 0.05f
            SignalEqWell(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(preset.productName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(preset.author, style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant)
                        preset.details?.let { SignalNote(it) }
                        SignalNote(stringResource(L10nR.string.opra_filter_summary, preset.bands.size, formatEqGain(preset.preampDb)))
                    }
                    SignalEqCurve(state.previewCurve, live = !bypassed, showFrequencyLabels = true)
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onApplySelected,
                            enabled = !state.loading && !current,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Text(stringResource(if (current) L10nR.string.opra_applied else L10nR.string.diag_apply_approx))
                        }
                        val starred = starredEqId != null
                        val canStar = starred || userPresets.size < EchoEqualizerUserPresets.MaxCount
                        val starLabel = stringResource(if (starred) L10nR.string.opra_unfavorite else L10nR.string.opra_favorite)
                        IconButton(
                            onClick = onToggleFavorite,
                            enabled = !state.loading && canStar,
                            modifier = Modifier.semantics { contentDescription = starLabel },
                        ) {
                            Icon(
                                if (starred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = null,
                                tint = if (starred) scheme.primary else scheme.onSurfaceVariant,
                            )
                        }
                    }
                    preset.sourceUrl?.takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let { link ->
                        TextButton(
                            onClick = { runCatching { uriHandler.openUri(link) } },
                            modifier = Modifier.padding(start = 4.dp, end = 16.dp, bottom = 8.dp),
                        ) { Text(stringResource(L10nR.string.opra_source)) }
                    }
                }
            }
        }
        if (state.results.isNotEmpty()) {
            SignalEqWell(Modifier.fillMaxWidth()) {
                LazyColumn(Modifier.height(360.dp).padding(8.dp).selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(state.results, key = { it.productId }) { product ->
                        val expanded = expandedProduct == product.productId
                        Column {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .echoClickable { expandedProduct = if (expanded) null else product.productId }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(product.productName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    SignalNote("${product.vendorName} · ${product.presets.size}")
                                }
                                Icon(
                                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = scheme.onSurfaceVariant,
                                )
                            }
                            EchoExpand(expanded) {
                                Column {
                                    product.presets.forEach { preset ->
                                        val selected = preset.eqId == state.selectedEqId
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .defaultMinSize(minHeight = 52.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (selected) scheme.primary.copy(alpha = 0.14f) else Color.Transparent)
                                                .selectable(
                                                    selected = selected,
                                                    enabled = !state.loading,
                                                    role = Role.RadioButton,
                                                    onClick = { onPresetSelected(preset.eqId) },
                                                )
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            Box(
                                                Modifier
                                                    .width(2.dp)
                                                    .height(28.dp)
                                                    .clip(RoundedCornerShape(1.dp))
                                                    .background(if (selected) scheme.primary else Color.Transparent),
                                            )
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    preset.author,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium,
                                                    color = if (selected) scheme.primary else scheme.onSurface,
                                                )
                                                preset.details?.let { SignalNote(it) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = scheme.outlineVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(
                painterResource(L10nR.drawable.opra_logo),
                contentDescription = "OPRA",
                modifier = Modifier
                    .width(88.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White)
                    .padding(4.dp),
            )
            SignalNote(stringResource(L10nR.string.opra_attribution))
        }
        TextButton(onClick = { uriHandler.openUri("https://github.com/opra-project/OPRA") }) {
            Text(stringResource(L10nR.string.opra_project))
        }
    }
}
