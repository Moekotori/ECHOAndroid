package app.echo.android.ui.library

import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import app.echo.android.feature.library.R as LibraryR

/** Exclusions only need a directory identity, so no persistent file access is requested. */
@Composable
internal fun rememberExcludedFolderPicker(onSelected: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val documentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
            val path = excludedFolderPath(uri.authority, documentId)
            if (path != null) onSelected(path)
            else Toast.makeText(context, LibraryR.string.scan_rules_folder_unsupported, Toast.LENGTH_LONG).show()
        }
    }
    return { picker.launch(null) }
}
