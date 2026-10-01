package app.echo.android.feature.player

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.lyrics.EchoLyrics
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LyricsCardSheet(track: EchoTrack, lyrics: EchoLyrics, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by remember(track.id, lyrics.lines) { mutableStateOf<Set<Int>>(emptySet()) }
    var style by remember { mutableStateOf(LyricsCardStyle.Artwork) }
    var artwork by remember(track.id) { mutableStateOf<Bitmap?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var rendering by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var exportBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    val selectedLines = remember(selected, lyrics) { selected.sorted().mapNotNull { lyrics.lines.getOrNull(it)?.text } }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val bitmap = exportBitmap; exportBitmap = null
        if (uri == null || bitmap == null) { exporting = false }
        else scope.launch {
            try {
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use {
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                } ?: error("Could not open destination") }
                saved = true
            } catch (e: CancellationException) { throw e } catch (_: Exception) { error = true }
            finally { exporting = false }
        }
    }
    LaunchedEffect(track.artworkUri) {
        if (track.artworkUri != null) try {
            val request = ImageRequest.Builder(context).data(track.artworkUri).size(512).allowHardware(false).build()
            val result = context.imageLoader.execute(request)
            artwork = (result as? SuccessResult)?.drawable?.toBitmap()
        } catch (e: CancellationException) { throw e } catch (_: Exception) { /* A card works without artwork. */ }
    }
    LaunchedEffect(selectedLines, style, artwork) {
        preview = null; saved = false
        if (selectedLines.isEmpty()) return@LaunchedEffect
        rendering = true
        delay(150)
        try { preview = withContext(Dispatchers.Default) { LyricsCardRenderer.render(track, selectedLines, artwork, style) } }
        catch (e: CancellationException) { throw e } catch (_: Exception) { error = true }
        finally { rendering = false }
    }
    ModalBottomSheet(onDismissRequest = { if (!exporting) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(stringResource(R.string.lyrics_card_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.lyrics_card_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LyricsCardStyle.entries.forEach { item -> FilterChip(style == item, { style = item }, enabled = !exporting,
                        label = { Text(stringResource(when (item) {
                            LyricsCardStyle.Paper -> R.string.lyrics_card_paper
                            LyricsCardStyle.Midnight -> R.string.lyrics_card_midnight
                            LyricsCardStyle.Artwork -> R.string.lyrics_card_artwork
                        })) }) }
                }
            }
            item {
                preview?.let { bitmap -> Image(bitmap.asImageBitmap(), stringResource(R.string.lyrics_card_preview),
                    Modifier.fillMaxWidth().heightIn(max = 330.dp)) }
                if (rendering) LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { val bitmap = preview ?: return@Button; exporting = true; scope.launch {
                        try { context.startActivity(android.content.Intent.createChooser(lyricsCardShareIntent(context, bitmap), null)) }
                        catch (e: CancellationException) { throw e } catch (_: Exception) { error = true }
                        finally { exporting = false }
                    } }, enabled = preview != null && !rendering && !exporting) { Text(stringResource(R.string.lyrics_card_share)) }
                    OutlinedButton(onClick = { exportBitmap = preview; exporting = true; saveLauncher.launch("echo-lyrics.png") },
                        enabled = preview != null && !rendering && !exporting) { Text(stringResource(R.string.lyrics_card_save)) }
                }
                if (saved) Text(stringResource(R.string.lyrics_card_saved), color = MaterialTheme.colorScheme.primary)
                if (error) Text(stringResource(R.string.track_tools_error), color = MaterialTheme.colorScheme.error)
                HorizontalDivider(Modifier.padding(top = 12.dp))
            }
            itemsIndexed(lyrics.lines, key = { index, _ -> index }) { index, line ->
                if (line.text.isNotBlank()) Row(Modifier.fillMaxWidth().clickable(enabled = !exporting) {
                    selected = if (index in selected) selected - index
                    else if (selected.size < 6 && selectedLines.sumOf { it.length } + line.text.length <= 1000) selected + index else selected
                }.padding(vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(index in selected, onCheckedChange = null)
                    Text(line.text, Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
