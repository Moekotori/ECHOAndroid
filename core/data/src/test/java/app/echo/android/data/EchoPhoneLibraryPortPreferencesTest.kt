package app.echo.android.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import app.echo.android.model.connect.EchoLinkSharingPort
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class EchoPhoneLibraryPortPreferencesTest {
    @Test fun fixedPortSurvivesClosingAndReopeningThePreferenceStore() = runBlocking {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
        val scratch = File(root, "misc").apply { mkdirs() }
        val directory = kotlin.io.path.createTempDirectory(scratch.toPath(), "phone-library-port-").toFile()
        val file = File(directory, "sharing.preferences_pb")
        val firstJob = Job()
        val secondJob = Job()
        try {
            val first = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + firstJob), produceFile = { file })
            assertEquals(EchoLinkSharingPort.DefaultPhone, readEchoPhoneLibraryPort(first.data.first()))
            first.edit { writeEchoPhoneLibraryPort(it, 31234) }
            for (invalid in listOf(0, -1, 65536)) {
                try { first.edit { writeEchoPhoneLibraryPort(it, invalid) }; fail("Invalid port accepted") }
                catch (_: IllegalArgumentException) { }
            }
            firstJob.cancelAndJoin()
            val reopened = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + secondJob), produceFile = { file })
            assertEquals(31234, readEchoPhoneLibraryPort(reopened.data.first()))
        } finally {
            firstJob.cancelAndJoin(); secondJob.cancelAndJoin()
            check(directory.canonicalFile.toPath().startsWith(scratch.canonicalFile.toPath()))
            directory.deleteRecursively()
        }
    }
}
