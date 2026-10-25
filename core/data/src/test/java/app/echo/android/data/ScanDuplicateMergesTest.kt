package app.echo.android.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ScanDuplicateMergesTest {
    @Test fun failedOrMissingDestinationsKeepTheSourceForRetry() = runBlocking {
        val old = "saf:doc#cue:2"
        val target = "mediastore:8"
        val live = mutableSetOf(old, target)
        val seen = mutableSetOf(target)
        var calls = 0
        mergeScanAliases(listOf(old to target), live, seen) { _, _ -> calls++; false }
        assertTrue(old in live)
        assertTrue(old in seen)
        mergeScanAliases(listOf(old to target, old to target), live, seen) { _, _ -> calls++; true }
        assertFalse(old in live)
        assertEquals(2, calls) // A duplicate alias cannot merge/count the same source twice.

        val unresolved = mutableSetOf(old)
        val retained = mutableSetOf<String>()
        mergeScanAliases(listOf(old to "missing"), unresolved, retained) { _, _ -> error("No destination") }
        assertTrue(old in unresolved)
        assertTrue(old in retained)
    }

    @Test fun crossSourceCueRemovalRequiresAConfirmedWholeFile() {
        val old = "saf:doc#cue:2"
        val base = "mediastore:8"
        val live = setOf(old, base)
        val children = LibraryScanPolicy.cueChildrenByBase(live)
        assertNull(LibraryScanPolicy.duplicateMergeTarget(old, base, live, children, emptySet()))
        assertEquals(base, LibraryScanPolicy.duplicateMergeTarget(old, base, live, children, setOf(base)))
        val active = live + "$base#cue:1"
        assertNull(LibraryScanPolicy.duplicateMergeTarget(old, base, active,
            LibraryScanPolicy.cueChildrenByBase(active), setOf(base, "$base#cue:1")))
        assertEquals(listOf("saf:doc" to base, old to base),
            LibraryScanPolicy.safDuplicateAliases("saf:doc", listOf(base), listOf(old, "saf:other#cue:1")))
    }

    @Test fun cancellationCannotBeRecordedAsACommittedMerge() = runBlocking {
        val live = mutableSetOf("old", "target")
        val seen = mutableSetOf("target")
        try {
            mergeScanAliases(listOf("old" to "target"), live, seen) { _, _ ->
                throw kotlinx.coroutines.CancellationException("cancelled transaction")
            }
            fail("Cancellation must propagate")
        } catch (_: kotlinx.coroutines.CancellationException) { }
        assertTrue("old" in live)
        assertFalse("old" in seen)
    }
}
