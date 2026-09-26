package app.echo.android.plugin

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CopyOnWriteArrayList
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.javascript.Function

/**
 * Loads imported plugins off the main thread and never calls back into playback itself.
 * [services] is invoked on the plugin thread and must not wait for that thread.
 */
class PluginEngine(
    private val root: File,
    private val services: PluginServices,
) {
    private val factory = PluginContextFactory()
    private val listeners = CopyOnWriteArrayList<(PluginsSnapshot) -> Unit>()
    private val snapshotRef = AtomicReference(PluginsSnapshot())
    private val plugins = linkedMapOf<String, RuntimePlugin>()
    private val sessions = linkedMapOf<String, PluginScriptSession>()
    private val generations = mutableMapOf<String, Int>()
    private val timerCounts = mutableMapOf<String, Int>()
    private val timers = linkedMapOf<Int, OwnedTimer>()
    private var nextTimer = 1
    private val pendingPlayback = AtomicReference<PlaybackSnapshot?>(null)
    private val lastPlayback = HashMap<String, PlaybackSnapshot>()
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "echo-plugins").apply { isDaemon = true }
    }
    private val timerExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "echo-plugin-timers").apply { isDaemon = true }
    }

    val snapshot: PluginsSnapshot get() = snapshotRef.get()

    fun addListener(listener: (PluginsSnapshot) -> Unit) {
        listeners += listener
        listener(snapshot)
    }

    fun start() {
        executor.execute { load() }
    }

    fun install(bytes: ByteArray, onDone: (PluginInstallResult) -> Unit) {
        executor.execute {
            val result = runCatching { installHere(bytes) }.getOrElse {
                PluginInstallResult.Rejected(PluginRejectReason.Io)
            }
            onDone(result)
        }
    }

    fun installSample(onDone: (PluginInstallResult) -> Unit) {
        install(SamplePlugin.zipBytes(), onDone)
    }

    fun setEnabled(id: String, enabled: Boolean) {
        executor.execute {
            val plugin = plugins[id] ?: return@execute
            if (enabled && sessions.containsKey(id)) return@execute
            if (!enabled && !plugin.enabled && !sessions.containsKey(id)) return@execute
            if (enabled) enable(id) else disable(id)
        }
    }

    fun setGrant(id: String, capability: PluginCapability, granted: Boolean) {
        executor.execute {
            val plugin = plugins[id] ?: return@execute
            if (capability !in plugin.manifest.permissions) return@execute
            val grants = if (granted) plugin.grants + capability else plugin.grants - capability
            if (capability == PluginCapability.PlaybackRead && !granted) lastPlayback.remove(id)
            plugins[id] = plugin.copy(grants = grants.allowedFor(plugin.manifest))
            persist()
            publish()
        }
    }

    fun delete(id: String) {
        executor.execute {
            if (plugins[id] == null && !File(root, id).exists()) return@execute
            disable(id)
            plugins.remove(id)
            File(root, id).deleteRecursively()
            persist()
            publish()
        }
    }

    fun openPage(id: String) {
        executor.execute {
            val session = sessions[id] ?: return@execute
            if (PluginCapability.UiPage !in plugins[id]?.grants.orEmpty()) return@execute
            session.callHook("onPageOpen")?.let { fail(id, it) }
        }
    }

    fun performAction(id: String, actionId: String) {
        executor.execute {
            if (!PluginPaths.isActionId(actionId)) return@execute
            val session = sessions[id] ?: return@execute
            if (PluginCapability.UiPage !in plugins[id]?.grants.orEmpty()) return@execute
            session.callHook("onAction", actionId)?.let { fail(id, it) }
        }
    }

    fun dispatchPlayback(snapshot: PlaybackSnapshot) {
        pendingPlayback.set(snapshot)
        executor.execute {
            val next = pendingPlayback.getAndSet(null) ?: return@execute
            sessions.keys.toList().forEach { id ->
                val plugin = plugins[id] ?: return@forEach
                if (!plugin.enabled || PluginCapability.PlaybackRead !in plugin.grants) return@forEach
                val session = sessions[id] ?: return@forEach
                if (lastPlayback[id] == next) return@forEach
                lastPlayback[id] = next
                session.callHook("onPlayback", next.toScriptMap())?.let { fail(id, it) }
            }
        }
    }

    fun close() {
        executor.shutdownNow()
        timerExecutor.shutdownNow()
    }

    internal fun flushForTest() {
        val latch = CountDownLatch(1)
        executor.execute { latch.countDown() }
        check(latch.await(20, TimeUnit.SECONDS)) { "plugin thread stalled" }
    }

    private fun installHere(bytes: ByteArray): PluginInstallResult = when (val installed = installPluginPackage(root, bytes)) {
        is PackageInstall.Rejected -> PluginInstallResult.Rejected(installed.reason)
        is PackageInstall.Ready -> {
            val id = installed.manifest.id
            val previous = plugins[id]
            val wasEnabled = sessions.containsKey(id)
            if (wasEnabled) disable(id)
            plugins[id] = RuntimePlugin(
                manifest = installed.manifest,
                enabled = false,
                grants = previous?.grants.orEmpty().allowedFor(installed.manifest),
                errorCode = null,
                errorDetail = null,
                logs = emptyList(),
                page = null,
            )
            persist()
            publish()
            if (wasEnabled) enable(id)
            PluginInstallResult.Installed(id)
        }
    }

    private fun load() {
        root.mkdirs()
        val indexed = readIndex()
        val discovered = linkedMapOf<String, RuntimePlugin>()
        root.listFiles().orEmpty()
            .filter { it.isDirectory && PluginPaths.isPluginId(it.name) }
            .forEach { directory ->
                val manifest = runCatching {
                    parsePluginManifest(File(directory, "echo-plugin.json").readText(Charsets.UTF_8))
                }.getOrNull() ?: return@forEach
                if (manifest.id != directory.name) return@forEach
                val saved = indexed[manifest.id]
                discovered[manifest.id] = RuntimePlugin(
                    manifest = manifest,
                    enabled = false,
                    grants = saved?.grants.orEmpty().allowedFor(manifest),
                    errorCode = null,
                    errorDetail = null,
                    logs = emptyList(),
                    page = null,
                )
            }
        plugins.clear()
        plugins.putAll(discovered)
        publish()
        discovered.keys.filter { indexed[it]?.enabled == true }.forEach(::enable)
    }

    private fun enable(id: String) {
        val plugin = plugins[id] ?: return
        if (sessions.containsKey(id)) return
        plugins[id] = plugin.copy(enabled = true, errorCode = null, errorDetail = null)
        publish()
        val script = runCatching {
            File(File(root, id), plugin.manifest.entry).readText(Charsets.UTF_8)
        }.getOrNull()
        if (script == null) {
            fail(id, ScriptFault("script", "missing"))
            return
        }
        val session = try {
            PluginScriptSession(
                factory = factory,
                script = script,
                grants = { plugins[id]?.grants.orEmpty() },
                services = services,
                storage = PluginStorage(File(root, "$id/storage.json")),
                callbacks = callbacks(id),
            )
        } catch (_: PluginTimeoutError) {
            fail(id, ScriptFault("timeout", null))
            return
        } catch (error: Throwable) {
            fail(id, ScriptFault("script", error.message?.lineSequence()?.firstOrNull()?.take(180)))
            return
        }
        sessions[id] = session
        val startup = session.callHook("onLoad") ?: session.callHook("onEnable")
        if (startup != null) {
            sessions.remove(id)
            fail(id, startup)
            return
        }
        persist()
        publish()
    }

    private fun disable(id: String) {
        val session = sessions.remove(id)
        lastPlayback.remove(id)
        generations[id] = (generations[id] ?: 0) + 1
        cancelTimers(id)
        val fault = session?.callHook("onDisable")
        val plugin = plugins[id] ?: return
        plugins[id] = plugin.copy(enabled = false, errorCode = fault?.code, errorDetail = fault?.detail)
        persist()
        publish()
    }

    private fun fail(id: String, fault: ScriptFault) {
        sessions.remove(id)
        lastPlayback.remove(id)
        generations[id] = (generations[id] ?: 0) + 1
        cancelTimers(id)
        val plugin = plugins[id] ?: return
        plugins[id] = plugin.copy(enabled = false, errorCode = fault.code, errorDetail = fault.detail)
        persist()
        publish()
    }

    private fun callbacks(id: String) = PluginScriptCallbacks(
        onPage = { page ->
            val plugin = plugins[id] ?: return@PluginScriptCallbacks
            plugins[id] = plugin.copy(page = page)
            publish()
        },
        onLog = { line ->
            val plugin = plugins[id] ?: return@PluginScriptCallbacks
            plugins[id] = plugin.copy(logs = (plugin.logs + line).takeLast(40))
            publish()
        },
        schedule = { delay, function -> schedule(id, delay, function) },
        cancel = { timerId -> cancel(id, timerId) },
    )

    private fun schedule(id: String, delayMs: Long, function: Function): Map<String, Any?> {
        if (delayMs < 1_000L) return mapOf("ok" to false, "error" to "too_soon")
        if (delayMs > 86_400_000L) return mapOf("ok" to false, "error" to "invalid")
        if ((timerCounts[id] ?: 0) >= 8) return mapOf("ok" to false, "error" to "too_many")
        val generation = generations.getOrPut(id) { 0 }
        val timerId = nextTimer++
        timerCounts[id] = (timerCounts[id] ?: 0) + 1
        val future = timerExecutor.schedule({
            executor.execute {
                val owned = timers.remove(timerId) ?: return@execute
                timerCounts[id] = ((timerCounts[id] ?: 1) - 1).coerceAtLeast(0)
                if (owned.pluginId != id || generations[id] != generation) return@execute
                val session = sessions[id] ?: return@execute
                session.invoke(function)?.let { fail(id, it) }
            }
        }, delayMs, TimeUnit.MILLISECONDS)
        timers[timerId] = OwnedTimer(id, future)
        return mapOf("ok" to true, "id" to timerId)
    }

    private fun cancel(pluginId: String, timerId: Int): Map<String, Any?> {
        val owned = timers[timerId] ?: return mapOf("ok" to false, "error" to "invalid")
        if (owned.pluginId != pluginId) return mapOf("ok" to false, "error" to "invalid")
        timers.remove(timerId)
        owned.future.cancel(false)
        timerCounts[pluginId] = ((timerCounts[pluginId] ?: 1) - 1).coerceAtLeast(0)
        return mapOf("ok" to true)
    }

    private fun cancelTimers(id: String) {
        timers.filterValues { it.pluginId == id }.keys.toList().forEach { timerId ->
            timers.remove(timerId)?.future?.cancel(false)
        }
        timerCounts.remove(id)
    }

    private fun publish() {
        val next = PluginsSnapshot(loaded = true, plugins = plugins.values.map { it.toSummary() })
        snapshotRef.set(next)
        listeners.forEach { listener -> runCatching { listener(next) } }
    }

    private fun persist() {
        val array = JSONArray()
        plugins.values.forEach { plugin ->
            val grants = JSONArray()
            plugin.grants.forEach { grants.put(it.wire) }
            array.put(
                JSONObject()
                    .put("id", plugin.manifest.id)
                    .put("enabled", sessions.containsKey(plugin.manifest.id))
                    .put("grants", grants),
            )
        }
        val temporary = File(root, "index.json.tmp")
        temporary.writeText(JSONObject().put("plugins", array).toString(), Charsets.UTF_8)
        val file = File(root, "index.json")
        if (file.exists()) file.delete()
        temporary.renameTo(file)
    }

    private fun readIndex(): Map<String, SavedPlugin> {
        val json = runCatching { JSONObject(File(root, "index.json").readText(Charsets.UTF_8)) }.getOrNull()
            ?: return emptyMap()
        val array = json.optJSONArray("plugins") ?: return emptyMap()
        return buildMap {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id")
                if (!PluginPaths.isPluginId(id)) continue
                val grants = mutableSetOf<PluginCapability>()
                val raw = item.optJSONArray("grants")
                if (raw != null) {
                    for (grantIndex in 0 until raw.length()) {
                        PluginCapability.fromWire(raw.optString(grantIndex))?.let { grants += it }
                    }
                }
                put(id, SavedPlugin(item.optBoolean("enabled"), grants))
            }
        }
    }

    private data class RuntimePlugin(
        val manifest: PluginManifest,
        val enabled: Boolean,
        val grants: Set<PluginCapability>,
        val errorCode: String?,
        val errorDetail: String?,
        val logs: List<String>,
        val page: PluginPageDocument?,
    ) {
        fun toSummary() = PluginSummary(
            id = manifest.id,
            name = manifest.name,
            version = manifest.version,
            summary = manifest.summary,
            enabled = enabled,
            requested = manifest.permissions,
            grants = grants,
            errorCode = errorCode,
            errorDetail = errorDetail,
            logs = logs,
            page = page,
        )
    }

    private data class SavedPlugin(val enabled: Boolean, val grants: Set<PluginCapability>)
    private data class OwnedTimer(val pluginId: String, val future: ScheduledFuture<*>)
}

private fun Set<PluginCapability>.allowedFor(manifest: PluginManifest): Set<PluginCapability> =
    intersect(manifest.permissions.toSet())

private fun PlaybackSnapshot.toScriptMap(): Map<String, Any?> = mapOf(
    "title" to title.take(200),
    "artist" to artist.take(200),
    "album" to album.take(200),
    "playing" to playing,
    "positionMs" to positionMs.coerceIn(0, 86_400_000L),
    "durationMs" to durationMs.coerceIn(0, 86_400_000L),
)
