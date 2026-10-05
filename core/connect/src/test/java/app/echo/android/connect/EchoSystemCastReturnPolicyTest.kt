package app.echo.android.connect

import org.junit.Assert.*
import org.junit.Test

class EchoSystemCastReturnPolicyTest {
    @Test fun onlyPublishedContentCanReturnToThePhoneQueue() {
        val published = mapOf("http://phone/stream/first" to 0, "http://phone/stream/second" to 1)
        assertEquals(1, EchoSystemCastReturnPolicy.index(published, "http://phone/stream/second"))
        assertNull(EchoSystemCastReturnPolicy.index(published, "http://other/stream/second"))
        assertNull(EchoSystemCastReturnPolicy.index(published, null))
    }

    @Test fun aReplacedQueueOrDifferentOccurrenceCannotBeRestored() {
        assertTrue(EchoSystemCastReturnPolicy.sameQueue(5, 5, "entry1", "entry1"))
        assertFalse(EchoSystemCastReturnPolicy.sameQueue(5, 5, "entry1", "entry2"))
        assertFalse(EchoSystemCastReturnPolicy.sameQueue(5, 6, "entry1", "entry1"))
        assertFalse(EchoSystemCastReturnPolicy.sameQueue(0, 0, null, null))
    }

    @Test fun invalidOrTerminalPositionsStayWithinTheTrack() {
        assertEquals(0, EchoSystemCastReturnPolicy.position(-1, 1000))
        assertEquals(1000, EchoSystemCastReturnPolicy.position(2000, 1000))
        assertEquals(2000, EchoSystemCastReturnPolicy.position(2000, 0))
    }

    @Test fun reorderingResolvesTheOccurrenceAndReplacementNeverTargetsAnotherSong() {
        assertEquals(2, EchoSystemCastReturnPolicy.targetIndex("entry-b", listOf(1 to "entry-c", 2 to "entry-b")))
        assertNull(EchoSystemCastReturnPolicy.targetIndex("entry-b", listOf(1 to "entry-c", 2 to "new-entry-b")))
    }
}
