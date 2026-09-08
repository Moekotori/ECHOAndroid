package app.echo.android.feature.connect

import androidx.compose.ui.res.stringResource

import app.echo.android.feature.connect.R as L10nR

import androidx.compose.runtime.Composable
import app.echo.android.model.library.LibraryScanPhase
import app.echo.android.model.library.LibraryScanProgress

@Composable
internal fun remoteLibraryDetail(scanState: LibraryScanProgress, ready: Boolean): String =
    when {
        scanState.isScanning -> {
            val phaseLabel = remoteScanPhaseLabel(scanState.phase)
            buildString {
                append(phaseLabel)
                append(" · ")
                append(scanState.totalCount?.let { "${scanState.scannedCount}/$it" } ?: scanState.scannedCount.toString())
                scanState.currentTitle?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
            }
        }
        scanState.phase == LibraryScanPhase.Completed -> stringResource(L10nR.string.feature_connect_sync_complete_scanstate_scannedcount_tracks_scanstate_insertedco_41ae3e, (scanState.scannedCount).toString(), (scanState.insertedCount).toString(), (scanState.updatedCount).toString(), (scanState.deletedCount).toString())
        scanState.phase == LibraryScanPhase.Error -> scanState.error ?: stringResource(L10nR.string.feature_connect_remote_library_sync_failed_e55eb1)
        ready -> stringResource(L10nR.string.feature_connect_ready_to_sync_to_the_cloud_album_wall_20a1e5)
        else -> stringResource(L10nR.string.feature_connect_enter_the_server_username_and_password_b90327)
    }

@Composable
internal fun remoteScanPhaseLabel(phase: LibraryScanPhase): String =
    when (phase) {
        LibraryScanPhase.Preparing -> stringResource(L10nR.string.feature_connect_preparing_sync_f64e14)
        LibraryScanPhase.QueryingMediaStore -> stringResource(L10nR.string.feature_connect_reading_remote_library_06fcd1)
        LibraryScanPhase.Diffing -> stringResource(L10nR.string.feature_connect_comparing_index_7c03bf)
        LibraryScanPhase.WritingDatabase -> stringResource(L10nR.string.feature_connect_writing_library_a8f4bd)
        LibraryScanPhase.CleaningRemoved -> stringResource(L10nR.string.feature_connect_cleaning_old_index_2c0185)
        LibraryScanPhase.Completed -> stringResource(L10nR.string.feature_connect_sync_complete_9ac5cf)
        LibraryScanPhase.Cancelled -> stringResource(L10nR.string.feature_connect_cancelled_4ab41f)
        LibraryScanPhase.Error -> stringResource(L10nR.string.feature_connect_sync_failed_8cc151)
        LibraryScanPhase.Idle -> stringResource(L10nR.string.feature_connect_waiting_to_sync_21ad43)
    }

