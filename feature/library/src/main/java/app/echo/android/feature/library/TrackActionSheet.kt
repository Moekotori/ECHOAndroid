package app.echo.android.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Queue
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoSheetRow
import app.echo.android.model.settings.EchoTrackMenuAction
import app.echo.android.model.library.EchoTrack
import app.echo.android.feature.library.R as L10nR

@Composable
internal fun TrackActionSheet(
    track: EchoTrack,
    canEditMetadata: Boolean,
    canImportLyrics: Boolean,
    canPickArtwork: Boolean,
    canAddToPlaylist: Boolean,
    canPlayNext: Boolean,
    canAddNextUp: Boolean,
    canEnqueue: Boolean,
    canRemoveFromPlaylist: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canOpenArtist: Boolean,
    canOpenAlbum: Boolean,
    canPlayOnPc: Boolean = false,
    onPlay: () -> Unit,
    onPlayOnPc: () -> Unit = {},
    onOpenArtist: () -> Unit,
    onOpenAlbum: () -> Unit,
    onEdit: () -> Unit,
    onImportLyrics: () -> Unit,
    onPickArtwork: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onPlayNext: () -> Unit,
    onAddNextUp: () -> Unit,
    onEnqueue: () -> Unit,
    onRemoveFromPlaylist: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onInfo: () -> Unit,
    onSelectForBatch: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onVersions: (() -> Unit)? = null,
    onHide: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onRangeStart: (() -> Unit)? = null,
    onRangeEnd: (() -> Unit)? = null,
) {
    // Constrain content, not the ModalBottomSheet: its anchors need the full window height.
    // Short windows need more room for actions while retaining the handle and safe insets.
    val hiddenActions = LocalHiddenTrackActions.current
    fun shown(action: app.echo.android.model.settings.EchoTrackMenuAction) = action !in hiddenActions
    val setRating = LocalLibraryMediaTools.current?.setRating?.takeIf { track.supportsLocalRating() }
    var showRating by rememberSaveable(track.id) { mutableStateOf(false) }
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxHeight = minOf(500.dp, screenHeight * if (screenHeight < 600.dp) 0.82f else 0.58f)
    Column(Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
        TrackSheetHeader(track, artworkSize = 40.dp, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(6.dp))
        // Only the actions scroll. At the top, downward scroll passes to the sheet.
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState(), overscrollEffect = null)
                .padding(bottom = 10.dp),
        ) {
            if (setRating != null) {
                EchoSheetRow(
                    label = stringResource(L10nR.string.track_rating),
                    icon = Icons.Rounded.StarBorder,
                    onClick = { showRating = true },
                    compact = true,
                )
            }
            if (onSelectForBatch != null) {
                TrackActionRow(stringResource(L10nR.string.library_multiselect), Icons.Rounded.Checklist, enabled = true, onClick = onSelectForBatch)
            }
            if (onRangeStart != null) TrackActionRow(stringResource(L10nR.string.library_range_start), Icons.Rounded.Checklist, true, onRangeStart)
            if (onRangeEnd != null) TrackActionRow(stringResource(L10nR.string.library_range_end), Icons.Rounded.Checklist, true, onRangeEnd)
            TrackActionRow(stringResource(L10nR.string.feature_library_play_38419a), Icons.Rounded.PlayArrow, enabled = true, onClick = onPlay)
            if (canPlayOnPc) {
                TrackActionRow(
                    stringResource(L10nR.string.feature_library_play_on_pc_7c21a4),
                    Icons.Rounded.Computer,
                    enabled = true,
                    onClick = onPlayOnPc,
                )
            }
            if (canOpenArtist) {
                TrackActionRow(
                    stringResource(L10nR.string.feature_library_go_to_artist),
                    Icons.Rounded.Person,
                    enabled = true,
                    onClick = onOpenArtist,
                )
            }
            if (canOpenAlbum) {
                TrackActionRow(
                    stringResource(L10nR.string.feature_library_go_to_album),
                    Icons.Rounded.Album,
                    enabled = true,
                    onClick = onOpenAlbum,
                )
            }
            if (canAddNextUp) {
                TrackActionRow(stringResource(L10nR.string.queue_add_next_up), Icons.Rounded.Queue, enabled = true, onClick = onAddNextUp)
            }
            if (canPlayNext) {
                TrackActionRow(
                    stringResource(L10nR.string.feature_library_play_next_a1f73e),
                    Icons.Rounded.SkipNext,
                    enabled = true,
                    onClick = onPlayNext,
                )
            }
            if (canEnqueue) {
                TrackActionRow(
                    stringResource(L10nR.string.queue_append_original),
                    Icons.Rounded.Queue,
                    enabled = true,
                    onClick = onEnqueue,
                )
            }
            if (canAddToPlaylist) {
                TrackActionRow(
                    stringResource(L10nR.string.feature_library_add_to_playlist_0f8338),
                    Icons.AutoMirrored.Rounded.PlaylistAdd,
                    enabled = true,
                    onClick = onAddToPlaylist,
                )
            }
            if (canMoveUp) {
                TrackActionRow(stringResource(L10nR.string.feature_library_move_up_e6d961), Icons.Rounded.KeyboardArrowUp, enabled = true, onClick = onMoveUp)
            }
            if (canMoveDown) {
                TrackActionRow(stringResource(L10nR.string.feature_library_move_down_cf81ae), Icons.Rounded.KeyboardArrowDown, enabled = true, onClick = onMoveDown)
            }
            if (canRemoveFromPlaylist) {
                TrackActionRow(
                    stringResource(L10nR.string.feature_library_remove_from_playlist_f07718),
                    Icons.Rounded.DeleteOutline,
                    enabled = true,
                    onClick = onRemoveFromPlaylist,
                )
            }
            LocalTrackActionOrder.current.forEach { action ->
                if (shown(action)) when (action) {
                    EchoTrackMenuAction.EditTags -> TrackActionRow(stringResource(L10nR.string.feature_library_edit_tags_c8ec75), Icons.Rounded.Edit, canEditMetadata, onEdit)
                    EchoTrackMenuAction.ImportLyrics -> TrackActionRow(stringResource(L10nR.string.feature_library_import_lrc_lyrics_8e2810), Icons.Rounded.UploadFile, canImportLyrics, onImportLyrics)
                    EchoTrackMenuAction.Artwork -> TrackActionRow(stringResource(L10nR.string.feature_library_custom_artwork_c8c999), Icons.Rounded.Album, canPickArtwork, onPickArtwork)
                    EchoTrackMenuAction.Versions -> if (onVersions != null) TrackActionRow(stringResource(L10nR.string.track_versions_title), Icons.Rounded.Album, true, onVersions)
                    EchoTrackMenuAction.Hide -> if (onHide != null) TrackActionRow(stringResource(L10nR.string.hidden_track_hide), Icons.Rounded.VisibilityOff, true, onHide)
                    EchoTrackMenuAction.Delete -> if (onDelete != null) TrackActionRow(stringResource(L10nR.string.feature_library_delete_138ccf), Icons.Rounded.DeleteOutline, true, onDelete, danger = true)
                }
            }
            TrackActionRow(stringResource(L10nR.string.feature_library_track_info_36a07b), Icons.Rounded.Info, enabled = true, onClick = onInfo)
            if (onShare != null) TrackActionRow(stringResource(L10nR.string.audio_share_action), Icons.Rounded.Share,
                enabled = true, onClick = onShare)
        }
    }
    if (showRating && setRating != null) {
        TrackRatingControl(track, setRating, onDismiss = { showRating = false })
    }
}

@Composable
private fun TrackActionRow(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    EchoSheetRow(label = label, icon = icon, onClick = onClick, enabled = enabled, danger = danger, chevron = false, compact = true)
}
