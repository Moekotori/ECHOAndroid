package app.echo.android.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.HistoryToggleOff
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoDropdownMenu
import app.echo.android.design.EchoDropdownMenuItem
import app.echo.android.design.EchoIcon
import app.echo.android.design.EchoSearchField

@Composable
internal fun PlaybackHistoryHeader(
    query: String,
    busy: Boolean,
    hasHistory: Boolean,
    onQuery: (String) -> Unit,
    onExport: () -> Unit,
    onPrune: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var searchExpanded by rememberSaveable { mutableStateOf(query.isNotEmpty()) }
    val searching = searchExpanded || query.isNotEmpty()
    val searchFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(searching) { if (searching) searchFocus.requestFocus() }

    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = {
            if (searching) {
                focusManager.clearFocus()
                keyboard?.hide()
                onQuery("")
                searchExpanded = false
            } else onBack()
        }) {
            EchoIcon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                stringResource(if (searching) R.string.playback_history_cancel else R.string.feature_home_back_49093c),
            )
        }
        if (searching) {
            EchoSearchField(
                query, onQuery, stringResource(R.string.playback_history_search),
                stringResource(R.string.playback_history_reset_search), Modifier.weight(1f).focusRequester(searchFocus),
            )
        } else {
            Column(Modifier.weight(1f).padding(start = 4.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.playback_history_title), style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.playback_history_subtitle), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { searchExpanded = true }) {
                EchoIcon(Icons.Rounded.Search, stringResource(R.string.playback_history_search))
            }
            IconButton(onClick = onExport, enabled = !busy && hasHistory) {
                EchoIcon(Icons.Rounded.FileDownload, stringResource(R.string.playback_history_export))
            }
            Row {
                IconButton(onClick = { menuOpen = true }) {
                    EchoIcon(Icons.Rounded.MoreHoriz, stringResource(R.string.playback_history_more), Modifier.size(24.dp))
                }
                EchoDropdownMenu(menuOpen, onDismissRequest = { menuOpen = false }) {
                    EchoDropdownMenuItem(
                        text = { Text(stringResource(R.string.playback_history_prune)) }, selected = false, enabled = !busy,
                        leadingIcon = { EchoIcon(Icons.Rounded.HistoryToggleOff, null) },
                        onClick = { menuOpen = false; onPrune() },
                    )
                    EchoDropdownMenuItem(
                        text = { Text(stringResource(R.string.playback_history_clear)) }, selected = false, enabled = !busy,
                        leadingIcon = { EchoIcon(Icons.Rounded.DeleteOutline, null) },
                        onClick = { menuOpen = false; onClear() },
                    )
                }
            }
        }
    }
}
