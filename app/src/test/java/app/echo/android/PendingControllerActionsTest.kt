package app.echo.android

import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@UnstableApi
class PendingControllerActionsTest {
    @Test
    fun repeatedPlayPauseKeepsFinalIntentAndQueueSelection() {
        val pending = PendingControllerActions()
        pending.add(PendingControllerAction(true, true) {})
        repeat(4) {
            pending.add(PendingControllerAction(false, !pending.playWhenReady!!) {})
        }
        assertEquals(true, pending.playWhenReady)
        assertEquals(2, pending.count())
        assertTrue(pending.removeFirst().replacesQueue)
        assertEquals(true, pending.removeFirst().playWhenReady)
        assertFalse(pending.isNotEmpty())
    }

    @Test
    fun latestSelectionReleasesObsoleteCapturedCommands() {
        val pending = PendingControllerActions()
        pending.add(PendingControllerAction(true, true) {})
        pending.add(PendingControllerAction(false, false) {})
        val latest = PendingControllerAction(true, true) {}
        pending.add(latest)
        assertEquals(listOf(latest), pending.toList())
    }

    @Test
    fun longDisconnectionBoundsActionsAndRetainsTheirQueueAnchor() {
        val pending = PendingControllerActions()
        val anchor = PendingControllerAction(true, true) {}
        pending.add(anchor)
        pending.add(PendingControllerAction(false, false) {})
        repeat(100) { pending.add(PendingControllerAction(false, null) {}) }
        assertEquals(PendingControllerActions.MaxActions, pending.count())
        assertEquals(false, pending.playWhenReady)
        assertEquals(anchor, pending.removeFirst())
        pending.clear()
        assertFalse(pending.isNotEmpty())
    }
}
