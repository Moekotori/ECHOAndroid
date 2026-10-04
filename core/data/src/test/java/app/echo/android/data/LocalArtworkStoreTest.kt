package app.echo.android.data

import java.io.File
import java.net.URI
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalArtworkStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun sharedCoversSurviveRestartAndOnlyUnreferencedCoversArePruned() {
        val directory = temporary.newFolder("artwork")
        val store = LocalArtworkStore(directory)
        val bytes = byteArrayOf(1, 2, 3)
        val first = store.store(bytes)!!
        assertEquals(first, store.store(bytes))
        val unused = store.store(byteArrayOf(4, 5))!!
        val restarted = LocalArtworkStore(directory)
        assertEquals(first, restarted.store(bytes))
        restarted.prune(setOf(first))
        assertArrayEquals(bytes, File(URI(first)).readBytes())
        assertFalse(File(URI(unused)).exists())
    }

    @Test fun capacityNeverEvictsAReferencedCoverAndPruningAllowsNewArtwork() {
        val store = LocalArtworkStore(temporary.newFolder("bounded"), maxBytes = 5, maxImageBytes = 4)
        val first = store.store(byteArrayOf(1, 2, 3))!!
        assertNull(store.store(byteArrayOf(4, 5, 6)))
        assertTrue(File(URI(first)).exists())
        assertNull(store.store(ByteArray(5)))
        assertNull(store.store(null))
        assertNull(store.store(byteArrayOf()))
        store.prune(emptySet())
        assertNotNull(store.store(byteArrayOf(4, 5, 6)))
    }
}
