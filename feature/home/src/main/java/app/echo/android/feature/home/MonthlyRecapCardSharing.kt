package app.echo.android.feature.home

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

internal suspend fun prepareMonthlyRecapFile(context: Context, bitmap: Bitmap): File = withContext(Dispatchers.IO) {
    val directory = File(context.cacheDir, "monthly-recap-cards").apply { check(mkdirs() || isDirectory) }
    val files = directory.listFiles().orEmpty().sortedByDescending(File::lastModified)
    files.drop(5).forEach(File::delete)
    val file = File(directory, "echo-recap-${UUID.randomUUID()}.png")
    file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    file
}

internal suspend fun monthlyRecapShareIntent(context: Context, bitmap: Bitmap): Intent = withContext(Dispatchers.IO) {
    val file = prepareMonthlyRecapFile(context, bitmap)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.monthly-recap-cards", file)
    Intent(Intent.ACTION_SEND).apply {
        type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri("ECHO", uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

internal suspend fun saveMonthlyRecapImage(context: Context, file: File, uri: Uri) = withContext(Dispatchers.IO) {
    val directory = File(context.cacheDir, "monthly-recap-cards").canonicalFile
    require(file.canonicalFile.parentFile == directory)
    checkNotNull(context.contentResolver.openOutputStream(uri)).use { output -> file.inputStream().use { it.copyTo(output) } }
}
