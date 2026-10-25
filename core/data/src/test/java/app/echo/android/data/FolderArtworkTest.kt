package app.echo.android.data

import org.junit.Assert.*
import org.junit.Test

class FolderArtworkTest {
    @Test fun sameNamePicturesStayWithTheirOwnTrackAndPreferJpeg() {
        val images = mapOf("first.song.jpg" to "first", "first.song.png" to "png", "second.jpeg" to "second", "cover.jpg" to "album")
        assertEquals(listOf("first", "png"), FolderArtworkPolicy.sidecars("First.Song.FLAC", images))
        assertEquals(listOf("second"), FolderArtworkPolicy.sidecars("SECOND.mp3", images))
        assertTrue(FolderArtworkPolicy.sidecars("unrelated.flac", images).isEmpty())
        assertTrue(FolderArtworkPolicy.sidecarNames("../escape.mp3").isEmpty())
        assertTrue(FolderArtworkPolicy.rank("ALBUM.JPEG") != Int.MAX_VALUE)
    }
    @Test fun priorityIsCaseInsensitiveAndRejectsNonImages() {
        assertEquals(0, FolderArtworkPolicy.rank("COVER.JPG"))
        assertTrue(FolderArtworkPolicy.rank("folder.png") > FolderArtworkPolicy.rank("cover.jpg"))
        assertEquals(Int.MAX_VALUE, FolderArtworkPolicy.rank("song.jpg"))
        assertNull(FolderArtworkPolicy.read("not an image".byteInputStream()))
        val png = byteArrayOf(0x89.toByte(), 80, 78, 71, 13, 10, 26, 10)
        assertArrayEquals(png, FolderArtworkPolicy.read(png.inputStream()))
        assertNull(FolderArtworkPolicy.read(ByteArray(FolderArtworkPolicy.MaxBytes + 1).also { png.copyInto(it) }.inputStream()))
    }
}
