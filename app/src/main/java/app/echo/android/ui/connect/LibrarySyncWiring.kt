package app.echo.android.ui.connect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.echo.android.EchoAndroidViewModel
import app.echo.android.connect.EchoRemoteClient
import app.echo.android.connect.EchoLibrarySyncSession
import app.echo.android.feature.connect.LibrarySyncActions
import app.echo.android.model.connect.EchoSyncSelection

@Composable
internal fun rememberLibrarySyncActions(viewModel: EchoAndroidViewModel, remoteClient: EchoRemoteClient): LibrarySyncActions =
    remember(viewModel, remoteClient) {
        var session: EchoLibrarySyncSession? = null
        var complete: app.echo.android.LibraryReconcileController? = null
        fun coordinator(): app.echo.android.LibraryReconcileController = complete ?: viewModel.reconcile(remoteClient.librarySyncSession(),checkNotNull(remoteClient.status.value.endpoint).id).also { complete = it }
        LibrarySyncActions(
            load = {
                val target = remoteClient.librarySyncSession()
                val remote = target.collections()
                session = target
                remote.map { EchoSyncSelection(it, true) } + viewModel.librarySync.localCollections().map { EchoSyncSelection(it, false) }
            },
            preview = { choices -> viewModel.librarySync.preview(checkNotNull(session), choices) },
            apply = { plan -> viewModel.librarySync.apply(checkNotNull(session), plan) },
            advanced = app.echo.android.feature.connect.LibraryReconcileActions(
                index = { complete = null; coordinator().index() },preview = { coordinator().preview(it) },apply = { draft,choice -> coordinator().apply(draft,choice) }),
        )
    }
