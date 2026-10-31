package app.echo.android.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.model.playback.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One bounded lazy viewport; selecting a curve opens a preview without moving the catalog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SignalHeadphoneCorrection(
    state: OpraHeadphoneCorrectionState,
    equalizer: EchoEqualizerState,
    bypassed: Boolean,
    playing: Boolean,
    userPresets: List<EchoEqualizerUserPreset>,
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
    val focus = LocalFocusManager.current
    val uriHandler = LocalUriHandler.current
    val scheme = MaterialTheme.colorScheme
    val autoEq = state.catalog == HeadphoneCorrectionCatalog.AutoEq
    val selectedBrand = remember(state.brands, state.selectedBrandId) {
        state.brands.firstOrNull { it.id == state.selectedBrandId }
    }
    val rootList = rememberSaveable(state.catalog, saver = LazyListState.Saver) { LazyListState() }
    val brandList = rememberSaveable(state.catalog, state.selectedBrandId, saver = LazyListState.Saver) { LazyListState() }
    val listState = if (state.selectedBrandId == null) rootList else brandList
    // The parent query and its list position survive drilling into a brand.
    var previousQuery by remember { mutableStateOf(state.query) }
    var previousBrand by remember { mutableStateOf(state.selectedBrandId) }
    LaunchedEffect(state.query, state.selectedBrandId, state.catalog) {
        if (previousBrand == state.selectedBrandId && previousQuery != state.query) listState.scrollToItem(0)
        previousBrand = state.selectedBrandId
        previousQuery = state.query
    }
    LaunchedEffect(Unit) {
        if (state.status.source == "empty" && !state.loading) onSearch()
    }
    val producedContent by produceState(OpraBrowseContent(), state.brands, state.results, state.query, state.selectedBrandId, state.catalog) {
        val snapshot = state
        value = withContext(Dispatchers.Default) {
            OpraBrowseContent(
                query = snapshot.query, brandId = snapshot.selectedBrandId, catalog = snapshot.catalog,
                inputBrands = snapshot.brands, inputProducts = snapshot.results,
                brands = if (snapshot.selectedBrandId == null) filterOpraBrands(snapshot.brands, snapshot.query) else emptyList(),
                products = filterOpraModels(snapshot.results, snapshot.query, snapshot.selectedBrandId != null),
            )
        }
    }
    val filtering = producedContent.query != state.query || producedContent.brandId != state.selectedBrandId ||
        producedContent.catalog != state.catalog || producedContent.inputBrands != state.brands ||
        producedContent.inputProducts != state.results
    // Keep the parent items mounted while returning/loading so LazyListState is not clamped to an empty list.
    var rootContent by remember(state.catalog) { mutableStateOf(OpraBrowseContent()) }
    LaunchedEffect(producedContent, filtering, state.loading, state.selectedBrandId) {
        if (!filtering && !state.loading && state.selectedBrandId == null) rootContent = producedContent
    }
    val content = when {
        state.selectedBrandId == null && rootContent.query == state.query && (filtering || state.loading) -> rootContent
        !filtering -> producedContent
        else -> OpraBrowseContent()
    }
    val favorites = remember(userPresets) { EchoEqualizerUserPresets.opraFavorites(userPresets) }
    val logos = remember(state.brands) { state.brands.associate { it.name to it.logoUrl } }
    var expandedProduct by remember(state.catalog, state.selectedBrandId, state.query) { mutableStateOf<String?>(null) }
    var previewId by rememberSaveable { mutableStateOf<String?>(null) }
    var shareFavorite by remember { mutableStateOf<EchoEqualizerUserPreset?>(null) }
    val closeKeyboard = { focus.clearFocus(); keyboard?.hide(); Unit }
    val chooseBrand: (String?) -> Unit = { closeKeyboard(); onBrandSelected(it) }
    Column(Modifier.fillMaxSize()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OpraCatalogControls(state, onQueryChange, onSearch = { closeKeyboard(); if (state.selectedBrandId == null) onSearch() },
                onCatalogSelected = chooseBrand, onRefresh = onRefresh)
            selectedBrand?.let { brand ->
                OpraSelectedBrand(brand, enabled = true,
                    backLabel = stringResource(if (autoEq) R.string.autoeq_all_measurements else R.string.opra_all_brands),
                    onBack = { chooseBrand(null) })
            }
            if (state.loading || filtering) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (filtering && content.catalog == null) {
            Spacer(Modifier.fillMaxWidth().weight(1f))
        } else LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(top = 8.dp, bottom = 188.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            state.message?.let { message ->
                item("message") {
                    Column {
                        SignalNote(message)
                        if (!state.loading && state.status.source == "empty") {
                            TextButton(onClick = onSearch) { Text(stringResource(R.string.signal_retry_catalog)) }
                        }
                    }
                }
            }
            if (bypassed) item("bypass") { SignalNote(stringResource(R.string.signal_bypass_detail), error = true) }
            if (equalizer.parametric && equalizer.sourceLabel != null) item("current") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SignalLiveDot(active = playing && equalizer.active && !bypassed && equalizer.processingSampleRateHz != null)
                    Text(equalizer.sourceLabel.orEmpty(), style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (state.query.isBlank() && state.selectedBrandId == null) {
                if (lastQuery.isNotBlank()) item("recent") {
                    TextButton(onClick = { onQueryChange(lastQuery) }, contentPadding = PaddingValues(0.dp)) {
                        Text(stringResource(R.string.opra_last_used, lastQuery))
                    }
                }
                if (favorites.isNotEmpty()) {
                    item("favorites-title") { OpraSectionTitle(stringResource(R.string.opra_favorites)) }
                    items(favorites.size, key = { "favorite:${favorites[it].id}" }, contentType = { "favorite" }) { index ->
                        val preset = favorites[index]
                        SignalEqUserPresetRow(preset = preset,
                            active = equalizer.sourceLabel == preset.sourceLabel && equalizer.parametric,
                            enabled = true, applyEnabled = !bypassed && equalizer.supported,
                            onApply = { onApplyUserPreset(preset.id) }, onShare = { shareFavorite = preset },
                            showActions = true, showEditDelete = false)
                    }
                }
            }
            if (content.brands.isNotEmpty()) {
                item("brands-title") { OpraSectionTitle(stringResource(
                    if (autoEq) R.string.autoeq_choose_measurement else R.string.opra_choose_brand)) }
                opraBrandItems(content.brands) { chooseBrand(it) }
            }
            if (content.products.isNotEmpty()) {
                item("models-title") {
                    OpraSectionTitle(if (state.selectedBrandId == null && content.products.size >= 16)
                        pluralStringResource(R.plurals.opra_top_models, content.products.size, content.products.size)
                    else stringResource(R.string.opra_choose_model))
                }
                opraModelItems(state, content.products, expandedProduct, { expandedProduct = it }, logos) {
                    closeKeyboard()
                    onPresetSelected(it)
                    previewId = it
                }
            } else if (!state.loading && !filtering && state.status.source != "empty" &&
                (state.selectedBrandId != null || (state.query.isNotBlank() && content.brands.isEmpty()))) {
                item("empty") {
                    Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.opra_no_matching_models), style = MaterialTheme.typography.titleSmall)
                        SignalNote(stringResource(R.string.opra_search_recovery))
                        if (state.query.isNotEmpty()) TextButton(onClick = { onQueryChange("") }) {
                            Text(stringResource(R.string.opra_clear_search))
                        }
                    }
                }
            }
            item("attribution") {
                TextButton(onClick = { uriHandler.openUri(if (autoEq) "https://github.com/jaakkopasanen/AutoEq" else "https://github.com/opra-project/OPRA") },
                    contentPadding = PaddingValues(vertical = 8.dp)) {
                    Text(stringResource(if (autoEq) R.string.autoeq_attribution else R.string.opra_attribution),
                        style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                }
            }
        }
    }
    if (previewId != null && previewId == state.selectedEqId && state.selectedPreset != null) {
        ModalBottomSheet(onDismissRequest = { previewId = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            OpraCorrectionPreview(state, equalizer, bypassed, userPresets, onApplySelected, onToggleFavorite,
                onClose = { previewId = null })
        }
    }
    shareFavorite?.let { preset -> EqShareCodeDialog(preset = preset, onDismiss = { shareFavorite = null }) }
}

@Composable
private fun OpraSectionTitle(title: String) {
    Text(title, Modifier.padding(top = 8.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private data class OpraBrowseContent(
    val query: String? = null,
    val brandId: String? = null,
    val catalog: HeadphoneCorrectionCatalog? = null,
    val inputBrands: List<OpraHeadphoneBrand> = emptyList(),
    val inputProducts: List<OpraHeadphoneCorrectionProduct> = emptyList(),
    val brands: List<OpraHeadphoneBrand> = emptyList(),
    val products: List<OpraHeadphoneCorrectionProduct> = emptyList(),
)
