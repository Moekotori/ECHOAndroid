package app.echo.android.smb

import com.hierynomus.msfscc.FileAttributes

/** 共享里的一个文件。[path] 是共享内的完整路径，用 `/` 分隔。 */
data class EchoSmbFileEntry(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val modifiedEpochMs: Long,
)

data class EchoSmbScanResult(
    val fileCount: Int,
    /** 触到目录或文件数上限，结果不完整；此时不要据此删除库里已有的曲目。 */
    val hitLimit: Boolean,
)

/** 广度优先遍历共享目录。跳过隐藏项和 `.`、`..`，按 [accept] 过滤文件。 */
class EchoSmbScanner(
    private val maxFolders: Int = 2_000,
    private val maxFiles: Int = 20_000,
) {
    suspend fun scan(
        endpoint: EchoSmbEndpoint,
        accept: (name: String) -> Boolean,
        onFile: suspend (EchoSmbFileEntry) -> Unit,
    ): EchoSmbScanResult {
        val share = EchoSmbConnections.share(endpoint)
        val queue = ArrayDeque<String>()
        queue += endpoint.normalizedBasePath
        var folders = 0
        var files = 0
        var hitLimit = false
        try {
            while (queue.isNotEmpty()) {
                if (folders >= maxFolders || files >= maxFiles) {
                    hitLimit = true
                    break
                }
                val folder = queue.removeFirst()
                folders += 1
                val listing = share.list(folder.replace('/', '\\'))
                for (info in listing) {
                    val name = info.fileName
                    if (name == "." || name == ".." || name.startsWith(".")) continue
                    val attributes = info.fileAttributes
                    if (attributes and FileAttributes.FILE_ATTRIBUTE_HIDDEN.value != 0L) continue
                    val child = if (folder.isEmpty()) name else "$folder/$name"
                    if (attributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L) {
                        queue += child
                    } else if (accept(name)) {
                        onFile(
                            EchoSmbFileEntry(
                                path = child,
                                name = name,
                                sizeBytes = info.endOfFile,
                                modifiedEpochMs = info.lastWriteTime.toEpochMillis(),
                            ),
                        )
                        files += 1
                        if (files >= maxFiles) {
                            hitLimit = true
                            break
                        }
                    }
                }
            }
        } catch (error: Exception) {
            EchoSmbConnections.invalidate(share)
            throw error
        }
        return EchoSmbScanResult(fileCount = files, hitLimit = hitLimit)
    }
}
