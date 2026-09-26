package app.echo.android.plugin

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI

internal fun openPluginHttp(uri: URI): PluginHttpExchange {
    val scheme = uri.scheme?.lowercase()
    if ((scheme != "http" && scheme != "https") || uri.userInfo != null) throw IOException("blocked")
    val connection = uri.toURL().openConnection() as? HttpURLConnection ?: throw IOException("blocked")
    return try {
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("User-Agent", "ECHOPlugin/1")
        val status = connection.responseCode
        val location = connection.getHeaderField("Location")
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        PluginHttpExchange(status, location, stream?.use { it.readAtMost(PluginHttp.MaxBodyBytes) } ?: ByteArray(0))
    } finally {
        connection.disconnect()
    }
}

private fun InputStream.readAtMost(max: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (total < max) {
        val read = read(buffer, 0, minOf(buffer.size, max - total))
        if (read < 0) break
        output.write(buffer, 0, read)
        total += read
    }
    return output.toByteArray()
}
