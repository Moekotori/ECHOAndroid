package app.echo.android.feature.plugins

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.echo.android.plugin.PluginCapability
import app.echo.android.plugin.PluginInstallResult
import app.echo.android.plugin.PluginRejectReason
import app.echo.android.plugin.PluginSummary

@Composable
internal fun PluginInstallResult.noticeText(): String = when (this) {
    is PluginInstallResult.Installed -> stringResource(R.string.plugins_notice_installed)
    is PluginInstallResult.Rejected -> stringResource(
        when (reason) {
            PluginRejectReason.Unreadable -> R.string.plugins_notice_unreadable
            PluginRejectReason.TooLarge -> R.string.plugins_notice_too_large
            PluginRejectReason.TooManyFiles -> R.string.plugins_notice_too_many
            PluginRejectReason.UnsafePath -> R.string.plugins_notice_unsafe
            PluginRejectReason.MissingManifest -> R.string.plugins_notice_manifest_missing
            PluginRejectReason.InvalidManifest -> R.string.plugins_notice_manifest_invalid
            PluginRejectReason.MissingEntry -> R.string.plugins_notice_entry
            PluginRejectReason.Io -> R.string.plugins_notice_io
        },
    )
}

@Composable
internal fun PluginSummary.failureText(): String? {
    val code = errorCode ?: return null
    return when (code) {
        "timeout" -> stringResource(R.string.plugins_error_timeout)
        "sandbox" -> stringResource(R.string.plugins_error_sandbox)
        else -> stringResource(R.string.plugins_error_script, errorDetail.orEmpty())
    }
}

@Composable
internal fun PluginCapability.title(): String = stringResource(
    when (this) {
        PluginCapability.PlaybackRead -> R.string.plugins_perm_playback_read
        PluginCapability.PlaybackControl -> R.string.plugins_perm_playback_control
        PluginCapability.LibrarySearch -> R.string.plugins_perm_library
        PluginCapability.Network -> R.string.plugins_perm_network
        PluginCapability.Storage -> R.string.plugins_perm_storage
        PluginCapability.UiPage -> R.string.plugins_perm_page
    },
)

@Composable
internal fun PluginCapability.detail(): String = stringResource(
    when (this) {
        PluginCapability.PlaybackRead -> R.string.plugins_perm_playback_read_detail
        PluginCapability.PlaybackControl -> R.string.plugins_perm_playback_control_detail
        PluginCapability.LibrarySearch -> R.string.plugins_perm_library_detail
        PluginCapability.Network -> R.string.plugins_perm_network_detail
        PluginCapability.Storage -> R.string.plugins_perm_storage_detail
        PluginCapability.UiPage -> R.string.plugins_perm_page_detail
    },
)
