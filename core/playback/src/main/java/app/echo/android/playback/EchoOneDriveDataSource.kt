package app.echo.android.playback

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

object EchoOneDrivePlayback {
    @Volatile
    internal var resolver: (suspend (String) -> String)? = null
        private set

    fun setStreamResolver(resolve: suspend (String) -> String) {
        resolver = resolve
    }
}

@UnstableApi
internal class EchoOneDriveDataSource : DataSource {
    private val http = DefaultHttpDataSource.Factory().createDataSource()
    private var stableUri: Uri? = null

    override fun addTransferListener(transferListener: TransferListener) = http.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        stableUri = dataSpec.uri
        val resolve = EchoOneDrivePlayback.resolver ?: throw IOException("OneDrive is unavailable")
        val address = withoutDownloadAddress { runBlocking { withTimeout(25_000) { resolve(dataSpec.uri.toString()) } } }
        val download = dataSpec.buildUpon().setUri(address).setHttpRequestHeaders(emptyMap()).build()
        return withoutDownloadAddress { http.open(download) }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        withoutDownloadAddress { http.read(buffer, offset, length) }

    override fun getUri(): Uri? = stableUri

    override fun getResponseHeaders(): Map<String, List<String>> = emptyMap()

    override fun close() {
        stableUri = null
        withoutDownloadAddress { http.close() }
    }

    // Signed download URLs belong only to this HTTP connection, including on failures.
    private inline fun <T> withoutDownloadAddress(block: () -> T): T = try {
        block()
    } catch (_: Exception) {
        throw IOException("Unable to read OneDrive audio")
    }
}
