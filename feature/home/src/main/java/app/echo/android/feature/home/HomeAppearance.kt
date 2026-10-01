package app.echo.android.feature.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight

/** Keep the chosen family and font scaling; imported/serif/system fonts remain intact. */
@Composable
internal fun HomeAppearance(content: @Composable () -> Unit) {
    val source = MaterialTheme.typography
    val typography = remember(source) {
        val selectedFamily = source.bodyMedium.fontFamily
        val family = selectedFamily
        source.copy(
            headlineSmall = source.headlineSmall.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
            titleLarge = source.titleLarge.copy(fontFamily = family, fontWeight = FontWeight.SemiBold,
                fontSize = source.titleLarge.fontSize * (20f / 22f), lineHeight = source.titleLarge.lineHeight * (25f / 28f)),
            titleMedium = source.titleMedium.copy(fontFamily = family, fontWeight = FontWeight.Medium),
            titleSmall = source.titleSmall.copy(fontFamily = family, fontWeight = FontWeight.Medium),
            bodyLarge = source.bodyLarge.copy(fontFamily = family, fontWeight = FontWeight.Normal),
            bodyMedium = source.bodyMedium.copy(fontFamily = family, fontWeight = FontWeight.Normal),
            bodySmall = source.bodySmall.copy(fontFamily = family, fontWeight = FontWeight.Normal),
            labelLarge = source.labelLarge.copy(fontFamily = family, fontWeight = FontWeight.Medium),
            labelMedium = source.labelMedium.copy(fontFamily = family, fontWeight = FontWeight.Normal),
            labelSmall = source.labelSmall.copy(fontFamily = family, fontWeight = FontWeight.Normal),
        )
    }
    MaterialTheme(typography = typography, content = content)
}
