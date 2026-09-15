package app.echo.android.data

import android.content.Context
import android.util.AtomicFile
import app.echo.android.model.playback.EchoRemotePinPolicy
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray

class EchoRemotePinStore(context: Context) {
    private val file = AtomicFile(File(context.applicationContext.filesDir, "remote-pins.json"))
    private val mutex = Mutex()

    suspend fun load(): List<String> = withContext(Dispatchers.IO) {
        mutex.withLock { read() }
    }

    suspend fun save(ids: List<String>): List<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = EchoRemotePinPolicy.merge(emptyList(), ids)
            val json = JSONArray()
            next.forEach { json.put(it) }
            val bytes = json.toString().toByteArray()
            val output = file.startWrite()
            try {
                output.write(bytes)
                file.finishWrite(output)
            } catch (error: Throwable) {
                file.failWrite(output)
                throw error
            }
            next
        }
    }

    private fun read(): List<String> {
        val bytes = try {
            file.openRead().use { it.readBytes() }
        } catch (_: java.io.FileNotFoundException) {
            return emptyList()
        }
        val json = runCatching { JSONArray(String(bytes)) }.getOrNull() ?: return emptyList()
        return List(json.length()) { json.optString(it) }.filter { it.isNotBlank() }
            .distinct().take(EchoRemotePinPolicy.MaxTracks)
    }
}
