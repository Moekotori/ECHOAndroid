package app.echo.android.data

import app.echo.android.model.library.LibrarySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun permissionGrantScansWhenLocalMediaStoreIsEmpty() {
        assertTrue(LibraryScanPolicy.shouldRefreshLocalLibraryAfterPermissionGrant(localMediaStoreCount = 0))
        assertFalse(LibraryScanPolicy.shouldRefreshLocalLibraryAfterPermissionGrant(localMediaStoreCount = 12))
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
    fun sdCardDocumentTreeKeepsRemovablePrefixAndRootScan() {
        assertEquals("primary" to "Music/Album", LibraryScanPolicy.splitDocumentTreeId("primary:Music/Album"))
        assertEquals("1D0C-1A0E" to "Music", LibraryScanPolicy.splitDocumentTreeId("1D0C-1A0E:Music"))
        assertEquals("1D0C-1A0E" to "", LibraryScanPolicy.splitDocumentTreeId("1D0C-1A0E:"))
        assertEquals("Music/", LibraryScanPolicy.documentTreeRelativePath("primary", "Music"))
        assertEquals(
            "Removable/1D0C-1A0E/Music/",
            LibraryScanPolicy.documentTreeRelativePath("1D0C-1A0E", "Music"),
        )
        assertEquals(
            "Removable/1D0C-1A0E/",
            LibraryScanPolicy.documentTreeRelativePath("1D0C-1A0E", ""),
        )
        assertNull(LibraryScanPolicy.documentTreeRelativePath("primary", ""))
    }

    @Test
    fun mediaStoreRelativePathDoesNotCollapseSdCardIntoPrimaryMusic() {
        assertEquals(
            "Music/",
            LibraryScanPolicy.mediaStoreRelativePathForVolume("external_primary", "Music/"),
        )
        assertEquals(
            "Removable/1D0C-1A0E/Music/",
            LibraryScanPolicy.mediaStoreRelativePathForVolume("1D0C-1A0E", "Music/"),
        )
        assertEquals(
            "Removable/1D0C-1A0E/",
            LibraryScanPolicy.mediaStoreRelativePathForVolume("1D0C-1A0E", null),
        )
        assertEquals(
            "1D0C-1A0E",
            LibraryScanPolicy.resolvedMediaStoreVolumeName(
                collectionVolumeName = "external",
                rowVolumeName = "1D0C-1A0E",
            ),
        )
        assertEquals(
            "1D0C-1A0E",
            LibraryScanPolicy.resolvedMediaStoreVolumeName(
                collectionVolumeName = "1D0C-1A0E",
                rowVolumeName = "external_primary",
            ),
        )
        assertTrue(LibraryScanPolicy.shouldScanAllMediaStoreVolumes(29, relativePathPrefix = null))
        assertFalse(LibraryScanPolicy.shouldScanAllMediaStoreVolumes(29, relativePathPrefix = "Music/"))
        assertFalse(LibraryScanPolicy.shouldScanAllMediaStoreVolumes(28, relativePathPrefix = null))
    }

    @Test
    fun legacySdCardDataPathIsNotStrippedAsInternalStorage() {
        assertEquals(
            "Music/",
            LibraryScanPolicy.legacyDataRelativePath(
                dataPath = "/storage/emulated/0/Music/song.flac",
                primaryStorageRoot = "/storage/emulated/0",
            ),
        )
        assertEquals(
            "Removable/1D0C-1A0E/Music/",
            LibraryScanPolicy.legacyDataRelativePath(
                dataPath = "/storage/1D0C-1A0E/Music/song.flac",
                primaryStorageRoot = "/storage/emulated/0",
            ),
        )
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
    fun sampleRateColumnIsAvailableFromAndroid12() {
        assertFalse(LibraryScanPolicy.mediaStoreSampleRateColumnAvailable(30))
        assertTrue(LibraryScanPolicy.mediaStoreSampleRateColumnAvailable(31))
        assertTrue(LibraryScanPolicy.mediaStoreSampleRateColumnAvailable(36))
    }

    @Test
    fun mediaStoreSampleRateWinsOverStoredRate() {
        assertEquals(96_000, LibraryScanPolicy.preferredSampleRateHz(96_000, 48_000))
        assertEquals(48_000, LibraryScanPolicy.preferredSampleRateHz(null, 48_000))
        assertEquals(48_000, LibraryScanPolicy.preferredSampleRateHz(0, 48_000))
        assertEquals(null, LibraryScanPolicy.preferredSampleRateHz(null, 0))
    }

    @Test
    fun sampleRateFileReadIsSkippedWhenRateIsAlreadyKnown() {
        assertFalse(LibraryScanPolicy.shouldReadSampleRateFromFile(readSampleRateEnabled = true, knownSampleRateHz = 48_000))
        assertTrue(LibraryScanPolicy.shouldReadSampleRateFromFile(readSampleRateEnabled = true, knownSampleRateHz = null))
        assertTrue(LibraryScanPolicy.shouldReadSampleRateFromFile(readSampleRateEnabled = true, knownSampleRateHz = 0))
        assertFalse(LibraryScanPolicy.shouldReadSampleRateFromFile(readSampleRateEnabled = false, knownSampleRateHz = null))
    }

    @Test
    fun sampleRateReadIsSkippedWhenLightweightOrStorageIsBusy() {
        assertTrue(LibraryScanPolicy.shouldSkipSampleRateRead(lightweight = true, storageBusy = false))
        assertTrue(LibraryScanPolicy.shouldSkipSampleRateRead(lightweight = false, storageBusy = true))
        assertFalse(LibraryScanPolicy.shouldSkipSampleRateRead(lightweight = false, storageBusy = false))
    }

    @Test
    fun sampleRateBackfillRunsOnlyWhenLeavingLightweight() {
        assertTrue(
            LibraryScanPolicy.shouldBackfillMissingSampleRates(
                wasLightweight = true,
                isLightweight = false,
            ),
        )
        assertFalse(
            LibraryScanPolicy.shouldBackfillMissingSampleRates(
                wasLightweight = false,
                isLightweight = false,
            ),
        )
        assertFalse(
            LibraryScanPolicy.shouldBackfillMissingSampleRates(
                wasLightweight = true,
                isLightweight = true,
            ),
        )
        assertFalse(
            LibraryScanPolicy.shouldBackfillMissingSampleRates(
                wasLightweight = false,
                isLightweight = true,
            ),
        )
    }

    @Test
    fun scanProgressEmitsFirstTrackThenStrideOrInterval() {
        assertFalse(
            LibraryScanPolicy.shouldEmitScanProgress(
                scannedCount = 0,
                lastEmittedCount = 0,
                elapsedSinceEmitMs = 1_000L,
            ),
        )
        assertTrue(
            LibraryScanPolicy.shouldEmitScanProgress(
                scannedCount = 1,
                lastEmittedCount = 0,
                elapsedSinceEmitMs = 0L,
            ),
        )
        assertFalse(
            LibraryScanPolicy.shouldEmitScanProgress(
                scannedCount = 40,
                lastEmittedCount = 1,
                elapsedSinceEmitMs = 100L,
            ),
        )
        assertTrue(
            LibraryScanPolicy.shouldEmitScanProgress(
                scannedCount = 40,
                lastEmittedCount = 1,
                elapsedSinceEmitMs = 400L,
            ),
        )
        assertTrue(
            LibraryScanPolicy.shouldEmitScanProgress(
                scannedCount = 101,
                lastEmittedCount = 1,
                elapsedSinceEmitMs = 0L,
            ),
        )
        assertFalse(
            LibraryScanPolicy.shouldEmitScanProgress(
                scannedCount = 101,
                lastEmittedCount = 101,
                elapsedSinceEmitMs = 1_000L,
            ),
        )
    }

    @Test
    fun summaryRebuildStaysIncrementalForSmallRescans() {
        assertFalse(
            LibraryScanPolicy.shouldRebuildLibrarySummariesIncrementally(
                changedKeyCount = 12,
                existingAlbumSummaryCount = 0,
            ),
        )
        assertTrue(
            LibraryScanPolicy.shouldRebuildLibrarySummariesIncrementally(
                changedKeyCount = 12,
                existingAlbumSummaryCount = 800,
            ),
        )
        assertFalse(
            LibraryScanPolicy.shouldRebuildLibrarySummariesIncrementally(
                changedKeyCount = 500,
                existingAlbumSummaryCount = 2_000,
            ),
        )
        assertFalse(
            LibraryScanPolicy.shouldRebuildLibrarySummariesIncrementally(
                changedKeyCount = 300,
                existingAlbumSummaryCount = 400,
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
