package app.echo.android

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.echo.android.data.update.GithubUpdate
import app.echo.android.data.update.GithubUpdateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import app.echo.android.feature.settings.UpdateProblem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class EchoUpdateState(val visible: Boolean = false, val busy: Boolean = false,
    val update: GithubUpdate? = null, val progress: Int? = null, val apk: File? = null,
    val error: UpdateProblem? = null, val checked: Boolean = false)

class EchoUpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GithubUpdateRepository(application)
    private val mutableState = MutableStateFlow(EchoUpdateState())
    val state = mutableState.asStateFlow()
    private var autoInstallRequested = false
    private var mayPrompt = true
    private var operation: Job? = null
    init { check(false) }

    fun open() {
        if (mutableState.value.update != null || mutableState.value.busy) show() else check()
    }
    fun consumeAutoInstall(): Boolean {
        if (autoInstallRequested || !mutableState.value.visible) return false
        autoInstallRequested = true
        return true
    }

    fun dismiss() { mayPrompt = false; mutableState.update { it.copy(visible = false) } }
    fun show() { mayPrompt = true; mutableState.update { it.copy(visible = true) } }
    fun installFailed(permission: Boolean = false) {
        mutableState.update { it.copy(error = if (permission) UpdateProblem.Permission else UpdateProblem.Install, busy = false) }
    }
    fun installing() { mutableState.update { it.copy(error = null) } }
    fun cancelDownload() {
        operation?.cancel()
        repository.cancelPendingRequests()
    }
    override fun onCleared() {
        repository.cancelPendingRequests()
        super.onCleared()
    }
    fun check(manual: Boolean = true) {
        if (mutableState.value.busy) { if (manual) show(); return }
        mayPrompt = true
        mutableState.value = EchoUpdateState(visible = manual, busy = true)
        operation = viewModelScope.launch {
            try {
                val update = repository.check(BuildConfig.VERSION_CODE.toLong(), manual)
                mutableState.value = EchoUpdateState(visible = mayPrompt && (mutableState.value.visible || update != null), update = update, checked = true)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutableState.value = EchoUpdateState(visible = mutableState.value.visible && mayPrompt, error = UpdateProblem.Check) }
        }
    }

    fun download() {
        val update = mutableState.value.update ?: return
        if (mutableState.value.busy) return
        autoInstallRequested = false
        mutableState.value = mutableState.value.copy(busy = true, error = null, progress = 0)
        operation = viewModelScope.launch {
            var verifying = false
            try {
                val apk = repository.download(update) { percent ->
                    mutableState.update { it.copy(progress = percent) }
                }
                verifying = true
                withContext(Dispatchers.IO) { verifyApk(apk, update.versionCode) }
                mutableState.value = mutableState.value.copy(busy = false, progress = null, apk = apk)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                mutableState.value = mutableState.value.copy(busy = false, progress = null, apk = null,
                    error = if (verifying || e is IllegalArgumentException) UpdateProblem.Verification else UpdateProblem.Download)
            } finally {
                mutableState.update { it.copy(busy = false, progress = null) }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun verifyApk(file: File, expectedCode: Long) {
        val context = getApplication<Application>()
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = requireNotNull(pm.getPackageArchiveInfo(file.path, flags))
        val installed = pm.getPackageInfo(context.packageName, flags)
        val code = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        require(archive.packageName == context.packageName && code == expectedCode && code > BuildConfig.VERSION_CODE)
        // Require the same current signers. Key rotation needs an explicit migration.
        val incoming = if (Build.VERSION.SDK_INT >= 28) archive.signingInfo?.apkContentsSigners else archive.signatures
        val current = if (Build.VERSION.SDK_INT >= 28) installed.signingInfo?.apkContentsSigners else installed.signatures
        require(!incoming.isNullOrEmpty() && !current.isNullOrEmpty() && incoming.toSet() == current.toSet())
    }
}
