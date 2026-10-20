package app.echo.android.feature.library

import app.echo.android.model.library.CueSheetPolicy
import app.echo.android.model.library.EchoTrack

internal fun EchoTrack.canEditFileTags(): Boolean =
    source.isLocalAudioFile && !CueSheetPolicy.isCueTrackId(id) && clipStartMs == 0L && clipEndMs == 0L
