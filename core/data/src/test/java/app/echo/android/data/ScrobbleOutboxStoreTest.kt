package app.echo.android.data

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ScrobbleOutboxStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun listen(account: String = "account-a", id: String = "track") = PendingScrobble(
        account, id, "Song", "Artist", "Album", 180_000L, System.currentTimeMillis() / 1_000L,
    )

    @Test fun persistsTimestampDeduplicationAndRetryAcrossRestart() = runBlocking {
        val file = File(temporary.root, "listens.json")
        val store = ScrobbleOutboxStore(file)
        val original = listen()
        store.enqueue(original)
        store.enqueue(original)
        store.fail(original, 1_000L)
        val restored = ScrobbleOutboxStore(file).read().single()
        assertEquals(original.startedAtEpochSeconds, restored.startedAtEpochSeconds)
        assertEquals(original.trackId, restored.trackId)
        assertEquals(1, restored.failures)
        assertEquals(31_000L, restored.retryAtEpochMs)
        assertFalse(file.readText().contains("token"))
        store.acknowledge(restored.key)
        assertTrue(ScrobbleOutboxStore(file).read().isEmpty())
    }

    @Test fun isolatesAccountsAndClearsOnSignOut() = runBlocking {
        val store = ScrobbleOutboxStore(File(temporary.root, "listens.json"))
        store.enqueue(listen())
        store.enqueue(listen("account-b"))
        store.retainAccount("account-b")
        assertEquals("account-b", store.read().single().account)
        store.retainAccount(null)
        assertTrue(store.read().isEmpty())
    }

    @Test fun boundsMetadataAndEncodedBytesWithoutLosingTheNewestListen() = runBlocking {
        val file = File(temporary.root, "bounded.json")
        val store = ScrobbleOutboxStore(file)
        assertTrue(runCatching { store.enqueue(listen().copy(title = "x".repeat(501))) }.isFailure)
        java.io.RandomAccessFile(file, "rw").use { it.setLength(ScrobbleOutboxStore.MAX_FILE_BYTES.toLong() + 1) }
        assertTrue(runCatching { store.read() }.isFailure)
        assertEquals(ScrobbleOutboxStore.MAX_FILE_BYTES.toLong() + 1, file.length())

        val original = listen().copy(title = "你".repeat(500), artist = "歌".repeat(500), album = "辑".repeat(500))
        fun encoded(item: PendingScrobble) = org.json.JSONObject().apply {
            put("account", item.account); put("trackId", item.trackId); put("title", item.title)
            put("artist", item.artist); put("album", item.album); put("durationMs", item.durationMs)
            put("startedAt", item.startedAtEpochSeconds); put("failures", 0); put("retryAt", 0)
        }.toString().toByteArray(Charsets.UTF_8)
        var count = 0
        var bytes = 2
        file.outputStream().buffered().use { output ->
            output.write('['.code)
            while (count < ScrobbleOutboxStore.MAX_PENDING) {
                val row = encoded(original.copy(trackId = count.toString().padStart(4096, 'x'),
                    startedAtEpochSeconds = original.startedAtEpochSeconds - count))
                val addition = row.size + if (count == 0) 0 else 1
                if (bytes + addition > ScrobbleOutboxStore.MAX_FILE_BYTES) break
                if (count > 0) output.write(','.code)
                output.write(row); bytes += addition; count++
            }
            output.write(']'.code)
        }
        val newest = original.copy(trackId = "new".padStart(4096, 'x'), startedAtEpochSeconds = original.startedAtEpochSeconds + 1)
        store.enqueue(newest)
        val retained = store.read()
        assertTrue(file.length() <= ScrobbleOutboxStore.MAX_FILE_BYTES)
        assertTrue(retained.size <= count)
        assertTrue(retained.any { it.key == newest.key })
    }

    @Test fun retriesBackOffAndExpireInsteadOfGrowingForever() = runBlocking {
        val store = ScrobbleOutboxStore(File(temporary.root, "listens.json"))
        val original = listen()
        var failed = original
        repeat(30) { failed = failed.afterFailure(1_000L) }
        assertEquals(6 * 60 * 60_000L + 1_000L, failed.retryAtEpochMs)
        assertEquals(16, failed.failures)
        store.enqueue(original.copy(startedAtEpochSeconds = original.startedAtEpochSeconds - 15 * 24 * 60 * 60L))
        assertTrue(store.read().isEmpty())
    }
}
