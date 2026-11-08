package app.echo.android.lyrics

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import app.echo.android.data.DocumentSiblingFiles
import app.echo.android.data.LibraryTrackEntity
import app.echo.android.model.lyrics.EchoLyrics
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import kotlinx.coroutines.CancellationException

/** One background load owns its two-directory snapshot; it is never retained across songs. */
internal class LocalLyricsSidecarReader(context: Context, private val checkActive: () -> Unit = {}) {
    private val resolver = context.contentResolver
    data class Snapshot(val files: Map<String, Uri>, val complete: Boolean)

    fun list(track: LibraryTrackEntity, audio: Uri): Snapshot = list(audio, track.relativePath)

    fun list(audio: Uri, libraryFolder: String?): Snapshot = when {
        audio.scheme == ContentResolver.SCHEME_FILE -> fileSnapshot(audio)
        audio.authority == MediaStore.AUTHORITY -> mediaStoreSnapshot(libraryFolder)
        else -> DocumentSiblingFiles.readLyrics(resolver, audio, EchoLyricsParser.fileExtensions, checkActive)?.let { listing ->
            Snapshot(listing.files.associate { it.name to it.uri }, listing.complete)
        } ?: Snapshot(emptyMap(), false)
    }

    fun load(snapshot: Snapshot, candidates: List<String>, adapt: (EchoLyrics) -> EchoLyrics?): EchoLyrics? {
        var reads = 0
        fun read(name: String?): EchoLyrics? {
            val uri = snapshot.files[name] ?: return null
            if (++reads > 12) return null
            return safely {
                checkActive()
                resolver.openInputStream(uri)?.use { input ->
                    val decoded = EchoLyricsTextDecoder.decodeResult(input.lyricsBytes())?.takeUnless { it.lossy } ?: return@use null
                    decoded.attach(EchoLyricsParser.parse(decoded.text, name))
                }
            }
        }
        for (candidate in LyricsSidecarDiscovery.discover(candidates, snapshot.files.keys, snapshot.complete).take(32)) {
            val primary = read(candidate.primary) ?: continue
            val translated = read(candidate.translation)
            val romanized = read(candidate.romanization)
            // Clip each source independently: title sidecars already use the movement timeline.
            val clipped = adapt(primary) ?: continue
            fun companion(value: EchoLyrics?): EchoLyrics? {
                if (value == null) return null
                val label = value.sourceLabel.orEmpty()
                val primaryLabel = primary.sourceLabel.orEmpty()
                val renamed = value.copy(sourceLabel = primaryLabel)
                return adapt(renamed)?.copy(sourceLabel = label)
            }
            val merged = LyricsSidecarDiscovery.merge(clipped, companion(translated), companion(romanized))
            if (merged.lines.any { it.text.isNotBlank() }) return merged
        }
        return null
    }

    private fun fileSnapshot(audio: Uri): Snapshot {
        val parent = audio.path?.let(::File)?.parentFile ?: return Snapshot(emptyMap(), false)
        val files = LinkedHashMap<String, Uri>()
        var complete = true
        for (folder in listOf("", "lyrics/")) {
            val directory = File(parent, folder)
            if (!directory.isDirectory) continue
            safely {
                Files.newDirectoryStream(directory.toPath()).use { entries ->
                    var visited = 0
                    for (path in entries) {
                        checkActive()
                        if (++visited > 4096) { complete = false; break }
                        val name = path.fileName.toString()
                        if (Files.isRegularFile(path) && EchoLyricsParser.fileExtensions.any { name.endsWith(it, true) })
                            files[folder + name] = Uri.fromFile(path.toFile())
                    }
                }
                true
            } ?: run { complete = false }
        }
        return Snapshot(files, complete)
    }

    private fun mediaStoreSnapshot(libraryFolder: String?): Snapshot {
        val location = LyricsSidecarLocation.fromLibraryFolder(libraryFolder) ?: return Snapshot(emptyMap(), false)
        val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val collection = MediaStore.Files.getContentUri(if (modern) location.volumeName else "external")
        @Suppress("DEPRECATION")
        val root = if (modern) "" else Environment.getExternalStorageDirectory().absolutePath
        val files = LinkedHashMap<String, Uri>()
        var complete = true
        for (folder in listOf("", "lyrics/")) {
            @Suppress("DEPRECATION")
            val pathColumn = if (modern) MediaStore.Files.FileColumns.RELATIVE_PATH else MediaStore.MediaColumns.DATA
            val projection = arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME, pathColumn)
            val selection = if (modern) "$pathColumn = ?" else "$pathColumn LIKE ?"
            val args = if (modern) arrayOf(location.relativePath + folder) else
                arrayOf(root.trimEnd('/') + "/" + libraryFolder.orEmpty().trim('/') + "/" + folder + "%")
            safely {
                resolver.query(collection, projection, selection, args, null)?.use { cursor ->
                    var visited = 0
                    while (cursor.moveToNext()) {
                        checkActive()
                        if (++visited > 4096) { complete = false; break }
                        if (!modern) {
                            val expected = File(root, libraryFolder.orEmpty().trim('/') + "/" + folder).path
                            if (File(cursor.getString(2).orEmpty()).parent != expected.trimEnd('/')) continue
                        }
                        val name = cursor.getString(1) ?: continue
                        if (EchoLyricsParser.fileExtensions.any { name.endsWith(it, true) })
                            files[folder + name] = Uri.withAppendedPath(collection, cursor.getLong(0).toString())
                    }
                    true
                }
            } ?: run { complete = false }
        }
        return Snapshot(files, complete)
    }

    private fun InputStream.lyricsBytes(): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            checkActive()
            val count = read(buffer)
            if (count < 0) break
            require(count > 0 && output.size() + count <= 2 * 1024 * 1024) { "Lyrics file exceeds read limit" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun <T> safely(read: () -> T): T? = try { read() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
}
