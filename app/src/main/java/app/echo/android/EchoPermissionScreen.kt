package app.echo.android

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon

@Composable
fun EchoPermissionScreen(
    permissionStatuses: List<PermissionEntry>,
    onDismiss: () -> Unit,
    onRequestPermission: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 464.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ECHOSteam's packaged desktop icon, from build-resources/icons/echo-app-icon.png.
            Image(
                painter = painterResource(R.drawable.echo_desktop_permission_icon),
                contentDescription = null,
                modifier = Modifier.size(112.dp),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.permission_welcome),
                style = typography.labelLarge,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.permission_title),
                modifier = Modifier.semantics { heading() },
                style = typography.displaySmall,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.permission_subtitle),
                style = typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(28.dp))
            HorizontalDivider()
            permissionStatuses.forEach { entry ->
                PermissionRow(entry = entry) {
                    if (entry.canRequest) onRequestPermission(entry.permission)
                    else onOpenSettings()
                }
                HorizontalDivider()
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(stringResource(R.string.permission_continue), textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.permission_skip),
                    style = typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(entry: PermissionEntry, onAction: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EchoIcon(
            imageVector = entry.icon,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(entry.label, style = typography.titleMedium, color = colors.onSurface)
            Text(entry.description, style = typography.bodySmall, color = colors.onSurfaceVariant)
        }

        if (entry.granted) {
            Row(
                modifier = Modifier.widthIn(max = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EchoIcon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(R.string.permission_granted),
                    style = typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        } else {
            TextButton(
                onClick = onAction,
                modifier = Modifier.widthIn(min = 48.dp, max = 120.dp).heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(stringResource(R.string.permission_allow), textAlign = TextAlign.Center)
            }
        }
    }
}

data class PermissionEntry(
    val permission: String,
    val label: String,
    val description: String,
    val icon: ImageVector,
    val granted: Boolean,
    val canRequest: Boolean,
)
