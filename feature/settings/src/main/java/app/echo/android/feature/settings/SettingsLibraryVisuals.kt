package app.echo.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

internal data class LibraryPalette(
    val teal: Color, val blue: Color, val violet: Color, val amber: Color, val rose: Color,
)

@Composable
internal fun libraryPalette(): LibraryPalette = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
    LibraryPalette(Color(0xFF79CFC0), Color(0xFF91B6FF), Color(0xFFC0A6EF), Color(0xFFE3BD77), Color(0xFFE4A1B4))
} else {
    LibraryPalette(Color(0xFF227E73), Color(0xFF426CB4), Color(0xFF7958AE), Color(0xFF956A27), Color(0xFFA45470))
}

@Composable
internal fun LibraryTintPanel(
    accent: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.fillMaxWidth().background(accent.copy(alpha = 0.055f), shape)
            .border(1.dp, accent.copy(alpha = 0.12f), shape).padding(16.dp),
        content = content,
    )
}
