package app.echo.android.data

import org.junit.Assert.*
import org.junit.Test

class CueCatalogScopeTest {
    @Test fun selectedDirectoryKeepsAncestorSidecarsButQueriesOnlyItsAudioSubtree() {
        val scope = CueCatalogScope("Music/Album/", true, "/storage/emulated/0")
        val cues = scope.cueSelection()!!
        assertTrue(cues.args.contains("Music/Album/%"))
        assertTrue(cues.args.contains("Music/"))
        assertTrue(cues.args.contains(""))
        assertEquals(listOf("Music/Album/%"), scope.audioSelections(listOf("Music")).single()!!.args.toList())
        assertTrue(scope.includes("Music/Album/Disc"))
        assertFalse(scope.includes("Music/Other"))
    }

    @Test fun primaryRootCannotHideRemovableVolumesAndQueriesStayBelowSqlParameterLimits() {
        val scope = CueCatalogScope(null, true, "")
        val all = scope.audioSelections(listOf("", "Music", "Removable/card/Music"))
        assertEquals(1, all.size)
        assertTrue(all.single()!!.args.contains("card"))
        val chunks = scope.audioSelections((1..2000).map { "Folder$it" })
        assertEquals(32, chunks.size)
        assertTrue(chunks.all { it!!.args.size <= 64 })
    }

    @Test fun legacyPathsUseProviderMountPathsAndEscapeLiteralWildcards() {
        val legacy = CueCatalogScope(null, false, "/storage/emulated/0")
        val query = legacy.audioSelections(listOf("Removable/card/Music"), listOf("/mnt/media_rw/card/A_%")).single()!!
        assertEquals(listOf("""/mnt/media\_rw/card/A\_\%/%"""), query.args.toList())
        assertEquals(query.sql.count { it == '?' }, query.args.size)
        val modern = CueCatalogScope("Removable/card/Music/", true, "").cueSelection()!!
        assertTrue(modern.args.contains("card"))
        assertFalse(modern.args.contains("Removable/"))
    }

    @Test fun failedDirectoryLookupUsesAncestorsWithoutMatchingSiblingNames() {
        val index = ScanDirectoryIndex((1..4000).map { "Music/Disc$it" }, ignoreCase = true)
        assertTrue(index.contains("MUSIC/Disc3999/Sub/"))
        assertFalse(index.contains("Music/Disc3999 live"))
        index.add("")
        assertTrue(index.contains("Other"))
    }
}
