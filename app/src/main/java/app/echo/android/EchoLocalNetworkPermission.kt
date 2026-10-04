package app.echo.android

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.echo.android.connect.hasEchoLocalNetworkAccess

/** The deferred action belongs to this UI lifetime; dismissing or leaving drops it. */
internal class EchoLocalNetworkPermission {
    var granted by mutableStateOf(true)
        internal set
    internal var pendingAction: (() -> Unit)? = null
    internal var request: () -> Unit = {}
    fun runForAddress(address: String, action: () -> Unit) {
        if (app.echo.android.connect.echoAddressNeedsLocalNetworkAccess(address)) run(action) else action()
    }
    fun run(action: () -> Unit) {
        if (granted) action() else {
            pendingAction = action
            request()
        }
    }
}

@Composable
internal fun rememberEchoLocalNetworkPermission(): EchoLocalNetworkPermission {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val access = remember { EchoLocalNetworkPermission().apply { granted = context.hasEchoLocalNetworkAccess() } }
    var showDenied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        access.granted = granted || context.hasEchoLocalNetworkAccess()
        val pending = access.pendingAction
        access.pendingAction = null
        if (access.granted) pending?.invoke() else showDenied = true
    }
    SideEffect {
        access.request = {
            access.granted = context.hasEchoLocalNetworkAccess()
            if (access.granted) {
                val pending = access.pendingAction
                access.pendingAction = null
                pending?.invoke()
            } else launcher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        }
    }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) access.granted = context.hasEchoLocalNetworkAccess()
            if (event == Lifecycle.Event.ON_STOP) access.pendingAction = null
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            access.pendingAction = null
            access.request = {}
        }
    }
    if (showDenied) AlertDialog(
        onDismissRequest = { showDenied = false },
        title = { Text(stringResource(R.string.local_network_permission_title)) },
        text = { Text(stringResource(R.string.local_network_permission_detail)) },
        confirmButton = {
            TextButton(onClick = {
                showDenied = false
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            }) { Text(stringResource(R.string.local_network_permission_settings)) }
        },
        dismissButton = { TextButton(onClick = { showDenied = false }) { Text(stringResource(R.string.local_network_permission_later)) } },
    )
    return access
}

@Composable
internal fun EchoLocalNetworkPermissionNotice(access: EchoLocalNetworkPermission) {
    if (!access.granted) Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.local_network_permission_detail))
        TextButton(onClick = { access.run {} }) { Text(stringResource(R.string.local_network_permission_allow)) }
    }
}
