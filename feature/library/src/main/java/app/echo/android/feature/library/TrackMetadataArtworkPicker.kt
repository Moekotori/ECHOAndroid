package app.echo.android.feature.library

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** Selection edits only the local draft. The shared save operation owns file writes. */
@Composable
internal fun rememberTrackMetadataArtworkPicker(state: TrackMetadataEditorState): () -> Unit {
    val resolver = LocalContext.current.contentResolver
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                // Some providers supply a transient grant; it is still usable for this save.
            }
            state.editArtwork(uri.toString())
        }
    }
    return { picker.launch(arrayOf("image/*")) }
}
