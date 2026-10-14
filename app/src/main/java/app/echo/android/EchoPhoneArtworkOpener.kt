package app.echo.android

import android.content.Context
import android.graphics.Bitmap
import app.echo.android.connect.EchoLinkCastBody
import app.echo.android.connect.EchoLinkCastPublication
import app.echo.android.playback.EchoPlaybackArtwork
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Read-only thumbnail export; do not send multi-megabyte embedded originals over LAN. */
class EchoPhoneArtworkOpener(private val context: Context) {
    fun open(publication: EchoLinkCastPublication, startByte: Long): EchoLinkCastBody? {
        val bitmap = EchoPlaybackArtwork.load(context, publication.artworkUrl, publication.uri, 512) ?: return null
        val bytes = try {
            val output = ByteArrayOutputStream(64 * 1024)
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 80, output)) return null
            output.toByteArray().takeIf { it.size <= 2 * 1024 * 1024 } ?: return null
        } finally {
            bitmap.recycle()
        }
        if (startByte < 0L || startByte >= bytes.size) return null
        return EchoLinkCastBody(ByteArrayInputStream(bytes, startByte.toInt(), bytes.size - startByte.toInt()),
            bytes.size.toLong(), "image/jpeg")
    }
}
