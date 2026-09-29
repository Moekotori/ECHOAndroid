package app.echo.android.smb

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.share.File
import java.io.Closeable
import java.io.IOException
import java.util.EnumSet

/**
 * 按偏移随机读取共享里的文件，供播放器的 DataSource 使用。
 * 不是线程安全的：一个实例只在一个加载线程上使用。
 */
class EchoSmbFileReader private constructor(
    private val file: File,
    val length: Long,
) : Closeable {
    /** 返回读到的字节数，到文件末尾返回 -1。 */
    fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        if (position >= this.length) return -1
        return file.read(buffer, position, offset, length)
    }

    override fun close() {
        runCatching { file.close() }
    }

    companion object {
        fun open(uri: String): EchoSmbFileReader {
            val location = EchoSmbPaths.parse(uri) ?: throw IOException("Not an SMB address: $uri")
            val share = EchoSmbConnections.share(location)
            val file = try {
                share.openFile(
                    location.path.replace('/', '\\'),
                    EnumSet.of(AccessMask.GENERIC_READ),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null,
                )
            } catch (error: Exception) {
                EchoSmbConnections.invalidate(share)
                throw IOException("Cannot open SMB file", error)
            }
            val size = runCatching { file.fileInformation.standardInformation.endOfFile }.getOrElse { error ->
                runCatching { file.close() }
                throw IOException("Cannot read SMB file size", error)
            }
            return EchoSmbFileReader(file, size)
        }
    }
}
