package app.echo.android.ui.library

import org.junit.Assert.fail
import org.junit.Test

class FilePlaylistPermissionsTest {
    @Test fun refusesOversizedBatchesBeforeTakingAnyGrantButAllowsReuseAndFolders() {
        val existing = (1..95).mapTo(hashSetOf()) { "content://old/$it" }
        requireFilePlaylistGrantBudget(existing, listOf("content://folder/new"))
        requireFilePlaylistGrantBudget(existing, List(2000) { "content://old/1" })
        try {
            requireFilePlaylistGrantBudget(existing, listOf("content://new/1", "content://new/2"))
            fail("The existing grants must be preserved")
        } catch (_: IllegalArgumentException) { }
    }
}
