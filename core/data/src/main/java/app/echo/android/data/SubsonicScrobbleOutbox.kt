package app.echo.android.data

import app.echo.android.model.library.*
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import org.json.JSONArray
import org.json.JSONObject

/** Durable receipts contain identities and timestamps, never passwords or signed URLs. I/O only. */
class SubsonicScrobbleOutbox(private val directory: File,
    private val send: (SubsonicEndpoint, String, Long) -> Unit = { endpoint, songId, timestamp ->
        SubsonicClient(endpoint).submitListen(songId, true, timestamp)
    }) {
    private data class Row(val source: String, val receipt: NavidromeReceipt)
    private val lock = ReentrantLock()

    fun submit(endpoint: SubsonicEndpoint, songId: String, timestampMs: Long) = lock.withLock {
        require(songId.isNotBlank() && timestampMs > 0)
        val rows = read().toMutableList()
        val key = key(endpoint.sourceId, songId, timestampMs)
        if (confirmed().contains(key)) return@withLock
        // Repeated ticks and process recovery must never blindly replay an uncertain submission.
        if (rows.any { it.receipt.key == key }) return@withLock
        check(rows.size < 1000) { "Navidrome receipt storage is full" }
        val row = Row(endpoint.sourceId, NavidromeReceipt(key, songId, timestampMs, NavidromeReceiptState.Pending, 0))
        rows += row
        write(rows)
        deliver(endpoint, row, rows)
    }

    fun receipts(source: String): List<NavidromeReceipt> = lock.withLock {
        read().filter { it.source == source }.map { row ->
            row.receipt.let { if (it.state == NavidromeReceiptState.Sending) it.copy(state = NavidromeReceiptState.Unknown) else it }
        }.sortedByDescending { it.timestampMs }
    }

    fun resolve(endpoint: SubsonicEndpoint, key: String, resolution: NavidromeReceiptResolution) = lock.withLock {
        val rows = read().toMutableList()
        val row = rows.firstOrNull { it.source == endpoint.sourceId && it.receipt.key == key } ?: return@withLock
        if (resolution == NavidromeReceiptResolution.Retry) deliver(endpoint, row, rows)
        else { rememberConfirmed(key); rows.remove(row); write(rows) }
    }

    /** Bounded event-driven retry of requests known not to have been sent before a process exit. */
    fun flushPending(endpoint: SubsonicEndpoint, shouldContinue: () -> Boolean = { true }) = lock.withLock {
        val rows = read().toMutableList()
        for (row in rows.filter { it.source == endpoint.sourceId && it.receipt.state == NavidromeReceiptState.Pending }.take(8)) {
            if (!shouldContinue()) break
            deliver(endpoint, row, rows)
        }
    }

    private fun deliver(endpoint: SubsonicEndpoint, row: Row, rows: MutableList<Row>) {
        val sending = row.copy(receipt = row.receipt.copy(state = NavidromeReceiptState.Sending, attempts = row.receipt.attempts + 1))
        rows.removeAll { it.receipt.key == row.receipt.key }; rows += sending; write(rows)
        try {
            send(endpoint, row.receipt.songId, row.receipt.timestampMs)
            // Keep a bounded confirmation marker so a repeated tick cannot submit this listen twice.
            rows.remove(sending)
            rememberConfirmed(row.receipt.key)
            write(rows)
        } catch (cancelled: java.util.concurrent.CancellationException) { throw cancelled }
        catch (error: Exception) {
            rows.remove(sending)
            rows += sending.copy(receipt = sending.receipt.copy(state =
                if (error is SubsonicApiException) NavidromeReceiptState.Rejected else NavidromeReceiptState.Unknown))
            write(rows)
            // The receipt now owns recovery. No exception-driven automatic replay after an unknown delivery.
        }
    }

    private fun key(source: String, songId: String, timestampMs: Long): String =
        MessageDigest.getInstance("SHA-256").digest("$source\u0000$songId\u0000$timestampMs".toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun read(): List<Row> {
        val file = File(directory, "pending.json")
        if (!file.exists()) return emptyList()
        check(file.length() <= 1_000_000) { "Invalid Navidrome receipt storage" }
        val array = JSONArray(file.readText())
        check(array.length() <= 1000)
        return array.objectList().map { row -> Row(row.getString("source"), NavidromeReceipt(row.getString("key"),
            row.getString("songId"), row.getLong("timestamp"), NavidromeReceiptState.valueOf(row.getString("state")), row.getInt("attempts"))) }
    }

    private fun write(rows: List<Row>) {
        directory.mkdirs()
        val array = JSONArray()
        rows.forEach { row -> array.put(JSONObject().put("source", row.source).put("key", row.receipt.key)
            .put("songId", row.receipt.songId).put("timestamp", row.receipt.timestampMs)
            .put("state", row.receipt.state.name).put("attempts", row.receipt.attempts)) }
        atomicWrite(File(directory, "pending.json"), array.toString())
    }

    private fun rememberConfirmed(key: String) {
        val file = File(directory, "confirmed.json")
        atomicWrite(file, JSONArray((confirmed() + key).takeLast(2000)).toString())
    }

    private fun confirmed(): List<String> {
        val file = File(directory, "confirmed.json")
        check(!file.exists() || file.length() <= 200_000)
        return if (file.exists()) JSONArray(file.readText()).let { array ->
            check(array.length() <= 2000)
            (0 until array.length()).map { array.getString(it) }
        } else emptyList()
    }

    private fun atomicWrite(file: File, value: String) {
        val temporary = File(directory, file.name + ".tmp")
        temporary.outputStream().use { output -> output.write(value.toByteArray()); output.fd.sync() }
        check(temporary.renameTo(file)) { "Cannot store Navidrome receipts" }
    }
}
