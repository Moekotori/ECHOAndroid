package app.echo.android.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EchoErrorLogDao {
    @Query("SELECT * FROM error_records ORDER BY occurredAtEpochMs DESC, id DESC")
    fun observeAll(): Flow<List<EchoErrorLogEntity>>

    @Query("SELECT COUNT(*) FROM error_records")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM error_records ORDER BY occurredAtEpochMs DESC, id DESC LIMIT 1")
    suspend fun latest(): EchoErrorLogEntity?

    @Query("SELECT COUNT(*) FROM error_records")
    suspend fun count(): Int

    @Insert
    suspend fun insert(entity: EchoErrorLogEntity): Long

    @Update
    suspend fun update(entity: EchoErrorLogEntity)

    @Query("DELETE FROM error_records")
    suspend fun deleteAll()

    @Query("DELETE FROM error_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        """
        DELETE FROM error_records
        WHERE id IN (
            SELECT id FROM error_records
            ORDER BY occurredAtEpochMs ASC, id ASC
            LIMIT :overflow
        )
        """,
    )
    suspend fun deleteOldest(overflow: Int)
}
