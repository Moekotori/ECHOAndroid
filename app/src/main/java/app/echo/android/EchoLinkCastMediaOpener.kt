package app.echo.android

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import app.echo.android.connect.EchoLinkCastBody
import app.echo.android.connect.EchoLinkCastBodyFactory
import app.echo.android.connect.EchoLinkCastPolicy
import app.echo.android.playback.EchoRemotePlaybackAuthRegistry
import java.io.File
import java.io.FileInputStream
import java.io.FilterInputStream
import java.net.HttpURLConnection
import java.net.URI

class EchoLinkCastMediaOpener(
    private val resolver: ContentResolver,
) : EchoLinkCastBodyFactory {
    override fun open(uri: String, startByte: Long): EchoLinkCastBody? {
        val trimmed = uri.trim()
        if (EchoLinkCastPolicy.isRemoteHttpUri(trimmed)) return openHttp(trimmed, startByte)
        val parsed = Uri.parse(trimmed)
        val pfd = runCatching { openDescriptor(parsed) }.getOrNull() ?: return null
        val stream = FileInputStream(pfd.fileDescriptor)
        val total = runCatching { stream.channel.size() }.getOrNull()
            ?: pfd.statSize.takeIf { it > 0L }
        if (total != null && startByte >= total) {
            runCatching { stream.close() }
            runCatching { pfd.close() }
            return null
        }
        if (startByte > 0L) {
            runCatching { stream.channel.position(startByte) }.onFailure {
                runCatching { stream.close() }
                runCatching { pfd.close() }
                return null
            }
        }
        return EchoLinkCastBody(
            stream = object : FilterInputStream(stream) {
                override fun close() {
                    try {
                        super.close()
                    } finally {
                        runCatching { pfd.close() }
                    }
                }
            },
            totalLength = total,
            mimeType = resolver.getType(parsed) ?: EchoLinkCastPolicy.mimeTypeForUri(uri),
        )
    }

    private fun openDescriptor(parsed: Uri): ParcelFileDescriptor? =
        when (parsed.scheme?.lowercase()) {
            "content" -> resolver.openFileDescriptor(parsed, "r")
            "file" -> {
                val path = parsed.path?.takeIf { it.isNotBlank() } ?: return null
                ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
            }
            else -> null
        }

    private fun openHttp(uri: String, startByte: Long): EchoLinkCastBody? {
        val request = EchoRemotePlaybackAuthRegistry.playbackRequest(uri)
        val connection = runCatching {
            URI(request.url).toURL().openConnection() as HttpURLConnection
        }.getOrNull() ?: return null
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 8_000
        connection.readTimeout = 20_000
        connection.requestMethod = "GET"
        request.headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
        if (startByte > 0L) connection.setRequestProperty("Range", "bytes=$startByte-")
        return runCatching {
            connection.connect()
            val code = connection.responseCode
            if (code !in 200..206) {
                connection.disconnect()
                return null
            }
            val input = connection.inputStream ?: run {
                connection.disconnect()
                return null
            }
            val total = EchoLinkCastPolicy.contentRangeTotal(connection.getHeaderField("Content-Range"))
                ?: connection.contentLengthLong.takeIf { it > 0L }?.let { length ->
                    if (code == 206) length + startByte else length
                }
            val mime = connection.contentType?.substringBefore(';')?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: EchoLinkCastPolicy.mimeTypeForUri(uri)
            EchoLinkCastBody(
                stream = object : FilterInputStream(input) {
                    override fun close() {
                        try {
                            super.close()
                        } finally {
                            connection.disconnect()
                        }
                    }
                },
                totalLength = total,
                mimeType = mime,
            )
        }.getOrElse {
            runCatching { connection.disconnect() }
            null
        }
    }
}
