package app.echo.android.data

import org.junit.Assert.*
import org.junit.Test

class DocumentSiblingFilesTest {
    @Test fun resolvesRootAndNestedParentsWithinGrant() {
        assertEquals("primary:", DocumentSiblingFiles.parentId("primary:", "primary:song.mp3"))
        assertEquals("1234-ABCD:Music/Album", DocumentSiblingFiles.parentId("1234-ABCD:Music", "1234-ABCD:Music/Album/song.flac"))
        assertEquals("primary:Music", DocumentSiblingFiles.parentId("primary:Music", "primary:Music/song.mp3"))
    }
    @Test fun rejectsOtherVolumesSiblingPrefixesAndTreeRoot() {
        assertNull(DocumentSiblingFiles.parentId("primary:Music", "primary:MusicOther/song.mp3"))
        assertNull(DocumentSiblingFiles.parentId("primary:Music", "1234-ABCD:Music/song.mp3"))
        assertNull(DocumentSiblingFiles.parentId("primary:Music", "primary:Music"))
        assertNull(DocumentSiblingFiles.parentId("primary:Music", "primary:Music/../Other/song.mp3"))
    }
}
