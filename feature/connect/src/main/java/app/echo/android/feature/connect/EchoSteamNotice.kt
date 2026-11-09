package app.echo.android.feature.connect

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource

@Composable
internal fun EchoSteamNotice() {
    ConnectNote(stringResource(R.string.echo_link_steam_edition_requirement))
}

@Composable
internal fun EchoLinkLibrarySource() {
    var showPurchase by rememberSaveable { mutableStateOf(false) }
    var failedToOpenStore by rememberSaveable { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    ConnectActionRow(
        ConnectAction(
            icon = Icons.Rounded.Link,
            title = "Echo Link",
            detail = stringResource(R.string.echo_link_steam_edition_requirement),
            onClick = {
                failedToOpenStore = false
                showPurchase = true
            },
        ),
    )
    if (showPurchase) {
        AlertDialog(
            onDismissRequest = { showPurchase = false },
            title = { Text(stringResource(R.string.echo_link_steam_edition_requirement)) },
            text = {
                val message = if (failedToOpenStore) R.string.echo_link_steam_store_error else R.string.echo_link_steam_purchase_message
                ConnectNote(stringResource(message), error = failedToOpenStore)
            },
            confirmButton = {
                TextButton(onClick = {
                    if (runCatching { uriHandler.openUri(EchoProStoreUrl) }.isSuccess) showPurchase = false
                    else failedToOpenStore = true
                }) {
                    Text(stringResource(R.string.echo_link_steam_purchase_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPurchase = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

private const val EchoProStoreUrl = "https://store.steampowered.com/app/5105160/ECHO_Pro/"
