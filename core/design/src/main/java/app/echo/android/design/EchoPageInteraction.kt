package app.echo.android.design

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics

/** Exiting pages remain drawn, but their nested navigation must stop owning Back immediately. */
val LocalEchoPageInteractive = compositionLocalOf { true }

/** Works for both full pages and intrinsically sized sheet content. */
@Composable
fun EchoInteractionGate(
    active: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactive = active && LocalEchoPageInteractive.current
    Box(
        modifier.then(if (interactive) Modifier else Modifier.clearAndSetSemantics { })
            // A parent pager can temporarily relinquish Back while its drag is still in flight.
            // Only this surface's own exit may consume that pointer stream.
            .then(if (active) Modifier else Modifier
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    }
                }),
        propagateMinConstraints = true,
    ) {
        CompositionLocalProvider(LocalEchoPageInteractive provides interactive) { content() }
    }
}

@Composable
fun EchoPageBackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val active = enabled && LocalEchoPageInteractive.current
    val latestActive = rememberUpdatedState(active)
    val latestBack = rememberUpdatedState(onBack)
    BackHandler(enabled = active) {
        if (latestActive.value) latestBack.value()
    }
}
