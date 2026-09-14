package app.echo.android.data

import android.content.Context
import android.util.AtomicFile
import app.echo.android.model.radio.EchoRadioStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Small, bounded collection separate from scanned tracks and the Room schema. */
class EchoRadioStore(context: Context) {
    private val file = AtomicFile(File(context.applicationContext.filesDir, "radio-stations.json"))
    private val mutex = Mutex()

    suspend fun load(): List<EchoRadioStation> = withContext(Dispatchers.IO) {
        mutex.withLock { readInitialized() }
    }

    suspend fun save(station: EchoRadioStation): List<EchoRadioStation> = update { stations ->
        require(station.id.isNotBlank() && station.name.isNotBlank() && station.name.length <= 120)
        require(EchoRadioStation.validUrl(station.url))
        require(stations.none { it.id != station.id && it.url == station.url })
        val index = stations.indexOfFirst { it.id == station.id }
        if (index >= 0) stations.toMutableList().also { it[index] = station }
        else {
            require(stations.size < EchoRadioStation.MaxStations)
            stations + station
        }
    }

    suspend fun delete(id: String): List<EchoRadioStation> = update { stations -> stations.filterNot { it.id == id } }

    private suspend fun update(transform: (List<EchoRadioStation>) -> List<EchoRadioStation>): List<EchoRadioStation> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val next = transform(readInitialized())
                write(next)
                next
            }
        }

    private fun write(stations: List<EchoRadioStation>) {
        val bytes = EchoRadioDefaults.encode(stations).toByteArray(Charsets.UTF_8)
        require(bytes.size <= 4 * 1024 * 1024)
        val output = file.startWrite()
        try {
            output.write(bytes)
            file.finishWrite(output)
        } catch (error: Throwable) {
            file.failWrite(output)
            throw error
        }
    }

    private fun readInitialized(): List<EchoRadioStation> {
        val bytes = try {
            file.openRead().use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 4 * 1024 * 1024)
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } catch (_: java.io.FileNotFoundException) {
            return EchoRadioDefaults.initialize(emptyList()).also(::write)
        }
        val text = String(bytes, Charsets.UTF_8)
        val legacy = text.trimStart().startsWith("[")
        val json = if (legacy) JSONArray(text) else JSONObject(text).getJSONArray("stations")
        require(json.length() <= EchoRadioStation.MaxStations)
        val stations = List(json.length()) { index ->
            val item = json.getJSONObject(index)
            EchoRadioStation(item.getString("id"), item.getString("name"), item.getString("url")).also {
                require(it.id.isNotBlank() && it.name.isNotBlank() && EchoRadioStation.validUrl(it.url))
            }
        }.also { require(it.map(EchoRadioStation::id).distinct().size == it.size) }
        return if (legacy) EchoRadioDefaults.initialize(stations).also(::write) else stations
    }
}
