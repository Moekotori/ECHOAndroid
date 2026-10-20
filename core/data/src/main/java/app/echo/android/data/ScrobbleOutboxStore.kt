package app.echo.android.data

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Contains listening metadata and an opaque account fingerprint, never authorization material. */
data class PendingScrobble(
    val account: String,
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val startedAtEpochSeconds: Long,
    val failures: Int = 0,
    val retryAtEpochMs: Long = 0L,
) {
    val key: String get() = "$account:$startedAtEpochSeconds:$trackId"

    fun afterFailure(nowEpochMs: Long): PendingScrobble = copy(
        failures = (failures + 1).coerceAtMost(16),
        retryAtEpochMs = nowEpochMs + minOf(30_000L * (1L shl failures.coerceIn(0, 10)), 6 * 60 * 60_000L),
    )
}

/** Small bounded outbox, read and committed atomically off the main thread. One owner per file. */
class ScrobbleOutboxStore(private val file: File) {
    private val mutex = Mutex()

    suspend fun read(): List<PendingScrobble> = access {
        val current = readLocked()
        val retained = current.filter { it.startedAtEpochSeconds >= System.currentTimeMillis() / 1_000L - MAX_AGE_SECONDS }
        if (retained != current) writeLocked(retained)
        retained
    }

    suspend fun enqueue(listen: PendingScrobble) = update { current ->
        require(validRecord(listen)) { "Scrobble metadata exceeds supported bounds" }
        if (current.any { it.key == listen.key }) current else current + listen
    }

    suspend fun retainAccount(account: String?) = update { current -> current.filter { it.account == account } }

    suspend fun acknowledge(key: String) = update { current -> current.filterNot { it.key == key } }

    suspend fun fail(listen: PendingScrobble, nowEpochMs: Long) = update { current ->
        current.map { if (it.key == listen.key) it.afterFailure(nowEpochMs) else it }
    }

    private suspend fun update(transform: (List<PendingScrobble>) -> List<PendingScrobble>) = access {
        val current = readLocked()
        val next = transform(current)
        if (current != next) writeLocked(next)
    }

    private suspend fun <T> access(block: () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock { block() }
    }

    private fun readLocked(): List<PendingScrobble> {
        val input = try { file.inputStream() } catch (_: java.io.FileNotFoundException) { return emptyList() }
        val encoded = input.use { source ->
            if (source.channel.size() > MAX_FILE_BYTES) throw IOException("Scrobble outbox exceeds byte limit")
            val bytes = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (true) {
                // Also guard against the file growing after the size check.
                val count = source.read(buffer, 0, minOf(buffer.size, MAX_FILE_BYTES + 1 - bytes.size()))
                if (count < 0) break
                bytes.write(buffer, 0, count)
                if (bytes.size() > MAX_FILE_BYTES) throw IOException("Scrobble outbox exceeds byte limit")
            }
            bytes.toString(Charsets.UTF_8.name())
        }
        val array = JSONArray(encoded)
        if (array.length() > MAX_PENDING) throw IOException("Scrobble outbox exceeds record limit")
        return (0 until array.length()).mapNotNull { index ->
            try {
                val row = array.getJSONObject(index)
                PendingScrobble(
                    account = row.getString("account"), trackId = row.getString("trackId"),
                    title = row.getString("title"), artist = row.getString("artist"),
                    album = if (row.isNull("album")) null else row.getString("album").takeIf(String::isNotBlank),
                    durationMs = row.getLong("durationMs"), startedAtEpochSeconds = row.getLong("startedAt"),
                    failures = row.optInt("failures"), retryAtEpochMs = row.optLong("retryAt"),
                ).takeIf(::validRecord)
            } catch (_: JSONException) { null }
        }
    }

    private fun validRecord(listen: PendingScrobble): Boolean = with(listen) {
        account.isNotBlank() && account.length <= 128 && trackId.isNotBlank() && trackId.length <= 4_096 &&
            title.isNotBlank() && title.length <= 500 && artist.isNotBlank() && artist.length <= 500 &&
            (album?.length ?: 0) <= 500 && durationMs >= 0L && startedAtEpochSeconds > 0L &&
            failures in 0..16 && retryAtEpochMs >= 0L
    }

    private fun writeLocked(listens: List<PendingScrobble>) {
        val records = ArrayDeque<ByteArray>()
        var byteCount = 2 // JSON array brackets, plus one comma between records.
        // Retain a deterministic newest suffix, bounded by both record count and encoded bytes.
        val newest = listens.filter(::validRecord)
            .sortedWith(compareBy<PendingScrobble> { it.startedAtEpochSeconds }.thenBy { it.key })
            .takeLast(MAX_PENDING)
        for (listen in newest.asReversed()) {
            val row = JSONObject().apply {
                put("account", listen.account); put("trackId", listen.trackId)
                put("title", listen.title); put("artist", listen.artist)
                listen.album?.let { put("album", it) }
                put("durationMs", listen.durationMs); put("startedAt", listen.startedAtEpochSeconds)
                put("failures", listen.failures); put("retryAt", listen.retryAtEpochMs)
            }.toString().toByteArray(Charsets.UTF_8)
            val addition = row.size + if (records.isEmpty()) 0 else 1
            if (byteCount + addition > MAX_FILE_BYTES) break
            records.addFirst(row)
            byteCount += addition
        }
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write('['.code)
                records.forEachIndexed { index, row ->
                    if (index > 0) output.write(','.code)
                    output.write(row)
                }
                output.write(']'.code)
                output.fd.sync()
            }
            // Same-directory atomic replacement keeps the previous queue intact on write failure.
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temporary.delete()
        }
    }

    companion object {
        const val MAX_PENDING = 2_000
        const val MAX_FILE_BYTES = 8 * 1024 * 1024
        private const val MAX_AGE_SECONDS = 14 * 24 * 60 * 60L
    }
}
