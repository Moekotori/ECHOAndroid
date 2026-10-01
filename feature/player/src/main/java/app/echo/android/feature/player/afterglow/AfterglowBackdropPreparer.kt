package app.echo.android.feature.player.afterglow

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One preparation lane per stage. Rapid colour/scene changes cancel queued and obsolete work. */
internal class AfterglowBackdropPreparer {
    private val lane = Mutex()
    suspend fun prepare(
        width: Int, height: Int, palette: AfterglowPalette, scene: AfterglowScene, seed: Int, budget: AfterglowBudget,
    ): AfterglowBackdrop = lane.withLock {
        withContext(Dispatchers.Default) {
            ensureActive()
            AfterglowBackdrop(width.toFloat(), height.toFloat(), palette, scene.night, seed, budget.particles,
                scene, budget.rasterMaxSide) { ensureActive() }
        }
    }
}
