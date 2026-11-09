package app.echo.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.design.EchoModalSheet
import app.echo.android.design.EchoSheetHeader
import app.echo.android.design.rememberEchoWindowLayout
import app.echo.android.model.playback.EchoEqualizerPresetDefinition
import app.echo.android.model.playback.EchoEqualizerPresets
import app.echo.android.model.playback.EchoEqualizerUserPreset

private val SearchWhitespace = Regex("\\s+")

@Composable
internal fun SignalEqPresetPicker(
    currentLabel: String,
    selectedId: String,
    userPresets: List<EchoEqualizerUserPreset>,
    activeUserId: String?,
    enabled: Boolean,
    eqEnabled: Boolean,
    onEnableEq: () -> Unit,
    onSelect: (String) -> Unit,
    onApplyUserPreset: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val title = stringResource(R.string.eq_choose_preset)
    TextButton(
        onClick = { expanded = true },
        enabled = enabled,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(currentLabel, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        EchoIcon(Icons.Rounded.KeyboardArrowDown, contentDescription = null)
    }
    if (expanded) {
        EqPresetSheet(
            title = title,
            currentLabel = currentLabel,
            selectedId = selectedId,
            userPresets = userPresets,
            activeUserId = activeUserId,
            enabled = enabled,
            eqEnabled = eqEnabled,
            onEnableEq = onEnableEq,
            onSelect = onSelect,
            onApplyUserPreset = { expanded = false; onApplyUserPreset(it) },
            onDismiss = { expanded = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EqPresetSheet(
    title: String,
    currentLabel: String,
    selectedId: String,
    userPresets: List<EchoEqualizerUserPreset>,
    activeUserId: String?,
    enabled: Boolean,
    eqEnabled: Boolean,
    onEnableEq: () -> Unit,
    onSelect: (String) -> Unit,
    onApplyUserPreset: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val mainEqPresets = remember(userPresets) { userPresets.filterNot { it.fromOpra } }
    val activeSavedId = activeUserId?.takeIf { id -> mainEqPresets.any { it.id == id } }
    var group by rememberSaveable { mutableStateOf(initialEqPresetGroup(activeSavedId, selectedId)) }
    val search = query.trim()
    val terms = remember(search) { search.split(SearchWhitespace).filter(String::isNotEmpty) }
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    val titles = remember(resources, configuration) { EqPresetTitleRes.mapValues { resources.getString(it.value) } }
    val details = remember(resources, configuration) { EqPresetDetailRes.mapValues { resources.getString(it.value) } }
    val builtIn = remember(terms, group, titles, details) {
        if (terms.isNotEmpty()) {
            EchoEqualizerPresets.presets.filter { preset ->
                val searchable = listOfNotNull(preset.name, titles[preset.id], details[preset.id]).joinToString(" ")
                terms.all { searchable.contains(it, ignoreCase = true) }
            }
        } else {
            when (group) {
                EqPresetGroup.Featured -> FeaturedEqPresets
                EqPresetGroup.Harman -> HarmanEqPresets
                EqPresetGroup.Tone -> ToneEqPresets
                EqPresetGroup.Genre -> GenreEqPresets
                EqPresetGroup.Saved -> emptyList()
            }
        }
    }
    val saved = remember(mainEqPresets, terms, group) {
        when {
            terms.isNotEmpty() -> mainEqPresets.filter { preset ->
                terms.all { preset.name.contains(it, ignoreCase = true) }
            }
            group == EqPresetGroup.Saved -> mainEqPresets
            else -> emptyList()
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(group, search) {
        if (builtIn.isNotEmpty() || saved.isNotEmpty()) {
            val current = when {
                search.isNotEmpty() -> 0
                group == EqPresetGroup.Saved -> saved.indexOfFirst { it.id == activeSavedId }
                else -> builtIn.indexOfFirst { it.id == selectedId }
            }
            val header = if (group == EqPresetGroup.Harman && current > 0) 1 else 0
            listState.scrollToItem((current + header).coerceAtLeast(0))
        }
    }
    val window = rememberEchoWindowLayout()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val close = stringResource(R.string.sound_help_close)

    EchoModalSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        val keyboard = LocalSoftwareKeyboardController.current
        Column(Modifier.fillMaxWidth().heightIn(max = (window.heightDp * .88f).dp).imePadding()) {
            EchoSheetHeader(
                title = title,
                subtitle = stringResource(R.string.eq_catalog_current, currentLabel),
                trailing = {
                    IconButton(onClick = onDismiss) { EchoIcon(Icons.Rounded.Close, close) }
                },
            )
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(100) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.eq_search_presets)) },
                    leadingIcon = { EchoIcon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = if (query.isEmpty()) null else ({
                        IconButton(onClick = { query = "" }) {
                            EchoIcon(Icons.Rounded.Close, stringResource(R.string.settings_search_clear))
                        }
                    }),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    singleLine = true,
                )
                if (search.isEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        EqPresetGroup.entries.forEach { category ->
                            FilterChip(
                                selected = group == category,
                                onClick = { group = category },
                                label = { Text(stringResource(category.labelRes)) },
                            )
                        }
                    }
                }
                if (!eqEnabled && enabled) {
                    TextButton(onClick = onEnableEq, contentPadding = PaddingValues(horizontal = 0.dp)) {
                        Text(stringResource(R.string.eq_catalog_turn_on))
                    }
                }
            }
            if (saved.isEmpty() && builtIn.isEmpty()) {
                val empty = if (search.isEmpty() && group == EqPresetGroup.Saved) R.string.eq_user_preset_empty
                else R.string.eq_no_presets_found
                Box(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) { SignalNote(stringResource(empty)) }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false).fillMaxWidth().selectableGroup(),
                    state = listState,
                    contentPadding = PaddingValues(start = 20.dp, top = 6.dp, end = 20.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (search.isEmpty() && group == EqPresetGroup.Harman) {
                        item("harman-note") {
                            Box(Modifier.padding(bottom = 6.dp)) {
                                SignalNote(stringResource(R.string.eq_catalog_harman_detail))
                            }
                        }
                    }
                    if (search.isNotEmpty() && builtIn.isNotEmpty() && saved.isNotEmpty()) {
                        item("built-in") { EqPresetSectionLabel(stringResource(R.string.eq_built_in_presets)) }
                    }
                    items(builtIn, key = { "built-in:${it.id}" }) { preset ->
                        val selected = activeSavedId == null && selectedId == preset.id
                        EqPresetOption(
                            label = titles[preset.id] ?: preset.name,
                            detail = details[preset.id],
                            selected = selected,
                            enabled = enabled,
                            preview = preset,
                            onClick = { if (!selected) onSelect(preset.id) },
                        )
                    }
                    if (search.isNotEmpty() && builtIn.isNotEmpty() && saved.isNotEmpty()) {
                        item("saved") { EqPresetSectionLabel(stringResource(R.string.eq_user_presets)) }
                    }
                    items(saved, key = { "user:${it.id}" }) { preset ->
                        EqPresetOption(
                            label = preset.name,
                            detail = null,
                            selected = preset.id == activeSavedId,
                            enabled = enabled,
                            onClick = { onApplyUserPreset(preset.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EqPresetSectionLabel(label: String) {
    Text(
        text = label,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun EqPresetOption(
    label: String,
    detail: String?,
    selected: Boolean,
    enabled: Boolean,
    preview: EchoEqualizerPresetDefinition? = null,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .clip(shape)
            .background(if (selected) colors.primary.copy(alpha = 0.10f) else colors.surfaceContainerLow)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        if (preview != null && LocalDensity.current.fontScale <= 1.3f) {
            SignalEqSparkline(
                preview.gainsDb,
                EchoEqualizerPresets.defaultFrequenciesHz,
                modifier = Modifier.width(56.dp),
                active = selected,
                rangeDb = 6f,
            )
        }
        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            if (selected) EchoIcon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
internal fun eqPresetLabel(id: String): String =
    EqPresetTitleRes[id]?.let { stringResource(it) } ?: EchoEqualizerPresets.nameFor(id)
