package app.echo.android.data

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal enum class EmbeddedTagReplaceStatus {
    Replaced,
    Restored,
    BackupKept,
}

internal object EmbeddedTagFileReplace {
    fun replace(target: File, source: File) {
        require(source.isFile) { "missing replacement ${source.path}" }
        val parent = target.parentFile ?: error("missing parent for ${target.path}")
        if (!parent.exists() && !parent.mkdirs()) {
            error("unable to create ${parent.path}")
        }
        val tmp = File(parent, ".${target.name}.echo-tmp")
        try {
            FileInputStream(source).use { input ->
                FileOutputStream(tmp).use { output ->
                    input.copyTo(output, COPY_BUFFER)
                    output.flush()
                    output.fd.sync()
                }
            }
            try {
                Files.move(
                    tmp.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    tmp.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } finally {
            tmp.delete()
        }
    }

    private const val COPY_BUFFER = 64 * 1024
}

internal object EmbeddedTagBackup {
    fun preserveOriginal(original: File, cacheDir: File, trackKey: String): File {
        val dest = File(cacheDir, "backup-$trackKey")
        if (dest.exists() && dest.length() > 0L) {
            original.delete()
            return dest
        }
        dest.delete()
        if (!original.renameTo(dest)) {
            original.copyTo(dest, overwrite = true)
            original.delete()
        }
        return dest
    }
}
