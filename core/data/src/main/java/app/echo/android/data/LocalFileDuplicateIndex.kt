package app.echo.android.data

import app.echo.android.model.library.CueSheetPolicy
import java.text.Normalizer

/**
 * 同一目录加同一文件名就是同一个文件。mtime 不参与同一性:
 * SAF 和 MediaStore 的修改时间经常差一秒、差时区，甚至一方为 0。
 * 多条曲库行指向这个路径时，留下最稳定的 id（音频表优先于 Files 回退和 SAF），
 * 其余交给 mergeScanDuplicate。大小和修改时间只说明内容变过，不拆成两首歌。
 */
class LocalFileDuplicateIndex<T> private constructor(
    private val groups: Map<LocalFileIdentity, List<LocalFileDuplicateCandidate<T>>>,
) {
    fun isEmpty(): Boolean = groups.isEmpty()

    fun find(
        relativePath: String?,
        @Suppress("UNUSED_PARAMETER") sizeBytes: Long,
        @Suppress("UNUSED_PARAMETER") dateModifiedSeconds: Long,
        displayName: String?,
    ): T? {
        val identity = LibraryScanPolicy.localFileIdentity(relativePath, displayName) ?: return null
        return choose(groups[identity] ?: return null)?.value
    }

    /** 与 [matchedId] 处于同一路径的其他曲库行，不含它自己。 */
    fun otherCopies(matchedId: String): List<T> {
        val group = groups.values.firstOrNull { candidates -> candidates.any { it.id == matchedId } } ?: return emptyList()
        return group.filter { it.id != matchedId }.map { it.value }
    }

    /** 每个有重复行的文件：应保留的记录，以及要合并进去的其余记录。 */
    fun duplicateGroups(): List<Pair<T, List<T>>> =
        groups.values.mapNotNull { group ->
            if (group.size < 2) return@mapNotNull null
            val keeper = choose(group) ?: return@mapNotNull null
            val copies = group.filter { it.id != keeper.id }.map { it.value }
            if (copies.isEmpty()) null else keeper.value to copies
        }

    private fun choose(
        group: List<LocalFileDuplicateCandidate<T>>,
    ): LocalFileDuplicateCandidate<T>? {
        if (group.isEmpty()) return null
        if (group.size == 1) return group.first()
        return group.minWithOrNull(compareBy<LocalFileDuplicateCandidate<T>> { duplicateIdRank(it.id) }.thenBy { it.id })
    }

    companion object {
        fun <T> empty(): LocalFileDuplicateIndex<T> = LocalFileDuplicateIndex(emptyMap())

        fun <T> builder(idOf: (T) -> String): Builder<T> = Builder(idOf)

        fun <T> fromExactKeys(keys: Map<String, T>, idOf: (T) -> String): LocalFileDuplicateIndex<T> {
            val builder = Builder(idOf)
            keys.forEach { (key, value) ->
                val parts = parseLocalFileDuplicateKey(key) ?: return@forEach
                builder.add(parts.directory, parts.sizeBytes, parts.dateModifiedSeconds, parts.name, value)
            }
            return builder.build()
        }
    }

    class Builder<T>(private val idOf: (T) -> String) {
        private val groups = LinkedHashMap<LocalFileIdentity, MutableList<LocalFileDuplicateCandidate<T>>>()

        fun add(
            relativePath: String?,
            sizeBytes: Long,
            dateModifiedSeconds: Long,
            displayName: String?,
            value: T,
        ) {
            val identity = LibraryScanPolicy.localFileIdentity(relativePath, displayName) ?: return
            val id = idOf(value)
            if (id.isBlank()) return
            val bucket = groups.getOrPut(identity) { mutableListOf() }
            bucket.removeAll { it.id == id }
            bucket += LocalFileDuplicateCandidate(
                id = id,
                sizeBytes = sizeBytes.coerceAtLeast(0L),
                dateModifiedSeconds = dateModifiedSeconds.coerceAtLeast(0L),
                value = value,
            )
        }

        fun build(): LocalFileDuplicateIndex<T> = LocalFileDuplicateIndex(groups.mapValues { it.value.toList() })
    }
}

internal data class LocalFileIdentity(
    val directory: String,
    val name: String,
)

private data class LocalFileDuplicateCandidate<T>(
    val id: String,
    val sizeBytes: Long,
    val dateModifiedSeconds: Long,
    val value: T,
)

private data class ParsedDuplicateKey(
    val directory: String,
    val name: String,
    val sizeBytes: Long,
    val dateModifiedSeconds: Long,
)

/** 音频表整文件优先于其分轨、Files 回退行和 SAF 行。 */
internal fun duplicateIdRank(id: String): Int = when {
    LibraryScanPolicy.isMediaStoreAudioTrackId(id) && !CueSheetPolicy.isCueTrackId(id) -> 0
    LibraryScanPolicy.isMediaStoreAudioTrackId(id) -> 1
    LibraryScanPolicy.isMediaStoreFileTrackId(id) -> 2
    id.startsWith(LibraryScanPolicy.SafTrackIdPrefix) -> 3
    else -> 4
}

private fun parseLocalFileDuplicateKey(key: String): ParsedDuplicateKey? {
    val dirLenEnd = key.indexOf(':')
    if (dirLenEnd <= 0) return null
    val dirLen = key.substring(0, dirLenEnd).toIntOrNull() ?: return null
    val dirStart = dirLenEnd + 1
    val dirEnd = dirStart + dirLen
    if (dirEnd > key.length) return null
    val directory = key.substring(dirStart, dirEnd)
    val nameLenEnd = key.indexOf(':', dirEnd)
    if (nameLenEnd <= dirEnd) return null
    val nameLen = key.substring(dirEnd, nameLenEnd).toIntOrNull() ?: return null
    val nameStart = nameLenEnd + 1
    val nameEnd = nameStart + nameLen
    if (nameEnd >= key.length || key[nameEnd] != '|') return null
    val name = key.substring(nameStart, nameEnd)
    val rest = key.substring(nameEnd + 1)
    val separator = rest.indexOf('|')
    if (separator <= 0) return null
    val size = rest.substring(0, separator).toLongOrNull() ?: return null
    val modified = rest.substring(separator + 1).toLongOrNull() ?: return null
    return ParsedDuplicateKey(directory, name, size, modified)
}

internal fun normalizedDuplicateDirectory(relativePath: String?): String? {
    val dir = relativePath?.replace('\\', '/')?.trim('/')?.takeIf { it.isNotBlank() } ?: return null
    return Normalizer.normalize(dir, Normalizer.Form.NFC)
}

internal fun normalizedDuplicateName(displayName: String?): String? {
    val name = displayName?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return Normalizer.normalize(name, Normalizer.Form.NFC)
}
