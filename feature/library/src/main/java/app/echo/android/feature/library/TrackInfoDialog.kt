package app.echo.android.feature.library

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.design.EchoModalSheet
import app.echo.android.design.EchoSheetHeader
import app.echo.android.design.displayMetadataOrUnknown
import app.echo.android.design.echoClickable
import app.echo.android.model.library.EchoPlaylist
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.libraryDisplayTitle
import app.echo.android.feature.library.R as L10nR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackInfoDialog(
    track: EchoTrack,
    onDismiss: () -> Unit,
    onOpenArtist: (() -> Unit)? = null,
    mediaTools: LibraryMediaTools? = LocalLibraryMediaTools.current,
    audioFormat: TrackInfoAudioFormat? = null,
) {
    var tool by remember(track.id) { mutableStateOf<String?>(null) }
    var showMissing by rememberSaveable(track.id) { mutableStateOf(false) }
    val setRating = mediaTools?.setRating?.takeIf { track.supportsLocalRating() }
    var rating by remember(track.id, track.rating) { mutableIntStateOf(track.rating) }
    var showRating by rememberSaveable(track.id) { mutableStateOf(false) }
    val artistLabel = stringResource(L10nR.string.feature_library_go_to_artist)
    val sections = trackInfoSections(track, audioFormat)
    var playlistRetry by remember(track.id) { mutableIntStateOf(0) }
    val membership by rememberTrackInfoPlaylists(track, mediaTools, playlistRetry)
    val playlists = membership.playlists.orEmpty()
    val playlistsUnavailable = membership.playlists == null && !membership.loading && !membership.failed
    val playlistLabel = stringResource(L10nR.string.track_info_playlists)
    val likedName = stringResource(L10nR.string.feature_library_liked_songs_8d6245)
    val missing = stringResource(L10nR.string.feature_library_not_available_7d3220)
    val membershipMessage = when {
        membership.loading -> stringResource(L10nR.string.feature_library_loading_playlist_eb6248)
        membership.failed -> stringResource(L10nR.string.track_info_playlists_failed)
        playlistsUnavailable -> missing
        playlists.isEmpty() -> stringResource(L10nR.string.track_info_playlists_empty)
        else -> null
    }
    fun playlistName(playlist: EchoPlaylist): String =
        if (playlist.isLikedSongs) likedName else playlist.name.ifBlank { missing }
    val fields = sections.flatMap { it.fields }
    val missingLabels = buildList {
        fields.forEach { if (it.value == null) add(it.label) }
        if (playlistsUnavailable) add(playlistLabel)
    }
    val context = LocalContext.current
    val copyLabel = stringResource(L10nR.string.track_info_copy)
    val copiedLabel = stringResource(L10nR.string.track_info_copied)
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val maxContentHeight = if (windowHeight > 0.dp) minOf(560.dp, windowHeight * 0.70f) else 560.dp
    EchoModalSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxContentHeight)) {
            EchoSheetHeader(
                title = displayMetadataOrUnknown(track.libraryDisplayTitle(LocalLibraryFileNames.current), unknownTrackLabel()),
                modifier = Modifier.semantics { heading() },
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (setRating != null) {
                    item(key = "rating") { TrackRatingRow(rating) { showRating = true } }
                }
                sections.forEach { section ->
                    if (section.fields.none { it.value != null }) return@forEach
                    item(key = "section:${section.title}") { TrackInfoHeading(section.title) }
                    section.fields.forEachIndexed { index, field ->
                        val value = field.value ?: return@forEachIndexed
                        item(key = "field:${section.title}:$index") {
                            TrackInfoLine(field.label, value, onOpenArtist?.takeIf { field.opensArtist }, artistLabel)
                        }
                    }
                }
                if (!playlistsUnavailable) {
                    item(key = "playlists") { TrackInfoHeading(L10nR.string.track_info_playlists) }
                    if (membershipMessage != null) item(key = "playlist-status") {
                        Column {
                            SelectionContainer { Text(membershipMessage, style = MaterialTheme.typography.bodyMedium) }
                            if (membership.failed) TextButton(onClick = { playlistRetry++ }) {
                                Text(stringResource(L10nR.string.hidden_track_retry))
                            }
                        }
                    }
                    items(playlists, key = { "playlist:${it.id}" }) { playlist ->
                        SelectionContainer { Text(playlistName(playlist), style = MaterialTheme.typography.bodyMedium) }
                    }
                    if (playlists.isNotEmpty()) item(key = "playlist-scope") {
                        Text(
                            stringResource(L10nR.string.track_info_playlists_scope),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (mediaTools != null && track.source.isLocalAudioFile) item(key = "media-tools") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { tool = "encoding" }) { Text(stringResource(L10nR.string.encoding_title)) }
                        TextButton(onClick = { tool = "images" }) { Text(stringResource(L10nR.string.album_images_title)) }
                    }
                }
                if (missingLabels.isNotEmpty()) {
                    item(key = "missing-toggle") {
                        TrackInfoMissingToggle(missingLabels.size, showMissing) { showMissing = !showMissing }
                    }
                    if (showMissing) items(missingLabels.size, key = { "missing:$it" }) { index ->
                        TrackInfoLine(missingLabels[index], missing)
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            ) {
                TextButton(enabled = !membership.loading && !membership.failed, onClick = {
                    context.getSystemService(ClipboardManager::class.java)?.let { clipboard ->
                        val details = buildString {
                            fields.forEach { append(it.label).append(": ").append(it.value ?: missing).append('\n') }
                            append(playlistLabel).append(": ")
                            if (membershipMessage != null) append(membershipMessage)
                            else playlists.forEach { append('\n').append(playlistName(it)) }
                        }
                        clipboard.setPrimaryClip(ClipData.newPlainText(copyLabel, details))
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            Toast.makeText(context, copiedLabel, Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text(copyLabel) }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(L10nR.string.feature_library_done_a5e659))
                }
            }
        }
    }
    if (showRating && setRating != null) {
        TrackRatingControl(
            track = track.copy(rating = rating),
            set = { _, choice -> setRating(track, choice).also { rating = it } },
            onDismiss = { showRating = false },
        )
    }
    if (mediaTools != null) when (tool) {
        "encoding" -> TrackEncodingDialog(track, mediaTools) { tool = null }
        "images" -> AlbumImagesDialog(track, mediaTools) { tool = null }
    }
}

@Composable
private fun TrackInfoHeading(title: Int) {
    Text(
        stringResource(title),
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp).semantics { heading() },
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun TrackInfoMissingToggle(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    TextButton(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth().semantics {
            if (expanded) collapse { onToggle(); true } else expand { onToggle(); true }
        },
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        Text(
            stringResource(L10nR.string.track_info_missing_fields, count),
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
        )
        EchoIcon(
            if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TrackInfoLine(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.echoClickable(
                        onClickLabel = onClickLabel,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            modifier = Modifier.weight(0.3f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
        SelectionContainer(Modifier.weight(0.7f)) {
            Text(
                value,
                color = if (onClick != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

