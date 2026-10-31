package app.echo.android.data

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.graphics.BitmapFactory
import app.echo.android.model.library.EchoAlbumImage
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.CueSheetPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** Gallery-only work. The scanner and list cover retain their one-picture behavior. */
class AlbumImagesRepository(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val directory = File(context.cacheDir, "album-image-gallery")
    private val mutex = Mutex()

    suspend fun load(track: EchoTrack, chosenTree: String? = null): List<EchoAlbumImage> = withContext(Dispatchers.IO) { mutex.withLock {
        require(track.source.isLocalAudioFile)
        directory.mkdirs()
        prune()
        val result = ArrayList<EchoAlbumImage>()
        var retained = 0L
        fun add(bytes: ByteArray, kind: Int, name: String? = null) {
            if (result.size >= 16 || bytes.size > 8 * 1024 * 1024 || retained + bytes.size > 24L * 1024 * 1024) return
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth !in 1..12000 || bounds.outHeight !in 1..12000 ||
                bounds.outWidth.toLong() * bounds.outHeight > 80_000_000) return
            val id = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            if (result.any { it.id == id }) return
            val file = File(directory, "$id.image")
            file.writeBytes(bytes)
            file.setLastModified(System.currentTimeMillis())
            retained += bytes.size
            result += EchoAlbumImage(id, file.toURI().toString(), kind, name)
        }
        val uri = Uri.parse(CueSheetPolicy.playbackUri(track.uri))
        if (chosenTree == null) resolver.openInputStream(uri)?.use { input ->
            val header = input.tagBytes(4) ?: return@use
            if (header.copyOfRange(0, 3).contentEquals("ID3".toByteArray())) {
                val rest = input.tagBytes(6) ?: return@use
                val tag = Id3TagReader.read(header + rest, input, maxPictures = 8) ?: return@use
                for (frame in tag.frames) {
                    coroutineContext.ensureActive()
                    if (frame.id !in listOf("APIC", "PIC")) continue
                    val payload = AudioFileTagRewriter.readableId3Payload(frame, tag.version) ?: continue
                    EmbeddedTagArtwork.id3(payload, frame.id == "PIC")?.let { add(it.bytes, it.type) }
                }
            } else if (header.contentEquals("fLaC".toByteArray())) {
                var traversed = 0L
                repeat(128) {
                    coroutineContext.ensureActive()
                    val block = input.tagBytes(4) ?: return@use
                    val length = ((block[1].toInt() and 255) shl 16) or ((block[2].toInt() and 255) shl 8) or (block[3].toInt() and 255)
                    traversed += length + 4L
                    if (traversed > 64L * 1024 * 1024) return@use
                    if (block[0].toInt() and 127 == 6 && length <= 8 * 1024 * 1024) {
                        EmbeddedTagArtwork.readFlac(input, length)?.let { add(it.bytes, it.type) }
                    } else if (!input.skipTagBytes(length.toLong())) return@use
                    if (block[0].toInt() and 128 != 0) return@use
                }
            }
        }
        val siblings = if (chosenTree == null) DocumentSiblingFiles.read(resolver, uri, listOf(".jpg", ".jpeg", ".png", ".webp"))
            ?.files?.take(24)?.map { it.name to it.uri }.orEmpty() else children(Uri.parse(chosenTree))
        for ((name, image) in siblings) {
            coroutineContext.ensureActive()
            if (result.size >= 16) break
            resolver.openInputStream(image)?.use { source -> readBoundedImage(source) }?.let { bytes ->
                val kind = when { name.contains("front", true) || name.startsWith("cover", true) -> 3
                    name.contains("back", true) -> 4; name.contains("disc", true) || name.contains("cd", true) -> 6; else -> 5 }
                add(bytes, kind, name)
            }
        }
        if (result.isEmpty() && chosenTree == null) track.artworkUri?.let { result += EchoAlbumImage("cover", it, 3) }
        prune()
        result.sortedWith(compareBy<EchoAlbumImage> { when (it.kind) { 3 -> 0; 4 -> 1; 6 -> 2; else -> 3 } }.thenBy { it.name.orEmpty() })
    } }

    private fun children(tree: Uri): List<Pair<String, Uri>> {
        val parent = DocumentsContract.getTreeDocumentId(tree)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
        return buildList {
            resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
                var visited = 0
                while (cursor.moveToNext() && ++visited <= 4096 && size < 24) {
                    val name = cursor.getString(1) ?: continue
                    if (name.substringAfterLast('.').lowercase() in listOf("jpg", "jpeg", "png", "webp"))
                        add(name to DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0)))
                }
            }
        }
    }

    private fun readBoundedImage(source: InputStream): ByteArray? {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = source.read(buffer)
            if (count < 0) break
            if (output.size() + count > 8 * 1024 * 1024) return null
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun prune() {
        val files = directory.listFiles().orEmpty().sortedByDescending(File::lastModified)
        var size = 0L
        files.forEachIndexed { index, file ->
            size += file.length()
            if (index >= 32 || size > 32L * 1024 * 1024) file.delete()
        }
    }
}
