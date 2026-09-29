package app.echo.android.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.common.PlaybackException
import app.echo.android.smb.EchoSmbFileReader
import app.echo.android.smb.EchoSmbPaths
import java.io.IOException

/** 直接从 SMB 共享按偏移读取音频。只在播放器的加载线程上调用。 */
@UnstableApi
internal class EchoSmbDataSource : BaseDataSource(/* isNetwork = */ true) {
    private var reader: EchoSmbFileReader? = null
    private var uri: Uri? = null
    private var position = 0L
    private var bytesRemaining = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val file = try {
            EchoSmbFileReader.open(dataSpec.uri.toString())
        } catch (error: IOException) {
            throw DataSourceException(error, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        }
        reader = file
        if (dataSpec.position > file.length) {
            throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
        }
        position = dataSpec.position
        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else {
            file.length - dataSpec.position
        }
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val file = reader ?: throw IOException("SMB source is not open")
        val toRead = minOf(length.toLong(), bytesRemaining).toInt()
        val read = try {
            file.read(position, buffer, offset, toRead)
        } catch (error: Exception) {
            throw DataSourceException(error, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        }
        if (read <= 0) return C.RESULT_END_OF_INPUT
        position += read
        bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        try {
            reader?.close()
        } finally {
            reader = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }
}

/**
 * smb:// 交给 [EchoSmbDataSource]，其它地址交给默认实现。放在 ResolvingDataSource 的上游，
 * 播放、智能过渡和 ReplayGain 扫描都经过这里。
 */
@UnstableApi
internal class EchoSmbAwareDataSourceFactory(
    private val fallback: DataSource.Factory,
) : DataSource.Factory {
    override fun createDataSource(): DataSource = SmbAwareDataSource(fallback.createDataSource())

    private class SmbAwareDataSource(private val fallback: DataSource) : DataSource {
        private val smb = EchoSmbDataSource()
        private var active: DataSource? = null

        override fun addTransferListener(transferListener: TransferListener) {
            fallback.addTransferListener(transferListener)
            smb.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            val target = if (EchoSmbPaths.isSmbUri(dataSpec.uri.toString())) smb else fallback
            active = target
            return target.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            active?.read(buffer, offset, length) ?: throw IOException("DataSource is not open.")

        override fun getUri(): Uri? = active?.uri

        override fun getResponseHeaders(): Map<String, List<String>> =
            active?.responseHeaders ?: emptyMap()

        override fun close() {
            val current = active
            active = null
            current?.close()
        }
    }
}
