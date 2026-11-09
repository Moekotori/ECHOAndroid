package app.echo.android.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal object LibraryCollectionPreferencesMigration : Migration(30, 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS library_liked_albums (albumKey TEXT NOT NULL PRIMARY KEY, likedAtEpochMs INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_library_liked_albums_likedAtEpochMs ON library_liked_albums (likedAtEpochMs)")
        db.execSQL("CREATE TABLE IF NOT EXISTS library_hidden_builtin_playlists (id TEXT NOT NULL PRIMARY KEY)")
    }
}
