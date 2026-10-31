package app.echo.android.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal object LibraryMusicRelationMigration : Migration(25, 26) {
    internal const val CreateTable = "CREATE TABLE IF NOT EXISTS `library_music_relations` (`trackId` TEXT NOT NULL, `kind` TEXT NOT NULL, `entityKey` TEXT NOT NULL, `name` TEXT NOT NULL, `context` TEXT NOT NULL, PRIMARY KEY(`trackId`, `kind`, `entityKey`), FOREIGN KEY(`trackId`) REFERENCES `library_tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
    internal const val CreateIndex = "CREATE INDEX IF NOT EXISTS `index_library_music_relations_kind_entityKey_trackId` ON `library_music_relations` (`kind`, `entityKey`, `trackId`)"
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE library_tracks ADD COLUMN musicRelationsJson TEXT DEFAULT NULL")
        db.execSQL(CreateTable)
        db.execSQL(CreateIndex)
        // The view's SELECT * expands to the new column when Room validates it.
    }
}
