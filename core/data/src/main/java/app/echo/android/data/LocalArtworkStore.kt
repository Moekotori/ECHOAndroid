package app.echo.android.data

import java.io.File
import java.security.MessageDigest

/** Shared embedded covers, bounded on disk; only this directory is managed here. */
internal class LocalArtworkStore(
    private val directory: File,
    private val maxBytes: Long = 128L * 1024 * 1024,
    private val maxImageBytes: Int = 8 * 1024 * 1024,
) {
    private var storedBytes: Long? = null

    @Synchronized
    fun store(bytes: ByteArray?): String? {
        if (bytes == null || bytes.isEmpty() || bytes.size > maxImageBytes) return null
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            val target = File(directory, "$digest.img")
            if (target.isFile) return target.toURI().toASCIIString()
            val used = storedBytes ?: directory.listFiles().orEmpty().sumOf { it.length() }
                .also { storedBytes = it }
            if (bytes.size.toLong() > maxBytes - used) return null
            if (!directory.isDirectory && !directory.mkdirs()) return null
            val temporary = File.createTempFile("cover-", ".tmp", directory)
            try {
                temporary.writeBytes(bytes)
                if (!temporary.renameTo(target)) return null
                storedBytes = used + bytes.size
                target.toURI().toASCIIString()
            } finally {
                temporary.delete()
            }
        }.getOrNull()
    }

    @Synchronized
    fun prune(referencedUris: Set<String>) {
        var retainedBytes = 0L
        directory.listFiles().orEmpty().forEach { file ->
            if (file.toURI().toASCIIString() !in referencedUris) file.delete()
            if (file.isFile) retainedBytes += file.length()
        }
        storedBytes = retainedBytes
    }

    fun exists(): Boolean = directory.isDirectory
}
