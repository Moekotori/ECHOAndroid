package app.echo.android.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun ThemeModeSelector(selectedMode: String, onSelect: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.settings_display_mode), color = scheme.onSurface, style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                "light" to R.string.settings_theme_light,
                "dark" to R.string.settings_theme_dark,
                "system" to R.string.settings_theme_system,
            ).forEach { (mode, label) ->
                val selected = selectedMode == mode
                val shape = RoundedCornerShape(16.dp)
                Column(
                    Modifier.weight(1f).clip(shape)
                        .border(if (selected) 2.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant, shape)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(mode) })
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Canvas(Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(9.dp))) {
                        fun preview(dark: Boolean) {
                            val base = if (dark) Color(0xFF19191D) else Color(0xFFF8F5F3)
                            val card = if (dark) Color(0xFF303036) else Color.White
                            val ink = if (dark) Color(0xFFD3A9B5) else Color(0xFF925568)
                            drawRect(base)
                            drawRoundRect(card, Offset(size.width * .12f, size.height * .15f), Size(size.width * .76f, size.height * .43f), CornerRadius(5.dp.toPx()))
                            drawRoundRect(ink, Offset(size.width * .20f, size.height * .25f), Size(size.width * .23f, size.height * .23f), CornerRadius(3.dp.toPx()))
                            drawLine(ink.copy(alpha = .65f), Offset(size.width * .52f, size.height * .30f), Offset(size.width * .78f, size.height * .30f), 3.dp.toPx())
                            drawLine(ink.copy(alpha = .30f), Offset(size.width * .52f, size.height * .43f), Offset(size.width * .70f, size.height * .43f), 2.dp.toPx())
                            drawRoundRect(card, Offset(size.width * .12f, size.height * .68f), Size(size.width * .76f, size.height * .17f), CornerRadius(4.dp.toPx()))
                        }
                        preview(mode == "dark")
                        if (mode == "system") clipRect(left = size.width / 2) { preview(true) }
                    }
                    Text(stringResource(label), Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                        color = if (selected) scheme.primary else scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
    }
}
