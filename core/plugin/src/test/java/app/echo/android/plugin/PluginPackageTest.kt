package app.echo.android.plugin

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PluginPackageTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun installsAPackageUnderItsId() {
        val root = folder.newFolder("plugins")
        val installed = installPluginPackage(root, SamplePlugin.zipBytes())
        assertTrue(installed is PackageInstall.Ready)
        assertTrue(File(root, "echo.sample.hello/main.js").isFile)
    }

    @Test
    fun rejectsEntriesThatLeaveThePackageDirectory() {
        val root = folder.newFolder("plugins")
        val zip = zip(listOf("../evil.js" to "escaped", "echo-plugin.json" to SamplePlugin.Manifest.trimIndent()))
        val installed = installPluginPackage(root, zip)
        assertEquals(PluginRejectReason.UnsafePath, (installed as PackageInstall.Rejected).reason)
        assertFalse(File(folder.root, "evil.js").exists())
    }

    @Test
    fun rejectsMissingScript() {
        val root = folder.newFolder("plugins")
        val zip = zip(listOf("echo-plugin.json" to SamplePlugin.Manifest.trimIndent()))
        val installed = installPluginPackage(root, zip)
        assertEquals(PluginRejectReason.MissingEntry, (installed as PackageInstall.Rejected).reason)
    }

    private fun zip(entries: List<Pair<String, String>>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
