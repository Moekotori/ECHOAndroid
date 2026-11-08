package app.echo.android.data

import org.json.JSONObject

/** Only browse snapshots are retained; search and sync pages remain uncached. */
internal class SubsonicBrowseSnapshots(private val now: () -> Long = System::currentTimeMillis) {
    private data class Key(val endpoint: SubsonicEndpoint, val path: String, val params: List<Pair<String, String>>)
    private data class Snapshot(val json: JSONObject, val expires: Long, val weight: Int)
    private val entries = LinkedHashMap<Key, Snapshot>(8, .75f, true)
    private var weight = 0
    private var generation = 0L

    @Synchronized fun clear() { entries.clear(); weight = 0; generation++ }

    fun read(endpoint: SubsonicEndpoint, path: String, params: List<Pair<String, String>>, load: () -> JSONObject): JSONObject {
        val key = Key(endpoint, path, params)
        val started = synchronized(this) {
            entries[key]?.let { row ->
                if (now() < row.expires) return row.json
                entries.remove(key); weight -= row.weight
            }
            generation
        }
        val json = load()
        // Account for parsed strings/objects conservatively, not just the response byte size.
        val cost = json.toString().length.toLong() * 4
        if (cost > MaxWeight) return json
        synchronized(this) {
            if (started != generation) return json
            entries.remove(key)?.let { weight -= it.weight }
            entries[key] = Snapshot(json, now() + 60_000, cost.toInt()); weight += cost.toInt()
            while (weight > MaxWeight || entries.size > 8) {
                val first = entries.keys.first(); weight -= entries.remove(first)!!.weight
            }
        }
        return json
    }

    companion object { private const val MaxWeight = 16 * 1024 * 1024 }
}
