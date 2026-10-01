package app.echo.android.feature.settings

internal fun DacAssessment.guidanceResource(): Int = when (this) {
    DacAssessment.PlaybackError -> R.string.dac_help_playback_error
    DacAssessment.NoUsb -> R.string.dac_help_no_usb
    DacAssessment.Bluetooth -> R.string.dac_help_bluetooth
    DacAssessment.PermissionPending -> R.string.dac_help_permission_pending
    DacAssessment.PermissionRequired -> R.string.dac_help_permission_required
    DacAssessment.ExclusiveDisabled -> R.string.dac_help_exclusive_disabled
    DacAssessment.WaitingPlayback -> R.string.dac_help_waiting_playback
    DacAssessment.TransportError -> R.string.dac_help_transport_error
    DacAssessment.ExclusivePending -> R.string.dac_help_exclusive_pending
    DacAssessment.UnsupportedSource -> R.string.dac_help_unsupported_source
    DacAssessment.UnsupportedFormat -> R.string.dac_help_unsupported_format
    DacAssessment.ClockUnverified -> R.string.dac_help_clock_unverified
    DacAssessment.VolumeChanged -> R.string.dac_help_volume_changed
    DacAssessment.Direct -> R.string.dac_help_direct
    DacAssessment.Processing -> R.string.dac_help_processing
    DacAssessment.BitPerfectPending -> R.string.dac_help_bitperfect_pending
}
