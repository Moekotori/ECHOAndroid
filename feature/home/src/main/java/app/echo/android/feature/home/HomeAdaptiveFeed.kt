package app.echo.android.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoLayoutInfo
import app.echo.android.design.LocalEchoLayout

/** A single lazy scroll surface keeps custom section order and survives window resizing. */
@Composable
internal fun HomeAdaptiveFeed(bottomInset: Dp, content: LazyGridScope.() -> Unit) {
    BoxWithConstraints(Modifier.widthIn(max = 1280.dp).fillMaxSize().statusBarsPadding(),
        contentAlignment = Alignment.TopCenter) {
        val layout = EchoLayoutInfo(maxWidth.value, maxHeight.value, LocalDensity.current.fontScale)
        val columns = if (layout.supportsTwoPanes()) 2 else 1
        val gap = if (columns == 2) 8.dp else 0.dp
        CompositionLocalProvider(LocalEchoLayout provides layout.copy(
            widthDp = (maxWidth.value - gap.value) / columns,
        )) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns), modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 12.dp, bottom = bottomInset + 24.dp),
                verticalArrangement = Arrangement.spacedBy(if (layout.compactHeader) 24.dp else 28.dp),
                horizontalArrangement = Arrangement.spacedBy(gap), content = content,
            )
        }
    }
}
