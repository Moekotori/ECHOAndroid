package app.echo.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Shares only the decoded snapshot, so first() still reads the current DataStore value. */
internal fun <Input, Revision, Output> Flow<Input>.mapSettingsSnapshot(
    revision: (Input) -> Revision,
    decode: (Input) -> Output,
): Flow<Output> {
    val lock = Mutex()
    var cached: DecodedSettingsSnapshot<Input, Revision, Output>? = null
    return map { input ->
        lock.withLock {
            val currentRevision = revision(input)
            val previous = cached
            if (previous != null && previous.input == input && previous.revision == currentRevision) {
                previous.output
            } else {
                val output = decode(input)
                // Legacy secret migration can change the revision during decoding.
                cached = if (revision(input) == currentRevision) {
                    DecodedSettingsSnapshot(input, currentRevision, output)
                } else null
                output
            }
        }
    }.flowOn(Dispatchers.IO)
}

private data class DecodedSettingsSnapshot<Input, Revision, Output>(
    val input: Input,
    val revision: Revision,
    val output: Output,
)
