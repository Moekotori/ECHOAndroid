package app.echo.android.model.connect

import org.junit.Assert.*
import org.junit.Test

class EchoSyncReconcileTest {
    private fun ref(name: String) = EchoSyncTrackRef(name,"Artist","Album",100000)
    private fun state(vararg names: String,name: String = "List",exists: Boolean = true) = EchoSyncState("pc:list",name,exists,names.map(::ref))
    @Test fun removalDoesNotRemoveSongsThatOnlyExistOnTheOtherDevice() {
        val phone = state("A"); val pc = state("A","B")
        val result = EchoSyncReconcile.draft(state(),pc,EchoSyncBaseline(phone,pc))
        assertEquals(listOf("B"),result.pcDesired.tracks.map { it.title })
        assertFalse(result.conflict)
    }
    @Test fun oneSidedRenameAndReorderPreserveUnrelatedSlots() {
        val phone = state("A","B"); val pc = state("A","X","B")
        val result = EchoSyncReconcile.draft(state("B","A",name = "Renamed"),pc,EchoSyncBaseline(phone,pc))
        assertEquals("Renamed",result.pcDesired.name)
        assertEquals(listOf("B","X","A"),result.pcDesired.tracks.map { it.title })
    }
    @Test fun deletionVersusEditRequiresAChoice() {
        val base = state("A")
        val result = EchoSyncReconcile.draft(state(exists = false),state("A","B"),EchoSyncBaseline(base,base))
        assertTrue(result.conflict)
        assertFalse(EchoSyncReconcile.resolve(result,EchoSyncChoice.Phone).second.exists)
        assertTrue(EchoSyncReconcile.resolve(result,EchoSyncChoice.Merge).first.exists)
    }
    @Test fun firstSyncMergesAndUnchangedSyncDoesNothing() {
        val result = EchoSyncReconcile.draft(state("A"),state("B"),null)
        assertEquals(listOf("A","B"),result.phoneDesired.tracks.map { it.title })
        val baseline = EchoSyncBaseline(result.phoneDesired,result.pcDesired)
        val unchanged = EchoSyncReconcile.draft(baseline.phone,baseline.pc,baseline)
        assertTrue(EchoSyncReconcile.same(unchanged.phone,unchanged.phoneDesired))
        assertTrue(EchoSyncReconcile.same(unchanged.pc,unchanged.pcDesired))
    }
}
