package app.echo.android.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.ArtworkTile
import app.echo.android.design.EchoIcon
import app.echo.android.design.EchoModalSheet
import app.echo.android.design.EchoSheetHeader
import app.echo.android.design.echoClickable
import app.echo.android.model.library.EchoPlaylist

/** Shared by the library, search, and now-playing entry points. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistDialog(
    playlists: List<EchoPlaylist>,
    onDismiss: () -> Unit,
    onSelectPlaylist: (EchoPlaylist) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    busy: Boolean = false,
    error: String? = null,
    awaitResult: Boolean = false,
    containingPlaylistIds: Set<String> = emptySet(),
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    var draftName by rememberSaveable { mutableStateOf("") }
    val editablePlaylists = remember(playlists) { playlists.filter { it.canEdit || it.isLikedSongs } }
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }

    EchoModalSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = windowHeight * 0.75f)) {
            EchoSheetHeader(
                title = stringResource(if (creating) R.string.feature_library_new_playlist_and_add_314ff2
                    else R.string.feature_library_add_to_playlist_0f8338),
                trailing = {
                    IconButton(onClick = onDismiss) {
                        EchoIcon(Icons.Rounded.Close, stringResource(R.string.feature_library_cancel_4c5fa5))
                    }
                },
            )
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 24.dp))
            if (error != null) Text(
                error, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
            )
            if (creating) {
                PlaylistCreateForm(
                    name = draftName,
                    onNameChange = { draftName = it },
                    busy = busy,
                    onCancel = { if (busy) onDismiss() else creating = false },
                    onConfirm = {
                        onCreatePlaylist(draftName.trim())
                        if (!awaitResult) creating = false
                    },
                    modifier = Modifier.weight(1f, fill = false),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                ) {
                    item(key = "create", contentType = "create") {
                        CreatePlaylistRow(enabled = !busy, onClick = { creating = true })
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                    if (editablePlaylists.isEmpty()) item(key = "empty", contentType = "empty") {
                        Text(
                            stringResource(R.string.feature_library_no_playlists_yet_create_one_first_6af586),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(editablePlaylists, key = { "playlist:${it.id}" }, contentType = { "playlist" }) { playlist ->
                        PlaylistDestinationRow(playlist, enabled = !busy,
                            alreadyAdded = playlist.id in containingPlaylistIds,
                            onClick = { onSelectPlaylist(playlist) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CreatePlaylistRow(enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.055f))
            .echoClickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        EchoIcon(Icons.Rounded.Add, null, Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f))
        Text(
            stringResource(R.string.feature_library_new_playlist_22cdbd), Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
        )
    }
}

@Composable
private fun PlaylistDestinationRow(playlist: EchoPlaylist, enabled: Boolean, alreadyAdded: Boolean, onClick: () -> Unit) {
    val foreground = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .echoClickable(enabled = enabled && !alreadyAdded, role = Role.Button, onClick = onClick)
            .heightIn(min = 76.dp).padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (!playlist.isLikedSongs && !playlist.artworkUri.isNullOrBlank()) {
            ArtworkTile(playlist.artworkUri, Modifier.size(48.dp), accent = rememberLibraryArtworkAccent(),
                cornerRadius = 12.dp, elevation = 0.dp)
        } else {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)),
                contentAlignment = Alignment.Center,
            ) {
                EchoIcon(if (playlist.isLikedSongs) Icons.Rounded.FavoriteBorder else Icons.Rounded.LibraryMusic,
                    null, Modifier.size(23.dp), tint = foreground)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(playlistDisplayName(playlist), style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium, color = foreground, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (alreadyAdded) stringResource(R.string.playlist_song_already_added) else libraryTrackCountLabel(playlist.trackCount), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f))
        }
        EchoIcon(if (alreadyAdded) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            null, Modifier.size(20.dp), tint = foreground)
    }
}

@Composable
private fun PlaylistCreateForm(
    name: String,
    onNameChange: (String) -> Unit,
    busy: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val canConfirm = name.isNotBlank() && !busy
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        OutlinedTextField(
            value = name, onValueChange = { onNameChange(it.take(80)) }, enabled = !busy,
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            label = { Text(stringResource(R.string.feature_library_name_57335e)) },
            shape = RoundedCornerShape(16.dp), singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (canConfirm) onConfirm() }),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.feature_library_cancel_4c5fa5)) }
            Button(onClick = onConfirm, enabled = canConfirm) {
                Text(stringResource(R.string.feature_library_create_7b4bc9))
            }
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}
