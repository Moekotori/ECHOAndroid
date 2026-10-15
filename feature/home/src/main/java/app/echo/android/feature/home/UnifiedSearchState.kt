package app.echo.android.feature.home

import app.echo.android.model.library.EchoUnifiedSearch

enum class UnifiedSearchCategory { All, Library, Pc }
enum class UnifiedSearchPhase { Idle, Loading, Ready, Failed, Disconnected }

/** Each source owns its results and loading lifetime; UI never waits for the other source. */
data class UnifiedSearchSourceState(
    val phase: UnifiedSearchPhase = UnifiedSearchPhase.Idle,
    val results: EchoUnifiedSearch = EchoUnifiedSearch(),
)
