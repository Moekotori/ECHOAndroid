package app.echo.android.feature.player

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Window-relative pixels. No Activity or vendor extension escapes the collector. */
internal data class PlayerFold(val left: Int, val top: Int, val right: Int, val bottom: Int, val horizontal: Boolean)

@Composable
internal fun rememberPlayerFold(visible: Boolean): State<PlayerFold?> {
    val activity = LocalActivity.current
    val folds = remember(activity, visible) {
        if (activity == null || !visible) flowOf(null)
        else WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).map { info ->
            info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull {
                it.isSeparating || it.occlusionType == FoldingFeature.OcclusionType.FULL
            }?.let {
                PlayerFold(it.bounds.left, it.bounds.top, it.bounds.right, it.bounds.bottom,
                    it.orientation == FoldingFeature.Orientation.HORIZONTAL)
            }
        }.distinctUntilChanged()
    }
    return folds.collectAsStateWithLifecycle(initialValue = null)
}
