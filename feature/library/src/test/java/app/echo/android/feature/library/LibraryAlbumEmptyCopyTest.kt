package app.echo.android.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryAlbumEmptyCopyTest {
    @Test
    fun cloudWithoutSetupGuidesToConnect() {
        val copy = libraryAlbumEmptyCopy(isCloud = true, cloudConfigured = false, hasQuery = false)
        assertEquals(R.string.library_cloud_empty_title, copy.titleRes)
        assertEquals(R.string.library_cloud_empty_detail, copy.detailRes)
        assertEquals(R.string.library_open_connect, copy.actionRes)
    }

    @Test
    fun cloudConfiguredEmptyGuidesToSync() {
        val copy = libraryAlbumEmptyCopy(isCloud = true, cloudConfigured = true, hasQuery = false)
        assertEquals(R.string.library_cloud_empty_configured_title, copy.titleRes)
        assertEquals(R.string.library_cloud_empty_configured_detail, copy.detailRes)
        assertEquals(R.string.library_open_connect, copy.actionRes)
    }

    @Test
    fun searchTakesPriorityOverCloudSetup() {
        val copy = libraryAlbumEmptyCopy(isCloud = true, cloudConfigured = false, hasQuery = true)
        assertEquals(R.string.library_no_matches, copy.titleRes)
        assertEquals(R.string.library_search_hint, copy.detailRes)
        assertEquals(R.string.library_clear_search, copy.actionRes)
    }

    @Test
    fun localEmptyGuidesToImport() {
        val copy = libraryAlbumEmptyCopy(isCloud = false, cloudConfigured = false, hasQuery = false)
        assertEquals(R.string.library_start_collection, copy.titleRes)
        assertEquals(R.string.library_import_hint, copy.detailRes)
        assertEquals(R.string.library_add_music, copy.actionRes)
    }
}
