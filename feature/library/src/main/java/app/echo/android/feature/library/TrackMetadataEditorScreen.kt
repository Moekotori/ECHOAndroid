package app.echo.android.feature.library

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.ArtworkTile
import app.echo.android.design.EchoIcon
import app.echo.android.design.EchoPageBackHandler
import app.echo.android.design.displayMetadataOrUnknown
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.EchoTrackMetadataUpdate
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun TrackMetadataEditorScreen(
    track: EchoTrack,
    loadLyrics: suspend (String) -> String?,
    onSave: suspend (EchoTrackMetadataUpdate) -> Boolean,
    onClose: () -> Unit,
    canPickArtwork: Boolean,
) {
    val editor = rememberTrackMetadataEditorState(track, loadLyrics)
    val saveState = retain(track.id) { TrackMetadataSaveState() }
    val saving = saveState.saving
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val pickArtwork = if (canPickArtwork) rememberTrackMetadataArtworkPicker(editor) else null
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(saveState.completed) {
        if (saveState.completed) {
            onClose()
            saveState.consumeCompletion()
        }
    }

    fun goBack() {
        if (saving || saveState.completed) return
        if (editor.hasChanges(track)) {
            focus.clearFocus()
            confirmDiscard = true
        } else {
            onClose()
        }
    }

    EchoPageBackHandler(onBack = ::goBack)
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.widthIn(max = 640.dp).fillMaxWidth()
                .padding(start = 8.dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = ::goBack, enabled = !saving) {
                EchoIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.feature_library_back_49093c))
            }
            Text(
                stringResource(R.string.feature_library_edit_tags_c8ec75),
                Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Button(
                enabled = editor.canSave && !saving,
                onClick = {
                    focus.clearFocus()
                    val update = editor.toUpdate(track)
                    scope.launch {
                        withContext(NonCancellable) { saveState.save { onSave(update) } }
                    }
                },
                modifier = Modifier.heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = LocalContentColor.current,
                            strokeWidth = 2.dp,
                        )
                    }
                    Text(
                        stringResource(
                            if (saving) R.string.feature_library_saving_9f2a14 else R.string.feature_library_save_68ae20,
                        ),
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
        Column(
            Modifier.weight(1f).widthIn(max = 640.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            TrackMetadataArtwork(track, editor.artworkUri, !saving, pickArtwork)
            TrackMetadataFields(editor, saving)
            Text(
                stringResource(R.string.feature_library_currently_saved_to_the_echoandroid_library_index_imported_e15a70),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.metadata_discard_title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onClose()
                }) { Text(stringResource(R.string.metadata_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) {
                    Text(stringResource(R.string.metadata_keep_editing))
                }
            },
        )
    }
}

@Composable
private fun TrackMetadataArtwork(
    track: EchoTrack,
    artworkUri: String?,
    enabled: Boolean,
    onPickArtwork: (() -> Unit)?,
) {
    val format = listOfNotNull(formatTrackMimeType(track.mimeType), track.sampleRateHz?.let(::formatTrackSampleRate))
        .joinToString("  ·  ")
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ArtworkTile(
                    artworkUri = artworkUri ?: track.artworkUri,
                    modifier = Modifier.size(104.dp),
                    accent = rememberLibraryArtworkAccent(),
                    cornerRadius = 12.dp,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (format.isNotEmpty()) {
                        Text(
                            format,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        displayMetadataOrUnknown(track.title, unknownTrackLabel()),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        displayMetadataOrUnknown(track.artist, unknownArtistLabel()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (onPickArtwork != null) {
                OutlinedButton(
                    onClick = onPickArtwork,
                    enabled = enabled,
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        EchoIcon(
                            Icons.Rounded.Image,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(stringResource(R.string.metadata_choose_artwork))
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackMetadataFields(editor: TrackMetadataEditorState, saving: Boolean) {
    val enabled = !saving
    MetadataSection(R.string.metadata_track_details) {
        MetadataField(
            R.string.feature_library_title_af1111, editor.title, { editor.title = it }, enabled,
            isError = editor.title.isBlank(),
        )
        MetadataField(
            R.string.feature_library_artist_37d883, editor.artist, { editor.artist = it }, enabled,
            isError = editor.artist.isBlank(),
        )
        MetadataField(R.string.metadata_composer, editor.composer, { editor.composer = it }, enabled)
    }
    MetadataSection(R.string.metadata_release) {
        MetadataField(R.string.feature_library_album_eb13be, editor.album, { editor.album = it }, enabled)
        MetadataField(R.string.feature_library_album_artist_6defa0, editor.albumArtist, { editor.albumArtist = it }, enabled)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetadataField(
                R.string.feature_library_track_000e4d, editor.trackNumber, { editor.trackNumber = it }, enabled,
                Modifier.weight(1f), keyboardType = KeyboardType.Number, isError = !editor.trackNumberValid,
            )
            MetadataField(
                R.string.feature_library_disc_3128c0, editor.discNumber, { editor.discNumber = it }, enabled,
                Modifier.weight(1f), keyboardType = KeyboardType.Number, isError = !editor.discNumberValid,
            )
            MetadataField(
                R.string.feature_library_year_fe373f, editor.year, { editor.year = it }, enabled,
                Modifier.weight(1f), keyboardType = KeyboardType.Number, isError = !editor.yearValid,
            )
        }
    }
    MetadataSection(R.string.metadata_lyrics) {
        val label = stringResource(R.string.metadata_lyrics)
        Box {
            OutlinedTextField(
                value = editor.lyrics,
                onValueChange = editor::editLyrics,
                enabled = enabled && !editor.lyricsLoading,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
                minLines = 4,
                maxLines = 8,
                shape = MaterialTheme.shapes.medium,
            )
            if (editor.lyricsLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center).size(20.dp), strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun MetadataSection(@StringRes title: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(title),
                Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        }
        content()
    }
}

@Composable
private fun MetadataField(
    @StringRes label: Int,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        isError = isError,
        label = { Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        shape = MaterialTheme.shapes.medium,
    )
}
