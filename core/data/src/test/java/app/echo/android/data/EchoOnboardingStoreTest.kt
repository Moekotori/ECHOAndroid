package app.echo.android.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EchoOnboardingStoreTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun freshInstallShowsGuideAndCompletionSurvivesReopening() = runBlocking {
        val file = temporaryFolder.root.resolve("onboarding.preferences_pb")
        val firstJob = SupervisorJob()
        val first = EchoOnboardingStore(PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + firstJob), produceFile = { file },
        ))
        try {
            assertFalse(first.completed.first())
            first.complete()
            // Repeated completion (including Skip) must remain safe.
            first.complete()
            assertTrue(first.completed.first())
        } finally {
            firstJob.cancelAndJoin()
        }

        val secondJob = SupervisorJob()
        val reopened = EchoOnboardingStore(PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + secondJob), produceFile = { file },
        ))
        try {
            assertTrue(reopened.completed.first())
        } finally {
            secondJob.cancelAndJoin()
        }
    }
}
