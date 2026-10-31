package app.echo.android.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal object LibraryDuplicateMigration : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `library_duplicate_control` (`id` INTEGER NOT NULL, `generation` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `processed` INTEGER NOT NULL, `skipped` INTEGER NOT NULL, `groupCount` INTEGER NOT NULL, `hiddenCount` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `library_duplicate_members` (`generation` TEXT NOT NULL, `trackId` TEXT NOT NULL, `groupKey` TEXT NOT NULL, `versionKey` TEXT NOT NULL, `representativeId` TEXT NOT NULL, `snapshot` TEXT NOT NULL, `rank` INTEGER NOT NULL, `score` INTEGER NOT NULL, `sampleRateHz` INTEGER, `bitDepth` INTEGER, `bitrate` INTEGER, `codecMime` TEXT, PRIMARY KEY(`generation`,`trackId`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_library_duplicate_members_trackId` ON `library_duplicate_members` (`trackId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_library_duplicate_members_generation_groupKey` ON `library_duplicate_members` (`generation`,`groupKey`)")
    }
}
