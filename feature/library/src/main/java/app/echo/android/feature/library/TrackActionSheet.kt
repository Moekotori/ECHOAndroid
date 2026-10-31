package app.echo.android.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoSheetRow
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
) {
    // Constrain content, not the ModalBottomSheet: its anchors need the full window height.
    // Short windows need more room for actions while retaining the handle and safe insets.
    val hiddenActions = LocalHiddenTrackActions.current
    fun shown(action: app.echo.android.model.settings.EchoTrackMenuAction) = action !in hiddenActions
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxHeight = minOf(500.dp, screenHeight * if (screenHeight < 600.dp) 0.82f else 0.58f)
    Column(Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
        TrackSheetHeader(track, artworkSize = 52.dp, modifier = Modifier.padding(horizontal = 24.dp))
        Spacer(Modifier.height(12.dp))
        // Only the actions scroll. At the top, downward scroll passes to the sheet.
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState(), overscrollEffect = null)
                .padding(bottom = 16.dp),
        ) {
            if (onSelectForBatch != null) {
                TrackActionRow(stringResource(L10nR.string.library_multiselect), Icons.Rounded.Checklist, enabled = true, onClick = onSelectForBatch)
            }
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
            if (shown(app.echo.android.model.settings.EchoTrackMenuAction.EditTags)) TrackActionRow(stringResource(L10nR.string.feature_library_edit_tags_c8ec75), Icons.Rounded.Edit, enabled = canEditMetadata, onClick = onEdit)
            if (shown(app.echo.android.model.settings.EchoTrackMenuAction.ImportLyrics)) TrackActionRow(
                stringResource(L10nR.string.feature_library_import_lrc_lyrics_8e2810),
                Icons.Rounded.UploadFile,
                enabled = canImportLyrics,
                onClick = onImportLyrics,
            )
            if (shown(app.echo.android.model.settings.EchoTrackMenuAction.Artwork)) TrackActionRow(
                stringResource(L10nR.string.feature_library_custom_artwork_c8c999),
                Icons.Rounded.Album,
                enabled = canPickArtwork,
                onClick = onPickArtwork,
            )
            if (onVersions != null && shown(app.echo.android.model.settings.EchoTrackMenuAction.Versions)) {
                TrackActionRow(stringResource(L10nR.string.track_versions_title), Icons.Rounded.Album, enabled = true, onClick = onVersions)
            }
            TrackActionRow(stringResource(L10nR.string.feature_library_track_info_36a07b), Icons.Rounded.Info, enabled = true, onClick = onInfo)
            if (onShare != null) TrackActionRow(stringResource(L10nR.string.audio_share_action), Icons.Rounded.Share,
                enabled = true, onClick = onShare)
            if (onHide != null && shown(app.echo.android.model.settings.EchoTrackMenuAction.Hide)) {
                TrackActionRow(stringResource(L10nR.string.hidden_track_hide), Icons.Rounded.VisibilityOff,
                    enabled = true, onClick = onHide)
            }
            if (onDelete != null && shown(app.echo.android.model.settings.EchoTrackMenuAction.Delete)) {
                TrackActionRow(stringResource(L10nR.string.feature_library_delete_138ccf), Icons.Rounded.DeleteOutline, enabled = true, danger = true, onClick = onDelete)
            }
        }
    }
}

@Composable
private fun TrackActionRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    EchoSheetRow(label = label, icon = icon, onClick = onClick, enabled = enabled, danger = danger, chevron = false)
}
