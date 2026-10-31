package app.echo.android.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.design.EchoModalSheet
import app.echo.android.design.EchoSheetHeader
import app.echo.android.design.EchoSwitch
import app.echo.android.design.rememberEchoHapticPerformer
import app.echo.android.model.library.*
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun LibraryTrackFilterButton(filter: LibraryTrackFilter, onChange: (LibraryTrackFilter) -> Unit,
    lyricsIndexState: StateFlow<LibraryLyricsIndexState>?, onInspectLyrics: () -> Unit, onCancelLyrics: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        BadgedBox(badge = { if (filter.isActive) Badge { Text(filter.activeCount.toString()) } }) {
            EchoIcon(Icons.Rounded.FilterList, stringResource(R.string.library_filter_title), Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (open) LibraryTrackFilterSheet(filter, lyricsIndexState, onInspectLyrics, onCancelLyrics, onDismiss = { open = false }, onApply = { next ->
        open = false
        onChange(next)
    })
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LibraryTrackFilterSheet(
    filter: LibraryTrackFilter,
    lyricsIndexState: StateFlow<LibraryLyricsIndexState>?,
    onInspectLyrics: () -> Unit,
    onCancelLyrics: () -> Unit,
    onDismiss: () -> Unit,
    onApply: (LibraryTrackFilter) -> Unit,
) {
    var draft by rememberSaveable(stateSaver = LibraryTrackFilterDraftSaver) { mutableStateOf(LibraryTrackFilterDraft(filter)) }
    val focus = LocalFocusManager.current
    val rangeFocus = remember { List(8) { FocusRequester() } }
    val applied = remember(draft) { draft.applied() }
    val maxHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() * 0.85f }
    EchoModalSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
            EchoSheetHeader(title = stringResource(R.string.library_filter_title),
                subtitle = stringResource(R.string.library_filter_scope),
                trailing = { TextButton(onClick = { draft = LibraryTrackFilterDraft(LibraryTrackFilter()) },
                    enabled = draft.filter.isActive || draft.ranges.any(String::isNotEmpty)) {
                    Text(stringResource(R.string.library_filter_reset))
                } })
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterSection(R.string.library_filter_conditions, activeCount = draft.conditionCount,
                    initiallyExpanded = !filter.isActive) {
                    FilterToggle(R.string.library_filter_favorites, draft.filter.favoritesOnly) {
                        draft = draft.copy(filter = draft.filter.copy(favoritesOnly = it))
                    }
                    Text(stringResource(R.string.library_filter_played), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LibraryPlayedFilter.entries.forEach { value ->
                            FilterChip(selected = draft.filter.played == value,
                                onClick = { draft = draft.copy(filter = draft.filter.copy(played = value)) },
                                label = { Text(stringResource(when (value) {
                                    LibraryPlayedFilter.Any -> R.string.library_filter_any
                                    LibraryPlayedFilter.Played -> R.string.library_filter_played_yes
                                    LibraryPlayedFilter.NeverPlayed -> R.string.library_filter_played_no
                                })) })
                        }
                    }
                    LibraryLyricsFilterControls(draft.filter.lyrics,
                        onSelect = { draft = draft.copy(filter = draft.filter.copy(lyrics = it)) },
                        indexState = lyricsIndexState, onInspect = onInspectLyrics, onCancel = onCancelLyrics)
                    Text(stringResource(R.string.library_filter_artwork), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LibraryPresenceFilter.entries.forEach { value ->
                            FilterChip(selected = draft.filter.artwork == value,
                                onClick = { draft = draft.copy(filter = draft.filter.copy(artwork = value)) },
                                label = { Text(stringResource(when (value) {
                                    LibraryPresenceFilter.Any -> R.string.library_filter_any
                                    LibraryPresenceFilter.Present -> R.string.library_filter_artwork_yes
                                    LibraryPresenceFilter.Missing -> R.string.library_filter_artwork_no
                                })) })
                        }
                    }
                    FilterToggle(R.string.library_filter_duplicates, draft.filter.duplicatesOnly) {
                        draft = draft.copy(filter = draft.filter.copy(duplicatesOnly = it))
                    }
                    Text(stringResource(R.string.library_filter_duplicates_hint), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilterSection(R.string.library_filter_tags, activeCount = draft.tagCount) {
                    FilterTextField(R.string.library_filter_artist, draft.filter.artist) { draft = draft.copy(filter = draft.filter.copy(artist = it)) }
                    FilterTextField(R.string.library_filter_album, draft.filter.album) { draft = draft.copy(filter = draft.filter.copy(album = it)) }
                    FilterTextField(R.string.library_filter_genre, draft.filter.genre) { draft = draft.copy(filter = draft.filter.copy(genre = it)) }
                    FilterTextField(R.string.library_filter_composer, draft.filter.composer) { draft = draft.copy(filter = draft.filter.copy(composer = it)) }
                    FilterTextField(R.string.library_filter_folder, draft.filter.folder, limit = 500) { draft = draft.copy(filter = draft.filter.copy(folder = it)) }
                    Text(stringResource(R.string.library_filter_contains_hint), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilterSection(R.string.library_filter_formats, activeCount = draft.filter.formats.size) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = draft.filter.formats.isEmpty(),
                            onClick = { draft = draft.copy(filter = draft.filter.copy(formats = emptySet())) },
                            label = { Text(stringResource(R.string.library_filter_any)) })
                        LibraryFileFormat.entries.forEach { format ->
                            FilterChip(selected = format in draft.filter.formats,
                                onClick = { draft = draft.copy(filter = draft.filter.copy(formats =
                                    if (format in draft.filter.formats) draft.filter.formats - format else draft.filter.formats + format)) },
                                label = { Text(format.displayName()) })
                        }
                    }
                    Text(stringResource(R.string.library_filter_formats_hint), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilterSection(R.string.library_filter_ranges, activeCount = draft.rangeCount) {
                    listOf(R.string.library_filter_year, R.string.library_filter_duration, R.string.library_filter_size,
                        R.string.library_filter_sample_rate).forEachIndexed { index, title ->
                        Text(stringResource(title), style = MaterialTheme.typography.labelLarge)
                        if (index == 1 || index == 3) {
                            FilterRangePresets(index, draft) { draft = it }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            repeat(2) { bound ->
                                val field = index * 2 + bound
                                OutlinedTextField(value = draft.ranges[field], onValueChange = { value ->
                                    draft = draft.copy(ranges = draft.ranges.toMutableList().also { it[field] = value.take(40) })
                                }, modifier = Modifier.weight(1f).focusRequester(rangeFocus[field]), singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = if (index == 1 || index == 2) KeyboardType.Decimal else KeyboardType.Number,
                                        imeAction = if (field == 7) ImeAction.Done else ImeAction.Next),
                                    keyboardActions = KeyboardActions(onNext = {
                                        if (field < 7) rangeFocus[field + 1].requestFocus() else focus.clearFocus()
                                    },
                                        onDone = { focus.clearFocus() }),
                                    label = { Text(stringResource(if (bound == 0) R.string.library_filter_min else R.string.library_filter_max)) })
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (applied == null) Text(stringResource(R.string.library_filter_invalid_range),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_filter_cancel)) }
                Button(onClick = {
                    focus.clearFocus()
                    applied?.let { if (it == filter) onDismiss() else onApply(it) }
                }, enabled = applied != null) {
                    Text(stringResource(R.string.library_filter_apply))
                }
            }
        }
    }
}

@Composable
private fun FilterSection(title: Int, activeCount: Int, initiallyExpanded: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded || activeCount > 0) }
    Column {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 12.dp)) {
            Text(stringResource(title), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
            if (activeCount > 0) Text(activeCount.toString(), modifier = Modifier.padding(horizontal = 8.dp),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            EchoIcon(if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, null,
                Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (expanded) Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun FilterToggle(title: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    val haptics = rememberEchoHapticPerformer()
    Row(Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch,
        onValueChange = { haptics.tick(); onChange(it) })
        .heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(title), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        EchoSwitch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun FilterTextField(title: Int, value: String, limit: Int = 200, onChange: (String) -> Unit) {
    val focus = LocalFocusManager.current
    val lastField = title == R.string.library_filter_folder
    OutlinedTextField(value = value, onValueChange = { onChange(it.take(limit)) }, singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = if (lastField) ImeAction.Done else ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }, onDone = { focus.clearFocus() }),
        modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(title)) })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterRangePresets(pair: Int, draft: LibraryTrackFilterDraft, onChange: (LibraryTrackFilterDraft) -> Unit) {
    val presets: List<Triple<Long?, Long?, String>> = if (pair == 1) listOf(
        Triple(null, 180L, stringResource(R.string.library_filter_duration_short)),
        Triple(180L, 360L, stringResource(R.string.library_filter_duration_medium)),
        Triple(360L, null, stringResource(R.string.library_filter_duration_long)),
    ) else listOf(
        Triple(44_100L, 44_100L, "44.1 kHz"),
        Triple(48_000L, 48_000L, "48 kHz"),
        Triple(88_200L, null, "≥88.2 kHz"),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        presets.forEach { (min, max, label) ->
            val selected = draft.ranges[pair * 2] == min?.toString().orEmpty() &&
                draft.ranges[pair * 2 + 1] == max?.toString().orEmpty()
            FilterChip(selected = selected, label = { Text(label) }, onClick = {
                onChange(if (selected) draft.withRange(pair, null, null) else draft.withRange(pair, min, max))
            })
        }
    }
}

internal fun LibraryFileFormat.displayName(): String = when (this) {
    LibraryFileFormat.M4a -> "M4A / MP4"
    LibraryFileFormat.Dsd -> "DSD (DSF / DFF)"
    LibraryFileFormat.WavPack -> "WavPack"
    else -> name.uppercase()
}
