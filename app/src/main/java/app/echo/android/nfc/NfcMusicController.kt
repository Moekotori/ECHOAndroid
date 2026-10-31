package app.echo.android.nfc

import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.SystemClock
import android.os.Build
import app.echo.android.data.EchoLibraryDatabase
import app.echo.android.data.NfcMusicBinding
import app.echo.android.data.NfcMusicBindingStore
import app.echo.android.feature.library.NfcMusicChoice
import app.echo.android.feature.library.NfcMusicPhase
import app.echo.android.feature.library.NfcMusicProblem
import app.echo.android.feature.library.NfcMusicUiState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** ViewModel-owned session; no Activity or NFC Tag is retained between discoveries. */
internal class NfcMusicController(context: Context, private val scope: CoroutineScope) {
    private val store = NfcMusicBindingStore(context)
    private val library = NfcMusicLibrary(EchoLibraryDatabase.create(context))
    private val adapter = NfcAdapter.getDefaultAdapter(context.applicationContext)
    val supported get() = adapter != null
    val enabled get() = adapter?.isEnabled == true
    private val launchAllowed get() = Build.VERSION.SDK_INT < 36 || adapter?.isTagIntentAllowed != false
    private val _state = MutableStateFlow<NfcMusicUiState?>(null)
    val state = _state.asStateFlow()
    private val _play = MutableStateFlow<String?>(null)
    val play = _play.asStateFlow()
    private var token: String? = null
    private var target: NfcMusicBinding? = null
    private var approvedContent: ByteArray? = null
    private var approvedTag: ByteArray? = null
    private var overwriteApproved = false
    private var verifyPending = false
    private var operation: Job? = null
    private var search: Job? = null
    private var lastToken: String? = null
    private var lastScan = 0L
    private var retryOpen = false
    private var tokenOnTag = false
    private var picking = false
    private var searchQuery = ""
    val reading get() = when (_state.value?.phase) {
        NfcMusicPhase.Waiting, NfcMusicPhase.Verify -> true
        NfcMusicPhase.Working -> !retryOpen
        else -> false
    }

    fun prepare(mediaId: String, label: String) {
        if (_state.value?.phase == NfcMusicPhase.Working) return
        dismiss()
        token = NfcMusicTag.newToken()
        target = NfcMusicBinding(mediaId, label.take(200).ifBlank { "ECHO" })
        retryOpen = false
        tokenOnTag = false
        waitForTag()
    }

    fun open(scannedToken: String) {
        // Releasing reader mode can redispatch the tag still in the field. Keep the active dialog intact.
        if (!NfcMusicTag.validToken(scannedToken) || _state.value != null) return
        val now = SystemClock.elapsedRealtime()
        if (lastToken == scannedToken && now - lastScan < 2500) return
        lastToken = scannedToken; lastScan = now
        dismiss()
        token = scannedToken
        retryOpen = true
        tokenOnTag = true
        resolve()
    }

