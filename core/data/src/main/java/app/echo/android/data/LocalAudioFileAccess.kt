package app.echo.android.data

import android.content.Context
import android.net.Uri
import android.os.Binder
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/** Open as the library-owning app after the caller's outer URI grant has been checked. */
internal object LocalAudioFileAccess {
    fun open(context: Context, source: Uri, signal: CancellationSignal?): ParcelFileDescriptor {
        signal?.throwIfCanceled()
        val identity = Binder.clearCallingIdentity()
        try {
            return when (source.scheme) {
                "content" -> context.contentResolver.openFileDescriptor(source, "r", signal)
                "file" -> ParcelFileDescriptor.open(File(source.path ?: throw FileNotFoundException()), ParcelFileDescriptor.MODE_READ_ONLY)
                else -> null
            } ?: throw FileNotFoundException()
        } finally { Binder.restoreCallingIdentity(identity) }
    }
}
