package app.echo.android.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun EchoPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.92f))
            .border(
                BorderStroke(1.dp, Color.White.copy(alpha = 0.96f)),
                RoundedCornerShape(20.dp),
            ),
    ) {
        content()
    }
}

@Composable
fun EchoTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 44.dp),
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
    ) {
        Text(text = text, maxLines = 1)
    }
}

@Composable
fun EchoIconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(10.dp),
        )
    }
}

@Composable
fun EchoInfoChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.96f)),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, color = Color(0xFF25242A))
        }
    }
}

@Composable
fun EchoMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.96f)),
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, color = Color(0xFF25242A))
            if (detail != null) {
                Text(
                    detail,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color(0xFF6D6D73),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
fun EchoSegmentChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            Color.White.copy(alpha = 0.92f)
        },
        border = BorderStroke(
            1.dp,
            if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            } else {
                Color.White.copy(alpha = 0.96f)
            },
        ),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            color = if (selected) MaterialTheme.colorScheme.primary else Color(0xFF6D6D73),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun EchoSectionTitle(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF25242A))
        Text(subtitle, color = Color(0xFF6D6D73), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun EchoPlaceholderLine(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.90f),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            color = Color(0xFF6D6D73),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
