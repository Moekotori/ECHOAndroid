package app.echo.android.data

import app.echo.android.model.library.LibrarySource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryScanPolicyTest {
    @Test
    fun emptyScanWithExistingRowsDoesNotDelete() {
        assertFalse(
            LibraryScanPolicy.shouldDeleteMissingLibraryRows(
                LibraryScanCompleteness(
                    querySucceeded = true,
                    scannedCount = 0,
                    existingCount = 12,
                ),
            ),
        )
    }

    @Test
    fun failedQueryDoesNotDelete() {
        assertFalse(
            LibraryScanPolicy.shouldDeleteMissingLibraryRows(
                LibraryScanCompleteness(
                    querySucceeded = false,
                    scannedCount = 0,
                    existingCount = 4,
                ),
            ),
        )
    }

    @Test
    fun cappedSyncDoesNotDelete() {
        assertFalse(
            LibraryScanPolicy.shouldDeleteMissingLibraryRows(
                LibraryScanCompleteness(
                    querySucceeded = true,
                    scannedCount = 5_000,
                    existingCount = 8_000,
                    hitVisitCap = true,
                ),
            ),
        )
    }

    @Test
    fun completeScanMayDeleteMissingRows() {
        assertTrue(
            LibraryScanPolicy.shouldDeleteMissingLibraryRows(
                LibraryScanCompleteness(
                    querySucceeded = true,
                    scannedCount = 10,
                    existingCount = 12,
                ),
            ),
        )
    }

    @Test
    fun firstScanOfEmptyLibraryMayCompleteWithoutDelete() {
        assertTrue(
            LibraryScanPolicy.shouldDeleteMissingLibraryRows(
                LibraryScanCompleteness(
                    querySucceeded = true,
                    scannedCount = 0,
                    existingCount = 0,
                ),
            ),
        )
    }

    @Test
    fun safIdsAreExcludedFromFullMediaStoreCleanup() {
        assertTrue(LibraryScanPolicy.shouldDeleteOnFullMediaStoreCleanup("mediastore:42"))
        assertFalse(LibraryScanPolicy.shouldDeleteOnFullMediaStoreCleanup("saf:primary%3AMusic"))
        assertTrue(LibraryScanPolicy.shouldDeleteOnDocumentTreeCleanup("saf:primary%3AMusic"))
        assertFalse(LibraryScanPolicy.shouldDeleteOnDocumentTreeCleanup("mediastore:42"))
    }

    @Test
    fun localSourceIncludesMediaStoreAndSafButNotSubsonic() {
        assertTrue(LibraryScanPolicy.isLocalLibrarySource(LibrarySource.MediaStore.id))
        assertTrue(LibraryScanPolicy.isLocalLibrarySource(LibraryScanPolicy.SafSourceId))
        assertFalse(LibraryScanPolicy.isLocalLibrarySource("${LibrarySource.Subsonic.id}:abc"))
        assertFalse(LibraryScanPolicy.isLocalLibrarySource("${LibrarySource.WebDav.id}:xyz"))
        assertTrue(LibraryScanPolicy.isRemoteLibrarySource("${LibrarySource.Subsonic.id}:abc"))
    }

    @Test
    fun editedMetadataIsPreservedWhenFingerprintDiffers() {
        assertTrue(
            LibraryScanPolicy.shouldPreserveUserMetadata(
                incomingFingerprint = "raw",
                existingFingerprint = "edited",
                metadataEditedAtEpochMs = 1L,
            ),
        )
        assertFalse(
            LibraryScanPolicy.shouldPreserveUserMetadata(
                incomingFingerprint = "raw",
                existingFingerprint = "raw",
                metadataEditedAtEpochMs = 1L,
            ),
        )
        assertFalse(
            LibraryScanPolicy.shouldPreserveUserMetadata(
                incomingFingerprint = "raw",
                existingFingerprint = "edited",
                metadataEditedAtEpochMs = null,
            ),
        )
    }
}
