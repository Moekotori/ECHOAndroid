package app.echo.android

import android.content.Context
import android.util.AtomicFile
import app.echo.android.connect.*
import app.echo.android.data.*
import app.echo.android.model.connect.*
import kotlinx.coroutines.*
import org.json.*
import java.io.File

internal class LibraryReconcileController(context: Context,private val repository: EchoLibraryRepository,
    private val session: EchoLibrarySyncSession,peerId: String) {
    private val file = File(context.filesDir,"library-sync/${java.security.MessageDigest.getInstance("SHA-256").digest(peerId.toByteArray()).joinToString("") { "%02x".format(it) }}.json")
    private val baselines = linkedMapOf<String,EchoSyncBaseline>()
    private var loaded = false
    suspend fun index(): List<EchoSyncIndexRow> {
        session.snapshot("favorites") // Capability and authentication gate before exporting phone metadata.
        readLedger()
        val local = repository.syncCollections().associateBy(EchoSyncCollection::key)
        val remote = session.collections().associateBy(EchoSyncCollection::key)
        return (local.keys + remote.keys + baselines.keys).map { key ->
            EchoSyncIndexRow(key,local[key]?.name.orEmpty().ifBlank { remote[key]?.name ?: baselines[key]?.phone?.name.orEmpty() },
                local[key]?.trackCount ?: 0,remote[key]?.trackCount ?: 0)
        }
    }
    suspend fun preview(keys: List<String>): List<EchoSyncDraft> {
        readLedger()
        val result = mutableListOf<EchoSyncDraft>(); var total = 0
        for (key in keys.distinct()) {
            currentCoroutineContext().ensureActive()
            val local = repository.syncSnapshot(key); val remote = session.snapshot(key)
            total += local.tracks.size + remote.tracks.size; require(total <= 10000)
            result += withContext(Dispatchers.Default) { EchoSyncReconcile.draft(local,remote,baselines[key]) }
        }
        return result
    }
    suspend fun apply(drafts: List<EchoSyncDraft>,choices: Map<String,EchoSyncChoice>): EchoSyncApplyResult {
        var matched = 0; var missing = 0; var kept = false
        for (draft in drafts) {
            currentCoroutineContext().ensureActive()
            require(!draft.conflict || choices.containsKey(draft.phone.key))
            check(repository.syncSnapshot(draft.phone.key).revision == draft.phone.revision)
            check(session.snapshot(draft.pc.key).revision == draft.pc.revision)
            val (phone,pc) = withContext(Dispatchers.Default) { EchoSyncReconcile.resolve(draft,choices[draft.phone.key]) }
            if (!EchoSyncReconcile.same(pc,draft.pc)) {
                val applied = session.replace(draft.pc.revision,pc); matched += applied.matched; missing += applied.missing; kept = kept || applied.keptExisting
            }
            if (!EchoSyncReconcile.same(phone,draft.phone)) {
                val applied = repository.replaceSyncState(draft.phone.revision,phone); matched += applied.matched; missing += applied.missing; kept = kept || applied.keptExisting
            }
            baselines.remove(draft.phone.key)
            baselines[draft.phone.key] = EchoSyncBaseline(repository.syncSnapshot(draft.phone.key),session.snapshot(draft.pc.key))
            while (baselines.size > 500 || baselines.values.sumOf { it.phone.tracks.size + it.pc.tracks.size } > 20000) baselines.remove(baselines.keys.first())
            writeLedger()
        }
        return EchoSyncApplyResult(matched,missing,kept)
    }
    private suspend fun readLedger() = withContext(Dispatchers.IO) {
        if (loaded) return@withContext
        if (file.exists()) {
            require(file.length() <= 32 * 1024 * 1024)
            val array = JSONObject(AtomicFile(file).openRead().bufferedReader().use { it.readText() }).optJSONArray("states") ?: JSONArray()
            require(array.length() <= 500)
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val phone = EchoSyncStateCodec.decode(item.getJSONObject("phone").toString())
                val pc = EchoSyncStateCodec.decode(item.getJSONObject("pc").toString())
                baselines[phone.key] = EchoSyncBaseline(phone,pc)
            }
        }
        loaded = true
    }
    private suspend fun writeLedger() = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        val text = JSONObject().put("updatedAt",System.currentTimeMillis()).put("states",JSONArray().apply { baselines.values.forEach {
            put(JSONObject().put("phone",JSONObject(EchoSyncStateCodec.encode(it.phone))).put("pc",JSONObject(EchoSyncStateCodec.encode(it.pc))))
        } }).toString().toByteArray()
        require(text.size <= 32 * 1024 * 1024)
        val atomic = AtomicFile(file); val output = atomic.startWrite()
        try { output.write(text); atomic.finishWrite(output) } catch (error: Exception) { atomic.failWrite(output); throw error }
    }
}
