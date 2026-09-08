package app.echo.android.data

import app.echo.android.model.library.LibrarySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryScanPolicyTest {
    private fun fingerprint(
        fingerprint: String? = "fp",
        sizeBytes: Long = 1_024L,
        dateModifiedSeconds: Long = 1_700_000_000L,
    ) = TrackFingerprint(
        id = "mediastore:1",
        contentUri = "content://media/1",
        sampleRateHz = 44_100,
        fingerprint = fingerprint,
        sizeBytes = sizeBytes,
        dateModifiedSeconds = dateModifiedSeconds,
    )

    @Test
    fun unchangedMediaStoreRowMatchesSnapshotSizeAndMtime() {
        assertTrue(
            LibraryScanPolicy.isMediaStoreRowUnchanged(
                existing = fingerprint(),
                dateModifiedSeconds = 1_700_000_000L,
                sizeBytes = 1_024L,
            ),
        )
    }

    @Test
    fun changedMtimeOrSizeIsNotUnchanged() {
        assertFalse(
            LibraryScanPolicy.isMediaStoreRowUnchanged(
                existing = fingerprint(),
                dateModifiedSeconds = 1_700_000_001L,
                sizeBytes = 1_024L,
            ),
        )
        assertFalse(
            LibraryScanPolicy.isMediaStoreRowUnchanged(
                existing = fingerprint(),
                dateModifiedSeconds = 1_700_000_000L,
                sizeBytes = 2_048L,
            ),
        )
    }

    @Test
    fun newRowOrMissingFingerprintIsNotUnchanged() {
        assertFalse(
            LibraryScanPolicy.isMediaStoreRowUnchanged(
                existing = null,
                dateModifiedSeconds = 1_700_000_000L,
                sizeBytes = 1_024L,
            ),
        )
        assertFalse(
            LibraryScanPolicy.isMediaStoreRowUnchanged(
                existing = fingerprint(fingerprint = null),
                dateModifiedSeconds = 1_700_000_000L,
                sizeBytes = 1_024L,
            ),
        )
    }

    @Test
    fun zeroedSnapshotFallsBackToFullFetch() {
        assertFalse(
            LibraryScanPolicy.isMediaStoreRowUnchanged(
                existing = fingerprint(sizeBytes = 0L, dateModifiedSeconds = 0L),
                dateModifiedSeconds = 0L,
                sizeBytes = 0L,
            ),
        )
    }

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
    fun allExplicitStorageFoldersUseDocumentTrees() {
        assertTrue(LibraryScanPolicy.usesDocumentTreeScan("primary"))
        assertTrue(LibraryScanPolicy.usesDocumentTreeScan("PRIMARY"))
        assertTrue(LibraryScanPolicy.usesDocumentTreeScan("1234-5678"))
    }

    @Test
    fun sdCardDocumentTreeKeepsRemovablePrefixAndRootScan() {
        assertEquals("primary" to "Music/Album", LibraryScanPolicy.splitDocumentTreeId("primary:Music/Album"))
        assertEquals("1D0C-1A0E" to "Music", LibraryScanPolicy.splitDocumentTreeId("1D0C-1A0E:Music"))
        assertEquals("1D0C-1A0E" to "", LibraryScanPolicy.splitDocumentTreeId("1D0C-1A0E:"))
        assertEquals("Music/", LibraryScanPolicy.documentTreeRelativePath("primary", "Music"))
        // SAF documentId 里的卷 UUID 是大写,MediaStore 卷名是小写:统一小写对齐
        assertEquals(
            "Removable/1d0c-1a0e/Music/",
            LibraryScanPolicy.documentTreeRelativePath("1D0C-1A0E", "Music"),
        )
        assertEquals(
            "Removable/1d0c-1a0e/",
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
            "Removable/1d0c-1a0e/Music/",
            LibraryScanPolicy.mediaStoreRelativePathForVolume("1D0C-1A0E", "Music/"),
        )
        assertEquals(
            "Removable/1d0c-1a0e/",
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
            "Removable/1d0c-1a0e/Music/",
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
    fun volumeScopeLimitsDeletionToScannedVolumes() {
        val primary = LibraryScanPolicy.mediaStoreVolumeScope("external_primary")
        val sdCard = LibraryScanPolicy.mediaStoreVolumeScope("1d0c-1a0e")
        val merged = LibraryScanPolicy.mediaStoreVolumeScope("external")
        val legacy = LibraryScanPolicy.mediaStoreVolumeScope(null)

        assertEquals(MediaStoreVolumeScope.PrimaryVolume, primary)
        assertEquals(MediaStoreVolumeScope.RemovableVolume("Removable/1d0c-1a0e/"), sdCard)
        assertEquals(MediaStoreVolumeScope.AllVolumes, merged)
        assertEquals(MediaStoreVolumeScope.AllVolumes, legacy)

        // 只扫了主卷:SD 卡的行不允许被当作缺失删除
        assertTrue(LibraryScanPolicy.mediaStoreRowWithinVolumeScopes("Music/", listOf(primary)))
        assertTrue(LibraryScanPolicy.mediaStoreRowWithinVolumeScopes(null, listOf(primary)))
        assertFalse(
            LibraryScanPolicy.mediaStoreRowWithinVolumeScopes(
                "Removable/1d0c-1a0e/Music/",
                listOf(primary),
            ),
        )
        // 历史行可能带大写卷名:归属判断不区分大小写
        assertTrue(
            LibraryScanPolicy.mediaStoreRowWithinVolumeScopes(
                "Removable/1D0C-1A0E/Music/",
                listOf(sdCard),
            ),
        )
        assertFalse(LibraryScanPolicy.mediaStoreRowWithinVolumeScopes("Music/", listOf(sdCard)))
        assertTrue(
            LibraryScanPolicy.mediaStoreRowWithinVolumeScopes(
                "Removable/1d0c-1a0e/Music/",
                listOf(merged),
            ),
        )
        // 没有任何完整扫过的卷:什么都不能删
        assertFalse(LibraryScanPolicy.mediaStoreRowWithinVolumeScopes("Music/", emptyList()))
    }

    @Test
    fun duplicateIdentityRequiresFilenameAndPreservesCase() {
        val first = LibraryScanPolicy.localFileDuplicateKey("Music/", 1024L, 99L, "first.wav")
        assertTrue(first != LibraryScanPolicy.localFileDuplicateKey("Music/", 1024L, 99L, "second.wav"))
        assertTrue(first != LibraryScanPolicy.localFileDuplicateKey("Music/", 1024L, 99L, "FIRST.wav"))
        assertTrue(first != LibraryScanPolicy.localFileDuplicateKey("music/", 1024L, 99L, "first.wav"))
        assertEquals(first, LibraryScanPolicy.localFileDuplicateKey("Music", 1024L, 99L, "first.wav"))
        assertNull(LibraryScanPolicy.localFileDuplicateKey("Music/", 1024L, 99L))
        assertNull(LibraryScanPolicy.localFileDuplicateKey("Music/", 0L, 99L, "first.wav"))
        assertNull(LibraryScanPolicy.localFileDuplicateKey(null, 1024L, 99L, "first.wav"))
    }

    @Test
    fun confirmedEmptyFolderDeletesButFailedTraversalNeverDoes() {
        val empty = LibraryScanCompleteness(true, 0, 12, confirmedEmpty = true)
        assertTrue(LibraryScanPolicy.shouldDeleteMissingLibraryRows(empty))
        assertFalse(LibraryScanPolicy.shouldDeleteMissingLibraryRows(empty.copy(querySucceeded = false)))
        assertFalse(LibraryScanPolicy.shouldDeleteMissingLibraryRows(empty.copy(hitVisitCap = true)))
    }

    @Test
    fun changedRelativePathIsUpdateNotFingerprintReuse() {
        // 指纹包含 relativePath:路径归一化(卷名大小写迁移)后必须重写行
        assertFalse(
            LibraryScanPolicy.shouldReuseUnchangedDocumentFingerprint(
                existingContentUri = "content://tree/doc/1",
                incomingContentUri = "content://tree/doc/1",
                existingSizeBytes = 1_024L,
                incomingSizeBytes = 1_024L,
                existingDateModifiedSeconds = 99L,
                incomingDateModifiedSeconds = 99L,
                existingRelativePath = "Removable/1D0C-1A0E/Music/",
                incomingRelativePath = "Removable/1d0c-1a0e/Music/",
            ),
        )
        assertTrue(
            LibraryScanPolicy.shouldReuseUnchangedDocumentFingerprint(
                existingContentUri = "content://tree/doc/1",
                incomingContentUri = "content://tree/doc/1",
                existingSizeBytes = 1_024L,
                incomingSizeBytes = 1_024L,
                existingDateModifiedSeconds = 99L,
                incomingDateModifiedSeconds = 99L,
                existingRelativePath = "Removable/1d0c-1a0e/Music/",
                incomingRelativePath = "Removable/1d0c-1a0e/Music/",
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
    fun albumArtistColumnIsAvailableFromAndroid11() {
        assertFalse(LibraryScanPolicy.mediaStoreAlbumArtistColumnAvailable(26))
        assertFalse(LibraryScanPolicy.mediaStoreAlbumArtistColumnAvailable(29))
        assertTrue(LibraryScanPolicy.mediaStoreAlbumArtistColumnAvailable(30))
        assertTrue(LibraryScanPolicy.mediaStoreAlbumArtistColumnAvailable(36))
    }

    @Test
    fun unsupportedSampleRateColumnDetectsProviderError() {
        assertTrue(
            LibraryScanPolicy.isUnsupportedMediaStoreSampleRateColumn(
                IllegalArgumentException("Invalid column sample_rate"),
            ),
        )
        assertFalse(
            LibraryScanPolicy.isUnsupportedMediaStoreSampleRateColumn(
                IllegalArgumentException("Invalid column album_artist"),
            ),
        )
        assertFalse(
            LibraryScanPolicy.isUnsupportedMediaStoreSampleRateColumn(
                RuntimeException("Invalid column sample_rate"),
            ),
        )
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
