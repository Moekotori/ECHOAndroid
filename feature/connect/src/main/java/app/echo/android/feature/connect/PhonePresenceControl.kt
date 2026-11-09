package app.echo.android.feature.connect

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

@Composable
internal fun PhonePresenceControl(
    checked: Boolean,
    connected: Boolean,
    supported: Boolean,
    onChange: (Boolean) -> Unit,
) {
    ConnectFlatGroup {
        ConnectPreference(
            title = stringResource(R.string.echo_link_phone_presence_title),
            detail = stringResource(
                when {
                    !connected -> R.string.echo_link_phone_presence_disconnected
                    !supported -> R.string.echo_link_phone_presence_unsupported
                    else -> R.string.echo_link_phone_presence_summary
                },
            ),
            checked = checked,
            enabled = checked || (connected && supported),
            onChange = onChange,
        )
    }
}
