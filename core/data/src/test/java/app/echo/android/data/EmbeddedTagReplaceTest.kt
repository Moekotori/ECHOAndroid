package app.echo.android.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedTagReplaceTest {
    @Test
    fun replaceOverwritesTargetWithSourceBytes() {
        withTempDir { dir ->
            val target = File(dir, "song.mp3").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
            val source = File(dir, "tagged.mp3").apply { writeBytes(byteArrayOf(9, 8, 7)) }

            EmbeddedTagFileReplace.replace(target, source)

            assertTrue(target.readBytes().contentEquals(byteArrayOf(9, 8, 7)))
            assertFalse(File(dir, ".${target.name}.echo-tmp").exists())
        }
    }

    @Test
    fun replaceCanRestoreOriginalFromBackup() {
        withTempDir { dir ->
            val before = byteArrayOf(10, 11, 12, 13, 14)
            val target = File(dir, "song.flac").apply { writeBytes(before) }
            val original = File(dir, "original.flac").apply { writeBytes(before) }
            val tagged = File(dir, "tagged.flac").apply { writeBytes(byteArrayOf(1, 1, 1)) }

            EmbeddedTagFileReplace.replace(target, tagged)
            assertTrue(target.readBytes().contentEquals(byteArrayOf(1, 1, 1)))

            EmbeddedTagFileReplace.replace(target, original)
            assertTrue(target.readBytes().contentEquals(before))
        }
    }

    @Test
    fun preserveOriginalKeepsFirstGoodBackup() {
        withTempDir { cache ->
            val first = File(cache, "original-1").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val preserved = EmbeddedTagBackup.preserveOriginal(first, cache, "track")
            assertEquals(byteArrayOf(1, 2, 3).toList(), preserved.readBytes().toList())
            assertFalse(first.exists())

            val second = File(cache, "original-2").apply { writeBytes(byteArrayOf(9, 9, 9)) }
            val kept = EmbeddedTagBackup.preserveOriginal(second, cache, "track")
            assertEquals(preserved.absolutePath, kept.absolutePath)
            assertEquals(byteArrayOf(1, 2, 3).toList(), kept.readBytes().toList())
            assertFalse(second.exists())
        }
    }

    private fun withTempDir(block: (File) -> Unit) {
        val dir = File(System.getProperty("java.io.tmpdir"), "echo-tag-${System.nanoTime()}")
        assertTrue(dir.mkdirs())
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }
}
