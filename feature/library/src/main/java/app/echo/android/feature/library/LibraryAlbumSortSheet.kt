package app.echo.android.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.design.EchoModalSheet
import app.echo.android.design.EchoSheetHeader
import app.echo.android.design.EchoSheetRow
import app.echo.android.model.library.AlbumSortMode

@Composable
internal fun LibraryAlbumSortMenu(
    selectedSortMode: AlbumSortMode,
    onSortModeChange: (AlbumSortMode) -> Unit,
    onCustomize: (() -> Unit)? = null,
    linked: Boolean = false,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        EchoIcon(
            Icons.AutoMirrored.Rounded.Sort,
            contentDescription = stringResource(R.string.feature_library_set_album_sort_order_7c2d91),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (open) LibraryAlbumSortSheet(
        selected = selectedSortMode,
        linked = linked,
        onDismiss = { open = false },
        onApply = {
            open = false
            onSortModeChange(it)
        },
        onCustomize = onCustomize,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryAlbumSortSheet(
    selected: AlbumSortMode,
    linked: Boolean,
    onDismiss: () -> Unit,
    onApply: (AlbumSortMode) -> Unit,
    onCustomize: (() -> Unit)?,
) {
    var draftMode by rememberSaveable(selected, linked) { mutableStateOf(selected) }
    val draft = if (linked) linkedAlbumSort(draftMode) else draftMode
    val fields = if (linked) LinkedAlbumSortFields else AlbumSortField.entries
    val field = fields.first { draft == it.ascending || draft == it.descending }
    val maxHeight = minOf(680.dp, with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() * 0.82f })
    EchoModalSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
            EchoSheetHeader(
                title = stringResource(R.string.album_sort_heading),
                subtitle = if (linked) stringResource(R.string.library_sort_loaded_items) else null,
                leading = {
                    EchoIcon(Icons.Rounded.Album, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
            )
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                Column(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AlbumSortSectionLabel(R.string.album_sort_field)
                    LibrarySortChoices(
                        selected = field,
                        options = fields,
                        label = { stringResource(it.label) },
                        onSelect = { if (it != field) draftMode = it.defaultMode },
                    )
                    AlbumSortSectionLabel(R.string.album_sort_direction, Modifier.padding(top = 4.dp))
                    AlbumSortDirection(field, draft, onSelect = { draftMode = it })
                }
                if (onCustomize != null) {
                    EchoSheetRow(
                        label = stringResource(R.string.library_layout_title),
                        icon = Icons.Rounded.Tune,
                        compact = true,
                        onClick = {
                            onDismiss()
                            onCustomize()
                        },
                    )
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_filter_cancel)) }
                Button(onClick = { onApply(draft) }, enabled = draft != selected) {
                    Text(stringResource(R.string.library_filter_apply))
                }
            }
        }
    }
}

@Composable
private fun AlbumSortSectionLabel(label: Int, modifier: Modifier = Modifier) {
    Text(
        stringResource(label),
        modifier.semantics { heading() },
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AlbumSortDirection(field: AlbumSortField, selected: AlbumSortMode, onSelect: (AlbumSortMode) -> Unit) {
    val choices = listOf(field.ascending to field.ascendingLabel, field.descending to field.descendingLabel)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        choices.forEachIndexed { index, (mode, label) ->
            SegmentedButton(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, choices.size, MaterialTheme.shapes.medium),
                icon = {
                    if (selected == mode) EchoIcon(Icons.Rounded.Check, null, Modifier.size(16.dp))
                },
            ) {
                Text(stringResource(label), textAlign = TextAlign.Center)
            }
        }
    }
}

private enum class AlbumSortField(
    val label: Int,
    val ascending: AlbumSortMode,
    val descending: AlbumSortMode,
    val ascendingLabel: Int,
    val descendingLabel: Int,
    val defaultMode: AlbumSortMode = descending,
) {
    Title(
        R.string.album_sort_album_title, AlbumSortMode.Title, AlbumSortMode.TitleDescending,
        R.string.album_sort_a_to_z, R.string.album_sort_z_to_a, defaultMode = AlbumSortMode.Title,
    ),
    Artist(
        R.string.feature_library_album_artist_6defa0, AlbumSortMode.Artist, AlbumSortMode.ArtistDescending,
        R.string.album_sort_a_to_z, R.string.album_sort_z_to_a, defaultMode = AlbumSortMode.Artist,
    ),
    Year(
        R.string.feature_library_year_fe373f, AlbumSortMode.YearAscending, AlbumSortMode.Year,
        R.string.album_sort_oldest_first, R.string.album_sort_newest_first,
    ),
    TrackCount(
        R.string.album_sort_track_count, AlbumSortMode.TrackCountAscending, AlbumSortMode.TrackCount,
        R.string.album_sort_fewest_first, R.string.album_sort_most_first,
    ),
    Duration(
        R.string.feature_library_duration_573e08, AlbumSortMode.DurationAscending, AlbumSortMode.Duration,
        R.string.album_sort_shortest_first, R.string.album_sort_longest_first,
    ),
    Added(
        R.string.album_sort_date_added, AlbumSortMode.OldestAdded, AlbumSortMode.RecentlyAdded,
        R.string.album_sort_oldest_first, R.string.album_sort_newest_first,
    ),
}

private val LinkedAlbumSortFields = AlbumSortField.entries.filter {
    it.ascending in LinkedAlbumSortOptions && it.descending in LinkedAlbumSortOptions
}
