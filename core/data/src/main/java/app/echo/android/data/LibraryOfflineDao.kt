package app.echo.android.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryOfflineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPin(pin: LibraryOfflinePinEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFiles(files: List<LibraryOfflineFileEntity>)

    @Query("SELECT * FROM library_offline_pins WHERE id = :pinId LIMIT 1")
    suspend fun getPin(pinId: String): LibraryOfflinePinEntity?

    @Query("SELECT * FROM library_offline_pins WHERE id = :pinId LIMIT 1")
    fun observePin(pinId: String): Flow<LibraryOfflinePinEntity?>

    @Query("SELECT * FROM library_offline_pins ORDER BY createdAtEpochMs DESC")
    fun observePins(): Flow<List<LibraryOfflinePinEntity>>

    @Query("SELECT * FROM library_offline_files WHERE pinId = :pinId")
    suspend fun filesForPin(pinId: String): List<LibraryOfflineFileEntity>

    @Query("SELECT * FROM library_offline_files WHERE pinId = :pinId")
    fun observeFilesForPin(pinId: String): Flow<List<LibraryOfflineFileEntity>>

    @Query("SELECT * FROM library_offline_files")
    fun observeFiles(): Flow<List<LibraryOfflineFileEntity>>

    @Query("SELECT * FROM library_offline_files WHERE status = :status LIMIT :limit")
    suspend fun filesWithStatus(status: String, limit: Int): List<LibraryOfflineFileEntity>

    @Query(
        """
        SELECT trackId, localPath FROM library_offline_files
        WHERE status = 'ready' AND localPath IS NOT NULL AND bytes > 0
        """,
    )
    suspend fun readyFiles(): List<LibraryOfflineFileReady>

    @Query("SELECT localPath FROM library_offline_files WHERE trackId = :trackId AND status = 'ready' LIMIT 1")
    suspend fun readyPath(trackId: String): String?

    @Query("SELECT pinId FROM library_offline_files WHERE trackId = :trackId LIMIT 1")
    suspend fun pinIdForTrack(trackId: String): String?

    @Query("SELECT COALESCE(SUM(bytes), 0) FROM library_offline_files WHERE status = 'ready'")
    suspend fun readyBytes(): Long

    @Query("SELECT COUNT(*) FROM library_offline_files WHERE pinId = :pinId AND status = 'ready'")
    suspend fun readyCount(pinId: String): Int

    @Query("SELECT COUNT(*) FROM library_offline_files WHERE pinId = :pinId AND status = 'failed'")
    suspend fun failedCount(pinId: String): Int

    @Query("SELECT COUNT(*) FROM library_offline_files WHERE pinId = :pinId AND status = 'downloading'")
    suspend fun downloadingCount(pinId: String): Int

    @Query("SELECT COALESCE(SUM(bytes), 0) FROM library_offline_files WHERE pinId = :pinId AND status = 'ready'")
    suspend fun readyBytesForPin(pinId: String): Long

    @Query("UPDATE library_offline_files SET status = :status, error = :error WHERE trackId = :trackId")
    suspend fun setFileStatus(trackId: String, status: String, error: String?)

    @Query(
        """
        UPDATE library_offline_files
        SET status = :status, localPath = :localPath, bytes = :bytes, error = NULL
        WHERE trackId = :trackId
        """,
    )
    suspend fun setFileReady(trackId: String, localPath: String, bytes: Long, status: String)

    @Query("UPDATE library_offline_pins SET status = :status, error = :error WHERE id = :pinId")
    suspend fun setPinStatus(pinId: String, status: String, error: String?)

    @Query("DELETE FROM library_offline_files WHERE pinId = :pinId")
    suspend fun deleteFilesForPin(pinId: String)

    @Query("DELETE FROM library_offline_pins WHERE id = :pinId")
    suspend fun deletePin(pinId: String)

    @Query("SELECT localPath FROM library_offline_files WHERE pinId = :pinId AND localPath IS NOT NULL")
    suspend fun localPathsForPin(pinId: String): List<String>

    @Transaction
    suspend fun replacePin(pin: LibraryOfflinePinEntity, files: List<LibraryOfflineFileEntity>) {
        deleteFilesForPin(pin.id)
        upsertPin(pin)
        if (files.isNotEmpty()) upsertFiles(files)
    }
}
