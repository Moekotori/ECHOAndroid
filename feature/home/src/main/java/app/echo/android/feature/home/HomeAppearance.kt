package app.echo.android.feature.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight

/** Preserve the selected font and scaling while giving home its own reading hierarchy. */
@Composable
internal fun HomeAppearance(content: @Composable () -> Unit) {
    val source = MaterialTheme.typography
    val typography = remember(source) {
        source.copy(
            headlineSmall = source.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            titleLarge = source.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            titleMedium = source.titleMedium.copy(fontWeight = FontWeight.Medium),
            titleSmall = source.titleSmall.copy(fontWeight = FontWeight.Medium),
            bodyLarge = source.bodyLarge.copy(fontWeight = FontWeight.Normal),
            bodyMedium = source.bodyMedium.copy(fontWeight = FontWeight.Normal),
            bodySmall = source.bodySmall.copy(fontWeight = FontWeight.Normal),
            labelLarge = source.labelLarge.copy(fontWeight = FontWeight.Medium),
            labelMedium = source.labelMedium.copy(fontWeight = FontWeight.Normal),
            labelSmall = source.labelSmall.copy(fontWeight = FontWeight.Normal),
        )
    }
    MaterialTheme(typography = typography, content = content)
}
