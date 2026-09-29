package app.echo.android.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一次有效收听。标题等字段是写入时的快照，曲目被重扫或删除后统计仍然成立。
 * localEpochDay / localHour 按收听当时的时区计算，旅行或改时区不会让旧记录漂移。
 */
@Entity(
    tableName = "library_play_events",
    indices = [
        Index(value = ["playedAtEpochMs"]),
        Index(value = ["localEpochDay"]),
        Index(value = ["trackId"]),
    ],
)
data class LibraryPlayEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val artworkUri: String?,
    val source: String?,
    val durationMs: Long,
    val listenedMs: Long,
    val playedAtEpochMs: Long,
    val localEpochDay: Long,
    val localHour: Int,
)
