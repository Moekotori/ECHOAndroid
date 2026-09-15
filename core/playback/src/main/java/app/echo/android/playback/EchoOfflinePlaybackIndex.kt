package app.echo.android.playback

import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** In-memory map of ready offline files. Playback resolve reads this, not Room. */
object EchoOfflinePlaybackIndex {
    private val files = AtomicReference<Map<String, String>>(emptyMap())

    fun replace(trackIdToPath: Map<String, String>) {
        files.set(trackIdToPath)
    }

    fun uriFor(trackId: String): String? {
        val path = files.get()[trackId] ?: return null
        val file = File(path)
        if (!file.isFile || file.length() <= 0L) return null
        return file.toURI().toString()
    }
}
