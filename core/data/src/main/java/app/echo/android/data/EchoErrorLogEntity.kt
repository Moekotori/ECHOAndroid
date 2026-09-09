package app.echo.android.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.echo.android.model.error.EchoErrorRecord
import app.echo.android.model.error.EchoErrorSource

@Entity(
    tableName = "error_records",
    indices = [
        Index(value = ["occurredAtEpochMs"]),
        Index(value = ["source"]),
    ],
)
data class EchoErrorLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val occurredAtEpochMs: Long,
    val source: String,
    val summary: String,
    val detail: String?,
    val stackTrace: String?,
    val count: Int,
    val threadName: String? = null,
    val appVersion: String? = null,
    val throwableName: String? = null,
    val firstOccurredAtEpochMs: Long = occurredAtEpochMs,
)

fun EchoErrorLogEntity.toRecord(): EchoErrorRecord =
    EchoErrorRecord(
        id = id,
        occurredAtEpochMs = occurredAtEpochMs,
        source = EchoErrorSource.fromId(source),
        summary = summary,
        detail = detail,
        stackTrace = stackTrace,
        count = count,
        threadName = threadName,
        appVersion = appVersion,
        throwableName = throwableName,
        firstOccurredAtEpochMs = firstOccurredAtEpochMs.takeIf { it > 0L } ?: occurredAtEpochMs,
    )
