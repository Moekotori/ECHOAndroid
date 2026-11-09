package app.echo.android.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import app.echo.android.design.LocalEchoPageInteractive
import app.echo.android.design.detectEchoTargetDrag
import app.echo.android.model.library.EchoTrack

internal class LibraryBatchDrag {
    class Checkbox(val track: State<EchoTrack>) {
        var coordinates: LayoutCoordinates? = null
        var bounds = Rect.Zero
    }

    val checkboxes = mutableSetOf<Checkbox>()
    private val crossed = ArrayList<Checkbox>()

    fun selectBetween(x: Float, from: Float, to: Float, selection: LibraryBatchSelection) {
        if (selection.locked || selection.selected.size >= LibraryBatchSelection.Limit) return
        val top = minOf(from, to)
        val bottom = maxOf(from, to)
        crossed.clear()
        for (checkbox in checkboxes) {
            val bounds = checkbox.bounds
            if (checkbox.coordinates?.isAttached == true && !bounds.isEmpty &&
                x >= bounds.left && x < bounds.right && bottom >= bounds.top && top < bounds.bottom &&
                checkbox.track.value.id !in selection.selected
            ) crossed.add(checkbox)
        }
        if (to >= from) crossed.sortBy { it.bounds.top } else crossed.sortByDescending { it.bounds.bottom }
        for (checkbox in crossed) selection.selectByDrag(checkbox.track.value)
        crossed.clear()
    }
}

@Composable
internal fun Modifier.libraryBatchDrag(track: EchoTrack, selection: LibraryBatchSelection?): Modifier {
    if (selection == null || !selection.active || selection.locked || !LocalEchoPageInteractive.current) return this
    val latestTrack = rememberUpdatedState(track)
    val checkbox = remember(track.id, selection) { LibraryBatchDrag.Checkbox(latestTrack) }
    DisposableEffect(selection, checkbox) {
        selection.drag.checkboxes.add(checkbox)
        onDispose { selection.drag.checkboxes.remove(checkbox) }
    }
    return onGloballyPositioned { coordinates ->
        checkbox.coordinates = coordinates
        checkbox.bounds = coordinates.boundsInRoot()
    }.pointerInput(selection, checkbox) {
        var laneX = 0f
        var lastY = 0f
        detectEchoTargetDrag(
            longPress = true,
            targetAt = { checkbox.coordinates?.takeIf { it.isAttached } },
            onStart = { coordinates, position ->
                val start = coordinates.localToRoot(position)
                laneX = start.x
                lastY = start.y
                selection.selectByDrag(latestTrack.value)
            },
            onDrag = { change, _ ->
                val coordinates = checkbox.coordinates
                if (coordinates?.isAttached == true) {
                    val y = coordinates.localToRoot(change.position).y
                    selection.drag.selectBetween(laneX, lastY, y, selection)
                    lastY = y
                }
            },
            onStop = {},
            onCancel = {},
            dragPass = PointerEventPass.Initial,
        )
    }
}
