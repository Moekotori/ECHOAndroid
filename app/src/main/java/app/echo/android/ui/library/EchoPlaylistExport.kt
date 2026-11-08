package app.echo.android.ui.library

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import app.echo.android.EchoAndroidViewModel
import app.echo.android.feature.library.PlaylistExportDialog
import app.echo.android.model.library.EchoPlaylist
import app.echo.android.model.library.PlaylistExportFormat

@Composable
internal fun rememberPlaylistExport(viewModel: EchoAndroidViewModel): (EchoPlaylist) -> Unit {
    var chosenId by rememberSaveable { mutableStateOf<String?>(null) }
    var chosenName by rememberSaveable { mutableStateOf("") }
    var pendingId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingFormat by rememberSaveable { mutableStateOf(PlaylistExportFormat.M3u.name) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = pendingId; pendingId = null
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && id != null && uri != null) {
            val format = PlaylistExportFormat.entries.firstOrNull { it.name == pendingFormat } ?: PlaylistExportFormat.M3u
            viewModel.playlistTools.export(id, uri, format)
        }
    }
    if (chosenId != null) PlaylistExportDialog(chosenName, onChoose = { format ->
        pendingId = chosenId; pendingFormat = format.name; chosenId = null
        val name = chosenName.trim().ifBlank { "playlist" }.replace(Regex("[\\\\/:*?\"<>|]"), "-").take(120)
        launcher.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType(format.mimeType).putExtra(Intent.EXTRA_TITLE, "$name.${format.extension}"))
    }, onDismiss = { chosenId = null })
    return { playlist -> chosenId = playlist.id; chosenName = playlist.name }
}
