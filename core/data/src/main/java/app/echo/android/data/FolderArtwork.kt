package app.echo.android.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.io.InputStream
import java.util.Locale

internal object FolderArtworkPolicy {
    val names = listOf("cover.jpg", "album.jpg", "folder.jpg", "cover.png", "album.png", "folder.png", "cover.jpeg", "folder.jpeg", "album.jpeg")
    fun sidecarNames(audioName: String): List<String> {
        val base = audioName.substringBeforeLast('.', audioName)
        if (base.isBlank() || base.any { it == '/' || it == '\\' }) return emptyList()
        return listOf("$base.jpg", "$base.jpeg", "$base.png")
    }
    fun <T> sidecars(audioName: String, images: Map<String, T>): List<T> =
        sidecarNames(audioName).mapNotNull { images[it.lowercase(Locale.ROOT)] }
    const val MaxBytes = 8 * 1024 * 1024
    fun rank(name: String) = names.indexOf(name.lowercase(Locale.ROOT)).takeIf { it >= 0 } ?: Int.MAX_VALUE
    fun read(input: InputStream): ByteArray? {
        val bytes = input.takeBounded(MaxBytes + 1)
        if (bytes.size !in 8..MaxBytes) return null
        val png = bytes.take(8) == listOf(0x89.toByte(), 0x50.toByte(), 0x4e.toByte(), 0x47.toByte(), 13.toByte(), 10.toByte(), 26.toByte(), 10.toByte())
        val jpeg = bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte()
        return bytes.takeIf { png || jpeg }
    }
    private fun InputStream.takeBounded(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (output.size() < limit) {
            val read = read(buffer, 0, minOf(buffer.size, limit - output.size()))
            if (read < 0) break
            if (read == 0) break
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}

/** Bounded directory indexes reused across songs. No wider storage permission is requested. */
internal class FolderArtworkResolver(private val resolver: ContentResolver, private val store: (ByteArray?) -> String?) {
    private val cache = object : LinkedHashMap<String, String?>(256, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String?>?) = size > 256
    }
    private val directories = object : LinkedHashMap<String, Map<String, File>>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Map<String, File>>?) = size > 16
    }
    fun documents(tree: Uri, parentId: String, children: List<DocumentTreeCachedChild>): String? = cached("$tree|$parentId") {
        children.asSequence().filter { FolderArtworkPolicy.rank(it.name) != Int.MAX_VALUE && it.sizeBytes <= FolderArtworkPolicy.MaxBytes }.take(32)
            .sortedBy { FolderArtworkPolicy.rank(it.name) }.firstNotNullOfOrNull { child -> readDocument(tree, child) }
    }
    /** Index existing listing once, then only open matching pictures. Missing sidecars cost no I/O. */
    fun documentSidecars(tree: Uri, children: List<DocumentTreeCachedChild>, audioNames: List<String>): Map<String, String> {
        val wanted = audioNames.flatMap(FolderArtworkPolicy::sidecarNames).mapTo(HashSet()) { it.lowercase(Locale.ROOT) }
        val images = children.asSequence().filter { it.name.lowercase(Locale.ROOT) in wanted && it.sizeBytes <= FolderArtworkPolicy.MaxBytes }
            .associateBy { it.name.lowercase(Locale.ROOT) }
        return buildMap {
            audioNames.forEach { name ->
                val uri = FolderArtworkPolicy.sidecars(name, images).firstNotNullOfOrNull { child ->
                    cached("$tree|${child.documentId}") { readDocument(tree, child) }
                }
                if (uri != null) put(name, uri)
            }
        }
    }
    private fun readDocument(tree: Uri, child: DocumentTreeCachedChild): String? =
        runCatching { resolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, child.documentId))
            ?.use(FolderArtworkPolicy::read)?.let(store) }.getOrNull()

    fun primaryDirectory(relative: String?, audioName: String? = null): String? {
        if (relative.isNullOrBlank()) return null
        val root = runCatching { android.os.Environment.getExternalStorageDirectory().canonicalFile }.getOrNull() ?: return null
        val folder = runCatching { File(root, relative).canonicalFile }.getOrNull() ?: return null
        if (folder != root && !folder.path.startsWith(root.path + File.separator)) return null
        val images = directories.getOrPut(folder.path) {
            runCatching {
                // DirectoryStream avoids materializing an unbounded File[] for a large folder.
                java.nio.file.Files.newDirectoryStream(folder.toPath()).use { entries ->
                    val result = LinkedHashMap<String, File>()
                    for (entry in entries) {
                        val file = entry.toFile()
                        val name = file.name.lowercase(Locale.ROOT)
                        if (name.substringAfterLast('.') !in setOf("jpg", "jpeg", "png")) continue
                        if (!file.isFile || file.length() > FolderArtworkPolicy.MaxBytes) continue
                        // Do not follow a picture symlink out of the granted primary directory.
                        val canonical = file.canonicalFile
                        if (canonical.parentFile != folder) continue
                        result.putIfAbsent(name, canonical)
                        if (result.size >= 2048) break
                    }
                    result
                }
            }.getOrDefault(emptyMap())
        }
        if (audioName != null) {
            FolderArtworkPolicy.sidecars(audioName, images).firstNotNullOfOrNull { file -> readFile(file) }?.let { return it }
        }
        return cached(folder.path) {
            images.values.asSequence().filter { FolderArtworkPolicy.rank(it.name) != Int.MAX_VALUE }
                .sortedBy { FolderArtworkPolicy.rank(it.name) }.firstNotNullOfOrNull(::readFile)
        }
    }
    private fun readFile(file: File): String? = cached(file.path) {
        runCatching { file.inputStream().use(FolderArtworkPolicy::read)?.let(store) }.getOrNull()
    }
    private fun cached(key: String, load: () -> String?): String? {
        if (cache.containsKey(key)) return cache[key]
        return load().also { cache[key] = it }
    }
}
