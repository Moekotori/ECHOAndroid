package app.echo.android.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import app.echo.android.design.EchoIcon

enum class SearchTrackAction(val labelRes: Int) {
    PlayNext(R.string.search_play_next), AddNextUp(R.string.search_add_next_up),
    Enqueue(R.string.search_enqueue), AddToPlaylist(R.string.search_add_to_playlist),
}

@Composable
internal fun SearchTrackActions(phoneOnly: Boolean = false, onAction: (SearchTrackAction) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) { EchoIcon(Icons.Rounded.MoreHoriz, stringResource(R.string.search_track_actions)) }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            SearchTrackAction.entries.filter { !phoneOnly || it != SearchTrackAction.AddToPlaylist }.forEach { action ->
                val label = stringResource(action.labelRes)
                DropdownMenuItem(text = { Text(if (phoneOnly) stringResource(R.string.search_on_phone, label) else label) }, onClick = { expanded = false; onAction(action) })
            }
        }
    }
}
