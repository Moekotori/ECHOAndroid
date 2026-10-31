package app.echo.android.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OriginalAudioShareStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val session = "11111111-1111-1111-1111-111111111111"

    private fun write(root: File, created: Long): File = File(root, session).apply { mkdirs() }.let { directory ->
        File(directory, "manifest.json").apply {
            writeText(JSONObject().put("createdAt", created).put("files", JSONArray().put(JSONObject()
                .put("source", "file:///music/original.flac").put("name", "Song.flac").put("mime", "audio/flac").put("size", 4096))).toString())
        }
    }

    @Test fun restartPreservesMetadataAndCachedEntriesStillExpire() {
        val root = temporary.newFolder()
        var now = 10L
        write(root, now)
        val store = OriginalAudioShareStore(root) { now }
        val first = store.entry(session, 0, "Song.flac")
        assertEquals(4096L, first.size)
        assertEquals(first, OriginalAudioShareStore(root) { now }.entry(session, 0, "Song.flac"))
        now += OriginalAudioShareStore.RetentionMs + 1
        assertThrows(FileNotFoundException::class.java) { store.entry(session, 0, "Song.flac") }
    }

    @Test fun exactFilenameAndIndexAreRequiredAndDeletedOrOversizedManifestsCannotUseCache() {
        val root = temporary.newFolder()
        val manifest = write(root, 10)
        val store = OriginalAudioShareStore(root) { 10 }
        store.entry(session, 0, "Song.flac")
        assertThrows(FileNotFoundException::class.java) { store.entry("../$session", 0, "Song.flac") }
        assertThrows(FileNotFoundException::class.java) { store.entry(session, 0, "Other.flac") }
        assertThrows(FileNotFoundException::class.java) { store.entry(session, 1, "Song.flac") }
        manifest.delete()
        assertThrows(FileNotFoundException::class.java) { store.entry(session, 0, "Song.flac") }
        manifest.writeBytes(ByteArray(OriginalAudioShareStore.MaxManifestBytes + 1))
        assertThrows(FileNotFoundException::class.java) { store.entry(session, 0, "Song.flac") }
    }
}
