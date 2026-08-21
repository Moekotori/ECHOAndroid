package app.echo.android.data

import app.echo.android.model.library.LibrarySource
import org.junit.Assert.assertEquals
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
    fun unchangedScanRowsAreRememberedWithoutRowUpdate() {
        assertEquals(
            LibraryScanRowAction.RememberSeen,
            LibraryScanPolicy.scanRowAction(
                existingFingerprint = "same",
                incomingFingerprint = "same",
            ),
        )
        assertFalse(LibraryScanPolicy.shouldStampLastSeenOnUnchangedRow())
        assertEquals(
            LibraryScanRowAction.Insert,
            LibraryScanPolicy.scanRowAction(
                existingFingerprint = null,
                incomingFingerprint = "new",
            ),
        )
        assertEquals(
            LibraryScanRowAction.Update,
            LibraryScanPolicy.scanRowAction(
                existingFingerprint = "old",
                incomingFingerprint = "new",
            ),
        )
        assertEquals(
            listOf("gone"),
            LibraryScanPolicy.unseenIds(
                existingIds = listOf("kept", "gone"),
                seenIds = setOf("kept", "inserted"),
            ),
        )
    }

    @Test
    fun primaryStorageFolderUsesMediaStorePrefixNotSaf() {
        assertFalse(LibraryScanPolicy.usesDocumentTreeScan("primary"))
        assertFalse(LibraryScanPolicy.usesDocumentTreeScan("PRIMARY"))
        assertTrue(LibraryScanPolicy.usesDocumentTreeScan("1234-5678"))
    }

    @Test
    fun failedDirectoryListingDoesNotDeleteEvenIfOtherFilesScanned() {
        assertFalse(
            LibraryScanPolicy.shouldDeleteMissingLibraryRows(
                LibraryScanCompleteness(
                    querySucceeded = false,
                    scannedCount = 20,
                    existingCount = 40,
                ),
            ),
        )
    }

    @Test
    fun sizeAndMtimeMatchWithNewDocumentUriIsUpdateNotRememberSeen() {
        assertFalse(
            LibraryScanPolicy.shouldReuseUnchangedDocumentFingerprint(
                existingContentUri = "content://com.android.externalstorage.documents/tree/OLD/document/1",
                incomingContentUri = "content://com.android.externalstorage.documents/tree/NEW/document/1",
                existingSizeBytes = 1_024L,
                incomingSizeBytes = 1_024L,
                existingDateModifiedSeconds = 99L,
                incomingDateModifiedSeconds = 99L,
            ),
        )
        assertTrue(
            LibraryScanPolicy.shouldReuseUnchangedDocumentFingerprint(
                existingContentUri = "content://com.android.externalstorage.documents/tree/NEW/document/1",
                incomingContentUri = "content://com.android.externalstorage.documents/tree/NEW/document/1",
                existingSizeBytes = 1_024L,
                incomingSizeBytes = 1_024L,
                existingDateModifiedSeconds = 99L,
                incomingDateModifiedSeconds = 99L,
            ),
        )
        assertEquals(
            LibraryScanRowAction.Update,
            LibraryScanPolicy.scanRowAction(
                existingFingerprint = "old-uri|1024|99",
                incomingFingerprint = "new-uri|1024|99",
            ),
        )
        assertEquals(
            LibraryScanRowAction.RememberSeen,
            LibraryScanPolicy.scanRowAction(
                existingFingerprint = "same-uri|1024|99",
                incomingFingerprint = "same-uri|1024|99",
            ),
        )
    }

    @Test
    fun editedMetadataIsPreservedOnMismatch() {
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
