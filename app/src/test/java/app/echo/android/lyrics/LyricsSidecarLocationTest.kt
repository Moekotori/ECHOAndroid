package app.echo.android.lyrics

import org.junit.Assert.*
import org.junit.Test

class LyricsSidecarLocationTest {
    @Test
    fun modernQueriesKeepPrimaryAndRemovableVolumesSeparate() {
        val primary = requireNotNull(LyricsSidecarLocation.fromLibraryFolder("Music/Album/"))
        val card = requireNotNull(LyricsSidecarLocation.fromLibraryFolder("Removable/1234-abcd/Music/Album/"))
        assertEquals("external_primary", primary.volumeName)
        assertEquals("1234-abcd", card.volumeName)
        assertEquals("Music/Album/", primary.relativePath)
        assertEquals(primary.relativePath, card.relativePath)
    }

    @Test
    fun legacyMatchesRejectSameNameInOtherFoldersAndVolumes() {
        val primary = requireNotNull(LyricsSidecarLocation.fromLibraryFolder("Music/Album/"))
        val card = requireNotNull(LyricsSidecarLocation.fromLibraryFolder("Removable/1234-abcd/Music/Album/"))
        val root = "/storage/emulated/0"
        assertTrue(primary.containsLegacyFile("$root/Music/Album/song.lrc", root))
        assertFalse(primary.containsLegacyFile("$root/Music/Other/song.lrc", root))
        assertFalse(primary.containsLegacyFile("$root/Music/Album/Sub/song.lrc", root))
        assertFalse(primary.containsLegacyFile("/storage/1234-ABCD/Music/Album/song.lrc", root))
        assertTrue(card.containsLegacyFile("/storage/1234-ABCD/Music/Album/song.lrc", root))
        assertFalse(card.containsLegacyFile("$root/Music/Album/song.lrc", root))
        assertFalse(primary.containsLegacyFile(null, root))
    }
}
