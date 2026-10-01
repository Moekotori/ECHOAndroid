package app.echo.android.feature.home

import androidx.compose.runtime.Composable

/** Inherit the shared UI scale, including the chosen family and accessibility scaling. */
@Composable
internal fun HomeAppearance(content: @Composable () -> Unit) {
    content()
}
