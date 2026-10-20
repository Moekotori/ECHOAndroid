package app.echo.android.data

import app.echo.android.model.library.CueSheetPolicy
import app.echo.android.model.library.LibrarySource

internal fun LibraryTrackEntity.canWriteEmbeddedTags(): Boolean =
    LibrarySource(source).isLocalAudioFile && !CueSheetPolicy.isCueTrackId(id) &&
        clipStartMs == 0L && clipEndMs == 0L
