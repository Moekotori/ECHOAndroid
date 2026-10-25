package app.echo.android.data

import app.echo.android.model.library.CueSheet
import app.echo.android.model.library.CueSheetPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** Per-snapshot, bounded name index. Audio/tag payloads stay in the database, not this index. */
internal class RemoteCueScan(private val refreshEmbedded: Boolean = true, private val cache: CueScanCache? = null) {
    private data class AudioRef(val id: String, val folder: String, val name: String, val facts: TrackFingerprint)
    private val audio = LinkedHashMap<String, AudioRef>()
    private val deferred = HashMap<String, LibraryTrackEntity>()
    private val cues = ArrayList<LibraryTrackEntity>()
    var incomplete = false
        private set
    var coverageComplete = true
        private set
    private val failedIds = HashSet<String>()
    val retryIds: Set<String> get() = failedIds

    /** Returns true for sidecars; callers must never write these as playable audio rows. */
    fun collect(track: LibraryTrackEntity): Boolean {
        val name = track.fileName ?: java.net.URI(track.contentUri).path.substringAfterLast('/')
        if (LocalAudioFileTypes.isCueSheet(name, track.mimeType)) {
            if (cues.size >= 4096) { incomplete = true; coverageComplete = false } else cues += track.copy(fileName = name)
            return true
        }
        if (audio.size >= 65536) { incomplete = true; coverageComplete = false } else {
            audio[track.id] = AudioRef(track.id, folder(track.relativePath), name, track.copy(fileName = name).cueSourceFacts())
        }
        return false
    }

    fun defer(track: LibraryTrackEntity) { if (track.id in audio) deferred[track.id] = track }

    suspend fun apply(
        existing: Map<String, TrackFingerprint>,
        seen: MutableSet<String>,
        readCue: (LibraryTrackEntity, (String) -> Boolean) -> CueSheet,
        readEmbedded: (LibraryTrackEntity) -> CueSheet?,
        load: suspend (List<String>) -> List<LibraryTrackEntity>,
        write: suspend (List<LibraryTrackEntity>) -> Unit,
        merge: suspend (List<Pair<String, String>>) -> Unit,
        readSource: suspend (LibraryTrackEntity) -> LibraryTrackEntity = { it },
    ) {
        val cueRoots = ScanDirectoryIndex(cues.map { folder(it.relativePath) })
        val byFolder = audio.values.asSequence().filter { cueRoots.contains(it.folder) }.groupBy { it.folder }
        val names = byFolder.mapValues { (_, refs) -> CueAudioNameIndex(refs.map { it.name }) }
        val catalogs = HashMap<String, CueDirectoryCatalog>()
        val uncertain = HashSet<String>()
        var movements = 0
        for (cue in cues) {
            coroutineContext.ensureActive()
            val root = folder(cue.relativePath)
            val sheet = try {
                readCue(cue) { reference ->
                    val (descendant, name) = cueReference(reference) ?: return@readCue false
                    names[join(root, descendant)]?.match(name) != null
                }
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) {
                uncertain += root; incomplete = true; continue
            }
            movements += sheet.tracks.size
            if (movements > 32768) { incomplete = true; coverageComplete = false; uncertain += root; continue }
            val directories = sheet.forLocalDirectories()
            if (directories.values.sumOf { it.tracks.size } != sheet.tracks.size) {
                uncertain += root; incomplete = true
            }
            for ((descendant, localized) in directories) {
                val target = join(root, descendant)
                catalogs.getOrPut(target) {
                    CueDirectoryCatalog(byFolder[target].orEmpty().map { it.name })
                }.add(localized, cue.fileName.orEmpty())
            }
        }
        val sheets = HashMap<String, Map<String, CueSheet>>()
        catalogs.forEach { (path, catalog) ->
            if (catalog.uncertain) { uncertain += path; incomplete = true }
            sheets[path] = catalog.sheets()
        }
        val children = LibraryScanPolicy.cueChildrenByBase(existing.keys)
        val uncertainIndex = ScanDirectoryIndex(uncertain)
        val candidates = audio.values.asSequence().filter { ref ->
            ref.name.substringAfterLast('.').lowercase(java.util.Locale.ROOT) in setOf("flac", "fla", "ape", "wv", "tta", "tak") ||
                ref.folder in sheets || children[ref.id].orEmpty().isNotEmpty() ||
                CueScanState.isWholeFileCue(existing[ref.id]?.fingerprint) || uncertainIndex.contains(ref.folder)
        }
        for (page in candidates.chunked(100)) {
            coroutineContext.ensureActive()
            val work = page.filter { ref ->
                val cached = cache?.reuse(ref.facts, sheets[ref.folder]?.get(ref.name), existing, uncertainIndex.contains(ref.folder))
                if (cached != null) { seen += cached; false } else true
            }
            if (work.isEmpty()) continue
            val loadIds = work.filterNot { it.id in deferred }.map { it.id }
            val tracks = if (loadIds.isEmpty()) emptyMap() else load(loadIds).associateBy { it.id }
            for (ref in work) {
                val source = (deferred[ref.id]?.let { readSource(it) } ?: tracks[ref.id])?.copy(fileName = ref.name) ?: continue
                val old = existing[ref.id]
                val oldChildren = children[ref.id].orEmpty()
                val external = sheets[ref.folder]?.get(ref.name)
                val failed = uncertainIndex.contains(ref.folder)
                // Embedded CUEs change with the audio file. Stable whole files need no second network read.
                if (external == null && !failed && oldChildren.isEmpty() && !refreshEmbedded && old != null &&
                    !CueScanState.isWholeFileCue(old.fingerprint) && old.fingerprint != LibraryScanPolicy.PendingDocumentMetadataFingerprint &&
                    old.sizeBytes > 0 && old.sizeBytes == source.sizeBytes && old.dateModifiedSeconds > 0 &&
                    old.dateModifiedSeconds == source.dateModifiedSeconds && old.contentUri == source.contentUri) continue
                val expansion = if (source.fingerprint == LibraryScanPolicy.PendingDocumentMetadataFingerprint &&
                    (oldChildren.isNotEmpty() || CueScanState.isWholeFileCue(old?.fingerprint))) CueExpansion.ReadFailed
                else source.resolveLocalCue(external, ref.name, failed) { readEmbedded(source) }
                if (expansion is CueExpansion.ReadFailed) {
                    incomplete = true
                    seen += CueScanState.preservedIds(ref.id, old != null, oldChildren)
                    failedIds += CueScanState.preservedIds(ref.id, old != null, oldChildren)
                    // A temporary base row must not sit alongside retained old movements.
                    if (oldChildren.isNotEmpty() && ref.id !in deferred) merge(listOf(ref.id to oldChildren.first()))
                    continue
                }
                val expanded = (expansion as CueExpansion.Success).tracks
                if (ref.id in deferred || expanded != listOf(source) || oldChildren.isNotEmpty()) write(expanded)
                cache?.remember(ref.facts, external, expanded)
                seen += expanded.map { it.id }
                if (expanded.any { CueSheetPolicy.isCueTrackId(it.id) }) {
                    seen.remove(ref.id)
                    if (ref.id !in deferred || old != null) merge(listOf(ref.id to expanded.first().id))
                } else if (oldChildren.isNotEmpty()) merge(oldChildren.map { it to ref.id })
            }
        }
    }

    // SMB/WebDAV providers may distinguish directory names by case. Never merge their scopes.
    private fun folder(path: String?): String = path.orEmpty().replace('\\', '/').trim('/')
    private fun join(parent: String, child: String): String = folder(listOf(parent, child).filter(String::isNotEmpty).joinToString("/"))
}
