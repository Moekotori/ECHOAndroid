package app.echo.android.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/** Shared surface for anchored choice menus; popup positioning and dismissal stay with Material. */
@Composable
fun EchoDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.widthIn(min = 188.dp, max = 264.dp),
        offset = DpOffset(0.dp, 4.dp),
        shape = RoundedCornerShape(20.dp),
        containerColor = scheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.55f)),
        content = content,
    )
}

/** Inset selected row and a reserved check column keep labels aligned across choices. */
@Composable
fun EchoDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    selected: Boolean,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    DropdownMenuItem(
        text = {
            ProvideTextStyle(MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )) { text() }
        },
        onClick = onClick,
        modifier = modifier
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clip(shape)
            .background(if (selected) scheme.primary.copy(alpha = 0.09f) else Color.Transparent)
            .heightIn(min = 48.dp)
            .semantics { this.selected = selected },
        leadingIcon = leadingIcon,
        trailingIcon = {
            Box(Modifier.size(18.dp)) {
                if (selected) EchoIcon(Icons.Rounded.Check, null, Modifier.size(18.dp))
            }
        },
        enabled = enabled,
        colors = MenuDefaults.itemColors(
            textColor = scheme.onSurface,
            leadingIconColor = echoIconColor(),
            trailingIconColor = echoIconColor(),
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
fun EchoDropdownMenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
    )
}
