package app.echo.android.feature.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoExpand
import app.echo.android.design.EchoIcon
import app.echo.android.design.echoClickable
import app.echo.android.design.echoExpandIndicator

@Composable
internal fun MountedSshfsSource(expanded: Boolean, onExpand: () -> Unit, onChooseFolder: () -> Unit, busy: Boolean) {
    LibrarySourceSection(
        title = "SSHFS",
        icon = Icons.Rounded.Folder,
        description = stringResource(R.string.connect_sshfs_description),
        expanded = expanded,
        onExpand = onExpand,
    ) {
        ConnectNote(stringResource(R.string.connect_sshfs_mount_hint))
        Button(onClick = onChooseFolder, enabled = !busy, shape = ConnectControlShape, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.connect_sshfs_choose_folder))
        }
    }
}

@Composable
internal fun LibrarySourceSection(
    title: String,
    icon: ImageVector,
    description: String,
    expanded: Boolean,
    onExpand: () -> Unit,
    status: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).echoClickable(role = Role.Button, onClick = onExpand)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EchoIcon(icon, null, Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                ConnectNote(description)
                status?.invoke()
            }
            EchoIcon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, Modifier.size(24.dp).echoExpandIndicator(expanded))
        }
        EchoExpand(expanded) {
            Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                content()
            }
        }
    }
}
