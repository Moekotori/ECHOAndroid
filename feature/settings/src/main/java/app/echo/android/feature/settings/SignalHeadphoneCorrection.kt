package app.echo.android.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import app.echo.android.feature.settings.R as L10nR

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import app.echo.android.design.EchoIcon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.echo.android.model.playback.EchoEqualizerState
import app.echo.android.model.playback.EchoEqualizerUserPresets
import app.echo.android.model.playback.OpraHeadphoneCorrectionState
import app.echo.android.model.playback.HeadphoneCorrectionCatalog
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.automirrored.rounded.OpenInNew

import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

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
    val keyboard = LocalSoftwareKeyboardController.current
    val uriHandler = LocalUriHandler.current
    val scheme = MaterialTheme.colorScheme
    val currentSearch by rememberUpdatedState(onSearch)
    var submittedQuery by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (state.status.source == "empty" && !state.loading && state.query.isBlank()) onBrandSelected(null)
    }
    // The existing controller cancels replaced requests. Brand browsing filters its loaded models locally.
    LaunchedEffect(state.query, state.selectedBrandId) {
        if (state.selectedBrandId == null && state.query.isNotBlank()) {
            delay(350)
            if (submittedQuery != state.query) {
                submittedQuery = state.query
                currentSearch()
            }
        }
    }
    val search = {
        if (!state.loading && state.query.isNotBlank()) {
            submittedQuery = state.query
            keyboard?.hide()
            onSearch()
        }
    }
    val favorites = remember(userPresets) { EchoEqualizerUserPresets.opraFavorites(userPresets) }
    var shareFavorite by remember { mutableStateOf<app.echo.android.model.playback.EchoEqualizerUserPreset?>(null) }
    val selectedBrandName = state.brands.firstOrNull { it.id == state.selectedBrandId }?.name
    val visibleResults by produceState(state.results, state.results, state.query, state.selectedBrandId) {
        value = withContext(Dispatchers.Default) {
            val needle = state.query.trim()
            if (state.selectedBrandId == null || needle.isEmpty()) state.results
            else {
                val compactNeedle = needle.filter(Char::isLetterOrDigit).lowercase(java.util.Locale.ROOT)
                state.results.filter { product ->
                    product.productName.filter(Char::isLetterOrDigit).lowercase(java.util.Locale.ROOT).contains(compactNeedle) ||
                        product.productId.contains(needle, ignoreCase = true)
                }
            }
        }
    }
    val autoEq = state.catalog == HeadphoneCorrectionCatalog.AutoEq
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = autoEq, onClick = { if (!autoEq) onBrandSelected("catalog:autoeq") },
                label = { Text(stringResource(L10nR.string.autoeq_offline_source)) })
            FilterChip(selected = !autoEq, onClick = { if (autoEq) onBrandSelected("catalog:opra") },
                label = { Text("OPRA") })
        }
        if (autoEq) {
            SignalNote(stringResource(L10nR.string.autoeq_catalog_note))
            state.status.revision?.let { SignalNote(stringResource(L10nR.string.autoeq_revision, it.take(12))) }
        }
        TextField(
            value = state.query,
            onValueChange = {
                submittedQuery = null
                onQueryChange(it)
                if (it.isBlank() && state.selectedBrandId == null) onSearch()
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(
                if (selectedBrandName != null) stringResource(L10nR.string.opra_search_in_brand, selectedBrandName)
                else stringResource(L10nR.string.opra_search_hint),
                style = MaterialTheme.typography.bodyMedium,
            ) },
            shape = RoundedCornerShape(14.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = scheme.surfaceContainer,
                unfocusedContainerColor = scheme.surfaceContainer,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { search() }),
            leadingIcon = {
                IconButton(onClick = search, enabled = !state.loading && state.query.isNotBlank()) {
                    EchoIcon(Icons.Default.Search, contentDescription = stringResource(L10nR.string.diag_search))
                }
            },
            trailingIcon = if (state.query.isNotEmpty()) ({
                IconButton(onClick = {
                    submittedQuery = null
                    onQueryChange("")
                    if (state.selectedBrandId == null) onSearch()
                }) { EchoIcon(Icons.Default.Close, contentDescription = stringResource(L10nR.string.opra_clear_search)) }
            }) else null,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (state.status.eqCount > 0) stringResource(L10nR.string.opra_catalog_summary, state.status.productCount, state.status.eqCount)
                else stringResource(L10nR.string.opra_browse_workflow),
                modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            IconButton(onClick = {
                if (autoEq) uriHandler.openUri("https://github.com/jaakkopasanen/AutoEq") else onRefresh()
            }, enabled = !state.loading) {
                EchoIcon(if (autoEq) Icons.AutoMirrored.Rounded.OpenInNew else Icons.Default.Refresh, contentDescription = stringResource(
                    if (autoEq) L10nR.string.autoeq_check_updates else L10nR.string.diag_refresh_library))
            }
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.message?.let { SignalNote(it) }
        if (bypassed) SignalNote(stringResource(L10nR.string.eq_bypassed), error = true)
        if (equalizer.parametric && equalizer.sourceLabel != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SignalLiveDot(active = equalizer.enabled && !bypassed)
                Text(equalizer.sourceLabel.orEmpty(), style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (lastQuery.isNotBlank() && state.query.isBlank() && state.selectedBrandId == null) {
            TextButton(onClick = { onQueryChange(lastQuery) }, contentPadding = PaddingValues(0.dp)) {
                Text(stringResource(L10nR.string.opra_last_used, lastQuery))
            }
        }
        if (favorites.isNotEmpty() && state.query.isBlank() && state.selectedBrandId == null) {
            SignalEqWell(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(L10nR.string.opra_favorites), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    favorites.forEach { preset ->
                        SignalEqUserPresetRow(
                            preset = preset,
                            active = equalizer.sourceLabel == preset.sourceLabel && equalizer.parametric,
                            enabled = !state.loading,
                            onApply = { onApplyUserPreset(preset.id) },
                            onShare = { shareFavorite = preset },
                            showActions = true,
                            showEditDelete = false,
                        )
                    }
                }
            }
        }
        shareFavorite?.let { preset ->
            EqShareCodeDialog(preset = preset, onDismiss = { shareFavorite = null })
        }
        OpraCorrectionPreview(state, equalizer, bypassed, userPresets, onApplySelected, onToggleFavorite)
        OpraBrandBrowser(state, onBrandSelected)
        OpraModelResults(state, visibleResults) {
            keyboard?.hide()
            onPresetSelected(it)
        }
        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
        TextButton(onClick = { uriHandler.openUri(if (autoEq) "https://github.com/jaakkopasanen/AutoEq" else "https://github.com/opra-project/OPRA") },
            contentPadding = PaddingValues(0.dp)) {
            Text(stringResource(if (autoEq) L10nR.string.autoeq_attribution else L10nR.string.opra_attribution), style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant)
        }
    }
}
