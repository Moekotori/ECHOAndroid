package app.echo.android.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.design.EchoModalSheet
import app.echo.android.design.EchoSheetHeader
import app.echo.android.design.EchoSwitch
import app.echo.android.design.echoClickable
import app.echo.android.model.library.ArtistSortMode

@Composable
internal fun LibraryArtistSortMenu(
    selectedSortMode: ArtistSortMode,
    onSortModeChange: (ArtistSortMode) -> Unit,
    onCustomize: (() -> Unit)? = null,
    linked: Boolean = false,
    showAlbumArtists: Boolean = false,
    onShowAlbumArtistsChange: ((Boolean) -> Unit)? = null,
) {
    LibrarySortMenu(
        selected = selectedSortMode,
        options = ArtistSortMode.entries,
        label = { it.label() },
        contentDescription = stringResource(R.string.feature_library_set_artist_sort_order_e15a06),
        onSelect = onSortModeChange,
        onCustomize = onCustomize,
        scopeNote = if (linked) stringResource(R.string.library_sort_loaded_items) else null,
        additionalOptions = { dismiss ->
            if (onShowAlbumArtistsChange != null) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .toggleable(value = showAlbumArtists, role = Role.Switch, onValueChange = {
                            dismiss()
                            onShowAlbumArtistsChange(it)
                        })
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.library_show_album_artists),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    EchoSwitch(checked = showAlbumArtists, onCheckedChange = null)
                }
            }
        },
    )
}

/** Shared by local and linked library categories; sorting stays with the existing caller. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> LibrarySortMenu(
    selected: T,
    options: List<T>,
    label: @Composable (T) -> String,
    contentDescription: String,
    onSelect: (T) -> Unit,
    onCustomize: (() -> Unit)? = null,
    scopeNote: String? = null,
    section: (T) -> Int = { 0 },
    additionalOptions: @Composable (dismiss: () -> Unit) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    LibrarySortButton(contentDescription = contentDescription, onClick = { expanded = true })
    if (!expanded) return

    val maxContentHeight = minOf(720.dp, with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() * 0.82f })
    EchoModalSheet(
        onDismissRequest = { expanded = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxContentHeight)) {
            EchoSheetHeader(
                title = stringResource(R.string.library_sort_title),
                subtitle = scopeNote,
            )
            // Bound the scrolling content, not the sheet, so its expanded anchor remains stable.
            Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                LibrarySortChoices(selected, options, label, onSelect = { expanded = false; onSelect(it) }, section = section)
                additionalOptions { expanded = false }
            }
            if (onCustomize != null) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .echoClickable(role = Role.Button) { expanded = false; onCustomize() }
                        .heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.library_layout_title),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    EchoIcon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Spacer(Modifier.size(20.dp))
            }
        }
    }
}

@Composable
internal fun <T> LibrarySortChoices(
    selected: T,
    options: List<T>,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    section: (T) -> Int = { 0 },
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= (136 * LocalDensity.current.fontScale).dp * 2) 2 else 1
        Column(
            Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                options.groupBy(section).forEach { (title, sectionOptions) ->
                    if (title != 0) Text(
                        stringResource(title),
                        modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 2.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    repeat((sectionOptions.size + columns - 1) / columns) { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            repeat(columns) { column ->
                                val index = row * columns + column
                                if (index < sectionOptions.size) {
                                    val option = sectionOptions[index]
                                    LibrarySortOption(
                                        text = label(option),
                                        selected = option == selected,
                                        modifier = Modifier.weight(1f),
                                        onClick = { onSelect(option) },
                                    )
                                } else {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibrarySortOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.clip(RoundedCornerShape(16.dp))
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest) else Modifier)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        if (selected) EchoIcon(
            Icons.Rounded.Check,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}
