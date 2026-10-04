package app.echo.android.data

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest

/** Remote embedded lyrics are kept locally so opening a player needs no tag rescan. */
internal class EmbeddedLyricsStore(private val directory: File) {
    fun read(track: LibraryTrackEntity): String? = runCatching {
        val file = target(track)
        if (!file.isFile || file.length() > MaxTextBytes + 16) return@runCatching null
        DataInputStream(file.inputStream()).use { input ->
            if (input.readLong() != track.sizeBytes || input.readLong() != track.dateModifiedSeconds) return@use null
            val text = input.readBytes().toString(Charsets.UTF_8).takeIf { it.isNotBlank() }
            if (text != null) file.setLastModified(System.currentTimeMillis())
            text
        }
    }.getOrNull()

    @Synchronized
    fun store(track: LibraryTrackEntity, text: String?) {
        runCatching {
            val target = target(track)
            val bytes = text?.takeIf { it.isNotBlank() }?.toByteArray(Charsets.UTF_8)
            if (bytes == null || bytes.size > MaxTextBytes) { target.delete(); return@runCatching }
            if (!directory.isDirectory && !directory.mkdirs()) return@runCatching
            val files = directory.listFiles().orEmpty().filter { it.isFile && it != target }.sortedBy { it.lastModified() }
            var used = files.sumOf { it.length() }
            for (file in files) {
                if (used + bytes.size + 16 <= MaxStoreBytes) break
                val size = file.length()
                if (file.delete()) used -= size
            }
            if (used + bytes.size + 16 > MaxStoreBytes) return@runCatching
            val temporary = File.createTempFile("lyrics-", ".tmp", directory)
            try {
                DataOutputStream(temporary.outputStream()).use { output ->
                    output.writeLong(track.sizeBytes)
                    output.writeLong(track.dateModifiedSeconds)
                    output.write(bytes)
                }
                if (!temporary.renameTo(target)) {
                    target.delete()
                    temporary.renameTo(target)
                }
            } finally { temporary.delete() }
        }
    }

    private fun target(track: LibraryTrackEntity): File {
        val key = MessageDigest.getInstance("SHA-256").digest(track.contentUri.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(directory, "$key.lyrics")
    }
    private companion object {
        const val MaxTextBytes = 512 * 1024
        const val MaxStoreBytes = 16 * 1024 * 1024
    }
}
