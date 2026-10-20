package app.echo.android

import app.echo.android.data.EmbeddedTagWriteResult
import app.echo.android.data.TrackMetadataUpdateResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class EmbeddedTagWriteAuthorizationTest {
    @Test fun grantedConsentRetriesTheSameItemBeforeBatchContinues() = runBlocking {
        var writes = 0
        var requests = 0
        val result = writeTagsWithAuthorization(
            write = { TrackMetadataUpdateResult(true, if (++writes == 1)
                EmbeddedTagWriteResult.NeedsMediaStoreConsent("content://media/1") else EmbeddedTagWriteResult.Written(100, 1)) },
            authorize = { requests++; true },
        )
        assertTrue(result.fileWrite is EmbeddedTagWriteResult.Written)
        assertEquals(2, writes)
        assertEquals(1, requests)
    }

    @Test fun deniedPermissionDoesNotRetryAndRepeatedFailureDoesNotLoop() = runBlocking {
        var writes = 0
        var requests = 0
        val write: suspend () -> TrackMetadataUpdateResult = {
            writes++; TrackMetadataUpdateResult(true, EmbeddedTagWriteResult.NeedsStoragePermission)
        }
        writeTagsWithAuthorization(write, { requests++; false })
        assertEquals(1, writes)
        assertEquals(1, requests)
        writes = 0; requests = 0
        writeTagsWithAuthorization(write, { requests++; true })
        assertEquals(2, writes)
        assertEquals(1, requests)
    }
    @Test fun grantedMediaConsentThatStillCannotWriteStopsAfterOneRequest() = runBlocking {
        var writes = 0
        var requests = 0
        val result = writeTagsWithAuthorization(
            write = { writes++; TrackMetadataUpdateResult(true,
                EmbeddedTagWriteResult.NeedsMediaStoreConsent("content://media/1")) },
            authorize = { requests++; true },
        )
        assertEquals(2, writes)
        assertEquals(1, requests)
        assertTrue(result.fileWrite is EmbeddedTagWriteResult.NeedsMediaStoreConsent)
    }

}
