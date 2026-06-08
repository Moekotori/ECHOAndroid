package app.echo.android.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EchoLibraryMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        EchoLibraryDatabase::class.java.canonicalName!!,
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate2To5_preservesTracksAndCreatesSearchSchema() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                """
                INSERT INTO library_tracks(
                    id, contentUri, title, artist, album, albumArtist, artworkUri,
                    durationMs, trackNumber, discNumber, year, mimeType, sizeBytes,
                    dateModifiedSeconds, source
                ) VALUES (
                    'track-1', 'content://track/1', 'Song One', 'Artist One', 'Album One',
                    'Album Artist', 'content://art/1', 120000, 1, 1, 2024, 'audio/flac',
                    1024, 1234, 'mediastore'
                )
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            5,
            true,
            EchoLibraryDatabase.Migration2To3,
            EchoLibraryDatabase.Migration3To4,
            EchoLibraryDatabase.Migration4To5,
        )

        migrated.query("SELECT COUNT(*) FROM library_tracks").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
        migrated.query("SELECT trackId FROM library_tracks_fts WHERE trackId = 'track-1'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("track-1", cursor.getString(0))
        }
        migrated.query("PRAGMA table_info(library_tracks)").use { cursor ->
            val columns = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                columns += cursor.getString(cursor.getColumnIndexOrThrow("name"))
            }
            assertNotNull(columns.firstOrNull { it == "lastSeenScanRunId" })
            assertNotNull(columns.firstOrNull { it == "fingerprint" })
            assertNotNull(columns.firstOrNull { it == "normalizedAlbumArtist" })
        }
        migrated.close()
    }

    private companion object {
        const val TEST_DB = "echo-library-migration-test"
    }
}
