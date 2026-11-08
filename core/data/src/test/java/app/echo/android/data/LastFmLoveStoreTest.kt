package app.echo.android.data

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LastFmLoveStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val track = LastFmLocalTrack("id", "Song", "Artist")

    @Test fun pendingAndSentRecordsSurviveRestartWithoutDuplicatingLikes() {
        val file = File(temporary.root, "love.json")
        LastFmLoveStore(file).apply { load("alice"); enqueue(listOf(track, track)) }
        LastFmLoveStore(file).apply {
            load("alice")
            assertEquals(1, pendingCount)
            sent(checkNotNull(nextPending))
        }
        LastFmLoveStore(file).apply {
            load("alice")
            enqueue(listOf(track))
            assertEquals(0, pendingCount)
            assertEquals(1, synced)
        }
        assertFalse(file.readText().contains("session"))
    }

    @Test fun switchingAccountsCannotSendPreviousAccountsQueue() {
        val store = LastFmLoveStore(File(temporary.root, "love.json"))
        store.load("alice"); store.enqueue(listOf(track))
        store.load("bob")
        assertEquals(0, store.pendingCount)
        assertNull(store.nextPending)
    }

    @Test fun permanentFailuresDoNotBlockOtherLikesOrCountAsSynced() {
        val store = LastFmLoveStore(File(temporary.root, "love.json"))
        store.load("alice"); store.enqueue(listOf(track, track.copy(title = "Second")))
        store.reject(checkNotNull(store.nextPending))
        assertEquals("Second", store.nextPending?.title)
        assertEquals(1, store.pendingCount)
        assertEquals(0, store.synced)
        assertEquals(1, store.rejectedCount)
    }

    @Test fun metadataIdentityNormalizesWidthWhitespaceAndCase() {
        assertEquals(lastFmMatchKey("Ｓｏｎｇ", " Artist  Name "), lastFmMatchKey("song", "artist name"))
        assertNotEquals(lastFmMatchKey("Song", "Other"), lastFmMatchKey("Song", "Artist"))
    }
}
