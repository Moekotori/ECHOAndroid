package app.echo.android.data

import android.content.Context
import android.util.AtomicFile
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Remove only files owned by the retired music download feature. */
suspend fun cleanupLegacyOfflineMusic(context: Context) = withContext(Dispatchers.IO) {
    File(context.filesDir, "offline").deleteRecursively()
    AtomicFile(File(context.filesDir, "remote-pins.json")).delete()
}
