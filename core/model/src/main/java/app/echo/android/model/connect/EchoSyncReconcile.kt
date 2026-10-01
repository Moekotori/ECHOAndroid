package app.echo.android.model.connect

data class EchoSyncState(val key: String, val name: String, val exists: Boolean, val tracks: List<EchoSyncTrackRef>, val revision: String = "")
data class EchoSyncBaseline(val phone: EchoSyncState, val pc: EchoSyncState)
data class EchoSyncDraft(val phone: EchoSyncState, val pc: EchoSyncState, val phoneDesired: EchoSyncState, val pcDesired: EchoSyncState, val conflict: Boolean)
enum class EchoSyncChoice { Phone, Pc, Merge }
data class EchoSyncApplyResult(val matched: Int, val missing: Int, val keptExisting: Boolean)
data class EchoSyncIndexRow(val key: String, val name: String, val phoneCount: Int, val pcCount: Int)

object EchoSyncReconcile {
    fun trackKey(ref: EchoSyncTrackRef): String = listOf(ref.title.trim().lowercase(),ref.artist.trim().lowercase(),ref.album.orEmpty().trim().lowercase(),(ref.durationMs / 1000).toString()).joinToString("\u001f")
    fun same(a: EchoSyncState,b: EchoSyncState): Boolean = a.exists == b.exists && a.name == b.name && a.tracks.map(::trackKey) == b.tracks.map(::trackKey)
    fun draft(phone: EchoSyncState,pc: EchoSyncState,base: EchoSyncBaseline?): EchoSyncDraft {
        if (base == null) {
            val union = union(phone.tracks,pc.tracks)
            return EchoSyncDraft(phone,pc,phone.copy(name = phone.name.ifBlank { pc.name },exists = phone.exists || pc.exists,tracks = union),
                pc.copy(name = pc.name.ifBlank { phone.name },exists = phone.exists || pc.exists,tracks = union),false)
        }
        val phoneChanged = !same(phone,base.phone); val pcChanged = !same(pc,base.pc)
        return EchoSyncDraft(phone,pc,applyDelta(phone,base.pc,pc),applyDelta(pc,base.phone,phone),phoneChanged && pcChanged && !same(phone,pc))
    }
    fun resolve(draft: EchoSyncDraft,choice: EchoSyncChoice?): Pair<EchoSyncState,EchoSyncState> {
        if (!draft.conflict && choice == null) return draft.phoneDesired to draft.pcDesired
        return when (choice ?: EchoSyncChoice.Merge) {
            EchoSyncChoice.Phone -> draft.phone to draft.phone.copy(key = draft.pc.key)
            EchoSyncChoice.Pc -> draft.pc.copy(key = draft.phone.key) to draft.pc
            EchoSyncChoice.Merge -> {
                val a = draft.phone.copy(name = draft.phone.name.ifBlank { draft.pc.name }, exists = draft.phone.exists || draft.pc.exists,
                    tracks = union(draft.phone.tracks,draft.pc.tracks))
                val b = draft.pc.copy(name = draft.pc.name.ifBlank { draft.phone.name }, exists = a.exists,
                    tracks = union(draft.pc.tracks,draft.phone.tracks))
                a to b
            }
        }
    }
    private fun union(a: List<EchoSyncTrackRef>, b: List<EchoSyncTrackRef>): List<EchoSyncTrackRef> {
        val keys = a.map(::trackKey).toHashSet()
        return a + b.filter { keys.add(trackKey(it)) }
    }
    private fun applyDelta(target: EchoSyncState,base: EchoSyncState,source: EchoSyncState): EchoSyncState {
        if (same(base,source)) return target
        if (base.exists && !source.exists && source.key != "favorites") return target.copy(exists = false,tracks = emptyList())
        if (!source.exists) return target
        val baseKeys = base.tracks.map(::trackKey).toHashSet()
        val sourceKeys = source.tracks.map(::trackKey).toHashSet()
        val removed = baseKeys - sourceKeys
        val kept = target.tracks.filterNot { trackKey(it) in removed }
        val additions = source.tracks.filter { trackKey(it) !in baseKeys }
        val merged = union(kept,additions).toMutableList()
        if (source.tracks.map(::trackKey) != base.tracks.map(::trackKey)) {
            val available = merged.associateBy(::trackKey)
            val ordered = source.tracks.mapNotNull { available[trackKey(it)] }.distinctBy(::trackKey)
            val positions = merged.indices.filter { trackKey(merged[it]) in sourceKeys }
            if (positions.size == ordered.size) positions.forEachIndexed { index,position -> merged[position] = ordered[index] }
        }
        return target.copy(exists = true,name = if (source.name != base.name || !target.exists) source.name else target.name,tracks = merged)
    }
}
