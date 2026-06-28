package app.echo.android

import android.graphics.Typeface
import androidx.core.net.toUri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberImportedFontFamily(fontUri: String?): FontFamily? {
    val context = LocalContext.current
    val fontFamily by produceState<FontFamily?>(null, fontUri) {
        value = withContext(Dispatchers.IO) {
            if (fontUri.isNullOrBlank()) {
                null
            } else {
                runCatching {
                    context.contentResolver.openFileDescriptor(fontUri.toUri(), "r")?.use { descriptor ->
                        FontFamily(Typeface.Builder(descriptor.fileDescriptor).build())
                    }
                }.getOrNull()
            }
        }
    }
    return fontFamily
}