    private fun resolve() {
        _state.value = NfcMusicUiState(NfcMusicPhase.Working)
        operation = scope.launch {
            try {
                target = store.find(checkNotNull(token))
                val binding = target
                if (binding != null && library.available(binding.mediaId)) {
                    _state.value = null
                    _play.value = binding.mediaId
                } else _state.value = NfcMusicUiState(NfcMusicPhase.Missing, binding?.label.orEmpty())
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { fail(NfcMusicProblem.Load) }
        }
    }

    fun consumePlay() { _play.value = null }
    fun dismiss() {
        operation?.cancel(); search?.cancel()
        _state.value = null
        token = null; target = null
        approvedTag = null; approvedContent = null; overwriteApproved = false; verifyPending = false; tokenOnTag = false
        picking = false; searchQuery = ""
    }

    fun retry() {
        when {
            picking -> query(searchQuery)
            retryOpen -> resolve()
            else -> waitForTag()
        }
    }

    fun refreshHardware() {
        when (_state.value?.phase) {
            NfcMusicPhase.Waiting, NfcMusicPhase.Verify, NfcMusicPhase.Disabled, NfcMusicPhase.LaunchBlocked -> waitForTag()
            else -> Unit
        }
    }

    private fun waitForTag() {
        _state.value = NfcMusicUiState(when {
            !supported -> NfcMusicPhase.Unsupported
            !enabled -> NfcMusicPhase.Disabled
            !launchAllowed -> NfcMusicPhase.LaunchBlocked
            verifyPending -> NfcMusicPhase.Verify
            else -> NfcMusicPhase.Waiting
        }, target?.label.orEmpty())
    }

    fun readerFailed() { fail(NfcMusicProblem.Disconnected) }

    /** Called on the main thread after ReaderCallback, serializing tag operations. */
    fun onTag(tag: Tag) {
        val phase = _state.value?.phase
        if (phase != NfcMusicPhase.Waiting && phase != NfcMusicPhase.Verify) return
        val binding = target ?: return
        val writingToken = token ?: return
        _state.value = NfcMusicUiState(NfcMusicPhase.Working, binding.label)
        operation = scope.launch {
            try {
                if (!library.available(binding.mediaId)) {
                    retryOpen = true
                    _state.value = NfcMusicUiState(NfcMusicPhase.Missing, binding.label)
                    return@launch
                }
                if (verifyPending) {
                    if (!withContext(Dispatchers.IO) { NfcMusicWriter.verify(tag, writingToken) })
                        throw NfcWriteException(NfcWriteFailure.Changed)
                } else {
                    val message = NfcMusicTag.message(writingToken)
                    val inspection = withContext(Dispatchers.IO) { NfcMusicWriter.inspect(tag, message) }
                    if ((inspection.existing != null || inspection.needsFormatting) && !overwriteApproved) {
                        approvedContent = inspection.existing
                        approvedTag = tag.id.clone()
                        _state.value = NfcMusicUiState(NfcMusicPhase.Overwrite, binding.label)
                        return@launch
                    }
                    if (overwriteApproved && approvedTag?.contentEquals(tag.id) != true)
                        throw NfcWriteException(NfcWriteFailure.Changed)
                    val verified = withContext(Dispatchers.IO) {
                        NfcMusicWriter.write(tag, message, if (overwriteApproved) approvedContent else null)
                    }
                    if (!verified) {
                        verifyPending = true
                        waitForTag()
                        return@launch
                    }
                }
                tokenOnTag = true
                verifyPending = true
                // Written tags remain recoverable through rebind if local persistence fails.
                try { store.save(writingToken, binding) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { throw NfcWriteException(NfcWriteFailure.Storage) }
                _state.value = NfcMusicUiState(NfcMusicPhase.Success, binding.label)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                fail(when ((error as? NfcWriteException)?.reason) {
                    NfcWriteFailure.ReadOnly -> NfcMusicProblem.ReadOnly
                    NfcWriteFailure.TooSmall -> NfcMusicProblem.TooSmall
                    NfcWriteFailure.Unsupported -> NfcMusicProblem.Unsupported
                    NfcWriteFailure.Changed -> NfcMusicProblem.Changed
                    NfcWriteFailure.Storage -> NfcMusicProblem.Storage
                    else -> NfcMusicProblem.Disconnected
                })
                // A retry always asks for fresh overwrite approval.
                approvedContent = null; approvedTag = null; overwriteApproved = false
            }
        }
    }

    fun approveOverwrite() { overwriteApproved = true; waitForTag() }

    fun chooseBinding() {
        _state.value = NfcMusicUiState(NfcMusicPhase.Choosing)
        query("")
    }

    fun query(query: String) {
        search?.cancel()
        val safe = query.take(100)
        picking = true
        searchQuery = safe
        _state.value = NfcMusicUiState(NfcMusicPhase.Choosing, query = safe, loading = true)
        search = scope.launch {
            delay(200)
            try {
                val choices = library.choices(safe)
                _state.value = NfcMusicUiState(NfcMusicPhase.Choosing, query = safe, choices = choices)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { fail(NfcMusicProblem.Load) }
        }
    }

    fun bind(choice: NfcMusicChoice) {
        if (_state.value?.phase != NfcMusicPhase.Choosing) return
        search?.cancel()
        _state.value = NfcMusicUiState(NfcMusicPhase.Working, choice.label)
        operation = scope.launch {
            try {
                if (!library.available(choice.mediaId)) { fail(NfcMusicProblem.Empty); return@launch }
                val binding = NfcMusicBinding(choice.mediaId, choice.label.take(200).ifBlank { "ECHO" })
                if (!tokenOnTag) {
                    target = binding
                    picking = false
                    retryOpen = false
                    waitForTag()
                    return@launch
                }
                store.save(checkNotNull(token), binding)
                target = binding
                _state.value = NfcMusicUiState(NfcMusicPhase.Bound, binding.label)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { fail(NfcMusicProblem.Storage) }
        }
    }

    fun playBound() {
        val mediaId = target?.mediaId ?: return
        dismiss()
        _play.value = mediaId
    }

    private fun fail(problem: NfcMusicProblem) {
        _state.value = NfcMusicUiState(NfcMusicPhase.Error, target?.label.orEmpty(), problem = problem)
    }
}
