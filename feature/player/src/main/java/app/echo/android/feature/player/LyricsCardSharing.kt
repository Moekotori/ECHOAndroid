package app.echo.android.feature.player

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

internal suspend fun lyricsCardShareIntent(context: Context, bitmap: Bitmap): Intent = withContext(Dispatchers.IO) {
    val directory = File(context.cacheDir, "lyrics-cards").apply { mkdirs() }
    val existing = directory.listFiles().orEmpty().sortedByDescending { it.lastModified() }
    existing.drop(7).forEach { it.delete() }
    val file = File(directory, "echo-${UUID.randomUUID()}.png")
    file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.lyrics-cards", file)
    Intent(Intent.ACTION_SEND).apply {
        type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri("ECHO", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
