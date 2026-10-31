package app.echo.android.feature.library

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.library.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun AlbumImagesDialog(track: EchoTrack, tools: LibraryMediaTools, onDismiss: () -> Unit) {
    var chosen by remember(track.id) { mutableStateOf<String?>(null) }
    var images by remember(track.id) { mutableStateOf<List<EchoAlbumImage>?>(null) }
    var index by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    val choose = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { chosen = it.toString() }
    }
    LaunchedEffect(track.id, chosen, reload) {
        images = null; failed = false; index = 0
        try { images = tools.images(track, chosen) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.album_images_title)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val image = images?.getOrNull(index)
            if (image != null) {
                val label = stringResource(when (image.kind) { 3 -> R.string.album_images_front; 4 -> R.string.album_images_back
                    6 -> R.string.album_images_disc; else -> R.string.album_images_page })
                Text("${index + 1} / ${images!!.size} · ${image.name ?: label}")
                AlbumGalleryImage(image.uri, label)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(enabled = index > 0, onClick = { index-- }) { Text(stringResource(R.string.album_images_previous)) }
                    TextButton(enabled = index + 1 < images!!.size, onClick = { index++ }) { Text(stringResource(R.string.album_images_next)) }
                }
            } else if (images == null && !failed) LinearProgressIndicator(Modifier.fillMaxWidth())
            else Text(stringResource(if (failed) R.string.album_images_failed else R.string.album_images_empty))
            Text(stringResource(R.string.album_images_note), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { choose.launch(null) }) { Text(stringResource(R.string.album_images_choose_folder)) }
            if (chosen != null) TextButton(onClick = { chosen = null }) { Text(stringResource(R.string.album_images_embedded)) }
            if (failed) TextButton(onClick = { reload++ }) { Text(stringResource(R.string.media_tools_retry)) }
        } }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.media_tools_close)) } })
}

@Composable
private fun AlbumGalleryImage(uri: String, description: String) {
    val resolver = LocalContext.current.applicationContext.contentResolver
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(uri) { mutableStateOf(false) }
    var retry by remember(uri) { mutableIntStateOf(0) }
    var zoom by remember(uri) { mutableFloatStateOf(1f) }
    var pan by remember(uri) { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val transform = rememberTransformableState { scale, delta, _ ->
        zoom = (zoom * scale).coerceIn(1f, 4f)
        val x = size.width * (zoom - 1f) / 2
        val y = size.height * (zoom - 1f) / 2
        pan = Offset((pan.x + delta.x).coerceIn(-x, x), (pan.y + delta.y).coerceIn(-y, y))
    }
    LaunchedEffect(uri, retry) {
        failed = false
        try { bitmap = withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it, null, bounds) }
            require(bounds.outWidth in 1..12000 && bounds.outHeight in 1..12000 && bounds.outWidth.toLong() * bounds.outHeight <= 80_000_000)
            val options = BitmapFactory.Options().apply {
                inSampleSize = 1
                while (bounds.outWidth / inSampleSize > 2048 || bounds.outHeight / inSampleSize > 2048) inSampleSize *= 2
            }
            requireNotNull(resolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it, null, options) })
        } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    Box(Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 320.dp).onSizeChanged { size = it }.clipToBounds().transformable(transform)) {
        bitmap?.let { Image(it.asImageBitmap(), description, Modifier.fillMaxWidth().height(300.dp)
            .graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y }) }
        if (failed) Text(stringResource(R.string.album_images_failed))
        else if (bitmap == null) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    if (failed) TextButton(onClick = { retry++ }) { Text(stringResource(R.string.media_tools_retry)) }
    TextButton(onClick = { zoom = 1f; pan = Offset.Zero }) { Text(stringResource(R.string.album_images_fit)) }
}
