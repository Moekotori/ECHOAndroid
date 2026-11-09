package app.echo.android.ui.library

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.echo.android.PlaylistManagementController
import app.echo.android.model.library.EchoPlaylist
import app.echo.android.model.library.PlaylistExportFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import app.echo.android.feature.library.R as LibraryR

class EchoPlaylistShareFileProvider : FileProvider()

@Composable
internal fun rememberPlaylistShare(tools: PlaylistManagementController): (EchoPlaylist) -> Unit {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    return { playlist ->
        if (job?.isActive != true) job = scope.launch {
            try {
                val format = PlaylistExportFormat.Text
                val text = tools.textExport(playlist.id, format)
                check(text.isNotBlank())
                val uri = withContext(Dispatchers.IO) {
                    val directory = File(context.cacheDir, "playlist-share")
                    check(directory.isDirectory || directory.mkdirs())
                    directory.listFiles()?.sortedByDescending(File::lastModified)?.drop(7)?.forEach(File::delete)
                    val file = File.createTempFile("playlist-", ".txt", directory)
                    file.writeText(text)
                    FileProvider.getUriForFile(context, "${context.packageName}.playlist-share", file,
                        playlistDocumentName(playlist.name, format))
                }
                check(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                val send = Intent(Intent.ACTION_SEND).setType(format.mimeType)
                    .putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_SUBJECT, playlist.name)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                send.clipData = ClipData.newUri(context.contentResolver, playlist.name, uri)
                context.startActivity(Intent.createChooser(send, context.getString(LibraryR.string.playlist_share)))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Toast.makeText(context, LibraryR.string.playlist_share_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
