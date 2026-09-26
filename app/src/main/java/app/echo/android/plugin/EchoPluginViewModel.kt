package app.echo.android.plugin

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal class EchoPluginBridge : PluginServices {
    @Volatile var snapshotProvider: () -> PlaybackSnapshot = { PlaybackSnapshot() }
    @Volatile var transportHandler: (TransportCommand) -> Unit = {}
    @Volatile var searchHandler: (String) -> List<LibraryTrackHit> = { emptyList() }

    override fun playbackSnapshot(): PlaybackSnapshot = snapshotProvider()
    override fun transport(command: TransportCommand) = transportHandler(command)
    override fun searchLibrary(query: String): List<LibraryTrackHit> = searchHandler(query)
    override fun fetch(url: String): PluginHttpResponse = PluginHttp.fetch(url, ::openPluginHttp)
}

internal class EchoPluginViewModel(application: Application) : AndroidViewModel(application) {
    val bridge = EchoPluginBridge()
    private val engine = PluginEngine(File(application.filesDir, "echo-plugins"), bridge)
    private val main = Handler(Looper.getMainLooper())
    private val _snapshot = MutableStateFlow(PluginsSnapshot())
    val snapshot: StateFlow<PluginsSnapshot> = _snapshot
    private val _wantsPlayback = MutableStateFlow(false)
    val wantsPlayback: StateFlow<Boolean> = _wantsPlayback

    init {
        engine.addListener { next ->
            _snapshot.value = next
            _wantsPlayback.value = next.plugins.any { plugin ->
                plugin.enabled && PluginCapability.PlaybackRead in plugin.grants
            }
        }
        engine.start()
    }

    fun install(bytes: ByteArray, onDone: (PluginInstallResult) -> Unit) {
        engine.install(bytes) { result -> main.post { onDone(result) } }
    }

    fun installSample(onDone: (PluginInstallResult) -> Unit) {
        engine.installSample { result -> main.post { onDone(result) } }
    }

    fun setEnabled(id: String, enabled: Boolean) = engine.setEnabled(id, enabled)

    fun setGrant(id: String, capability: PluginCapability, granted: Boolean) = engine.setGrant(id, capability, granted)

    fun delete(id: String) = engine.delete(id)

    fun openPage(id: String) = engine.openPage(id)

    fun performAction(id: String, actionId: String) = engine.performAction(id, actionId)

    fun dispatch(snapshot: PlaybackSnapshot) = engine.dispatchPlayback(snapshot)

    override fun onCleared() {
        engine.close()
        main.removeCallbacksAndMessages(null)
    }
}
