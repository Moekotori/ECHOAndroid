package app.echo.android.data

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONArray
import org.json.JSONObject

internal data class LastFmLoveRecord(val title: String, val artist: String, val sent: Boolean = false, val rejected: Boolean = false) {
    val key: String get() = lastFmMatchKey(title, artist)
}

/** Single worker owns this ledger. It contains metadata only and has hard byte and record limits. */
internal class LastFmLoveStore(private val file: File) {
    private var owner = ""
    private var estimatedBytes = 128
    private val records = linkedMapOf<String, LastFmLoveRecord>()
    var capacityReached = false
        private set
    val nextPending: LastFmLoveRecord? get() = records.values.firstOrNull { !it.sent && !it.rejected }
    val pendingCount: Int get() = records.values.count { !it.sent && !it.rejected }
    val rejectedCount: Int get() = records.values.count { it.rejected }
    val synced: Int get() = records.values.count { it.sent }

    fun load(account: String) {
        records.clear()
        estimatedBytes = 128
        capacityReached = false
        owner = account
        if (!file.isFile) return
        check(file.length() <= MAX_BYTES) { "Last.fm sync ledger exceeds byte limit" }
        val root = JSONObject(file.readText())
        if (root.getString("owner") != owner) { save(); return }
        val rows = root.getJSONArray("tracks")
        check(rows.length() <= MAX_RECORDS)
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val record = LastFmLoveRecord(row.getString("title"), row.getString("artist"), row.optBoolean("sent"), row.optBoolean("rejected"))
            check(record.title.length in 1..500 && record.artist.length in 1..500)
            records[record.key] = record
            estimatedBytes += estimate(record)
        }
    }

    fun enqueue(tracks: List<LastFmLocalTrack>) {
        var changed = false
        for (track in tracks) {
            if (track.title.isBlank() || track.artist.isBlank() || track.title.length > 500 || track.artist.length > 500) continue
            val record = LastFmLoveRecord(track.title, track.artist)
            if (record.key in records) continue
            if (records.size >= MAX_RECORDS || estimatedBytes + estimate(record) > MAX_BYTES) { capacityReached = true; continue }
            records[record.key] = record
            estimatedBytes += estimate(record)
            changed = true
        }
        if (changed) save()
    }

    fun sent(record: LastFmLoveRecord) {
        val previous = records[record.key] ?: return
        records[record.key] = previous.copy(sent = true)
        try { save() } catch (error: Exception) { records[record.key] = previous; throw error }
    }

    fun reject(record: LastFmLoveRecord) {
        val previous = records[record.key] ?: return
        records[record.key] = previous.copy(rejected = true)
        try { save() } catch (error: Exception) { records[record.key] = previous; throw error }
    }

    private fun save() {
        val encoded = JSONObject().put("owner", owner).put("tracks", JSONArray().apply {
            records.values.forEach { put(JSONObject().put("title", it.title).put("artist", it.artist).put("sent", it.sent).put("rejected", it.rejected)) }
        }).toString().toByteArray(Charsets.UTF_8)
        check(encoded.size <= MAX_BYTES) { "Last.fm sync ledger exceeds byte limit" }
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, file.name + ".tmp")
        try {
            FileOutputStream(temporary).use { it.write(encoded); it.fd.sync() }
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { temporary.delete() }
    }

    // Six bytes per UTF-16 code unit also bounds JSON escaping of control characters.
    private fun estimate(record: LastFmLoveRecord): Int = 96 + 6 * (record.title.length + record.artist.length)

    companion object {
        const val MAX_RECORDS = 10_000
        const val MAX_BYTES = 24 * 1024 * 1024
    }
}
