package app.echo.android.playback

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession

@UnstableApi
object EchoMediaSessionControllerGate {
    private val AllowedExternalPackages = setOf(
        "com.google.android.projection.gearhead",
        "com.google.android.gms",
        "com.android.bluetooth",
        "com.google.android.bluetooth",
        "com.android.systemui",
        "com.google.android.googlequicksearchbox",
        "com.google.android.wearable.app",
        "com.google.android.carassistant",
        "com.google.android.apps.googleassistant",
        "com.google.android.apps.automotive.templates.host",
    )

    fun isAllowedPackage(packageName: String): Boolean =
        packageName in AllowedExternalPackages

    internal fun isAllowedServiceRequest(
        context: Context,
        controllerInfo: MediaSession.ControllerInfo,
        session: MediaSession? = null,
    ): Boolean = isLegacyBrowserServicePlaceholder(
        packageName = controllerInfo.packageName,
        uid = controllerInfo.uid,
        controllerVersion = controllerInfo.controllerVersion,
    ) || isLegacyTransportController(controllerInfo.uid, controllerInfo.controllerVersion) ||
        isAllowed(context, controllerInfo, session)

    // A platform session token already permits transport controls. Companion apps and
    // vendor media panels need not have privileged media/library access to use it.
    internal fun isLegacyTransportController(uid: Int, controllerVersion: Int): Boolean =
        uid >= 0 && controllerVersion == MediaSession.ControllerInfo.LEGACY_CONTROLLER_VERSION

    // Media3 supplies this synthetic identity from onBind before it knows the browser caller.
    // Only allow it when selecting the service session; onConnect must check the real caller.
    internal fun isLegacyBrowserServicePlaceholder(
        packageName: String,
        uid: Int,
        controllerVersion: Int,
    ): Boolean = packageName == "android.media.session.MediaController" &&
        uid == -1 &&
        controllerVersion == MediaSession.ControllerInfo.LEGACY_CONTROLLER_VERSION

    fun isAllowed(
        context: Context,
        controllerInfo: MediaSession.ControllerInfo,
        session: MediaSession? = null,
    ): Boolean {
        if (controllerInfo.isTrusted) return true
        if (controllerInfo.uid == Process.myUid() || controllerInfo.uid == Process.SYSTEM_UID) return true
        val packageName = controllerInfo.packageName
        if (packageName == context.applicationContext.packageName) return true
        if (session != null &&
            (session.isAutomotiveController(controllerInfo) || session.isAutoCompanionController(controllerInfo))
        ) {
            return true
        }
        return isAllowedPackage(packageName) || isInstalledXiaomiMediaController(context, controllerInfo)
    }

    private fun isInstalledXiaomiMediaController(
        context: Context,
        controllerInfo: MediaSession.ControllerInfo,
    ): Boolean {
        if (controllerInfo.packageName != XiaomiMediaControllerPackage) return false
        // Accept the preinstalled MiPlay service even when it is not marked trusted.
        // A package name alone must not grant access to the playback session/library.
        val info = try {
            context.packageManager.getApplicationInfo(controllerInfo.packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            return false
        }
        return isPreinstalledXiaomiMediaController(
            packageName = controllerInfo.packageName,
            controllerUid = controllerInfo.uid,
            installedUid = info.uid,
            applicationFlags = info.flags,
        )
    }

    internal fun isPreinstalledXiaomiMediaController(
        packageName: String,
        controllerUid: Int,
        installedUid: Int,
        applicationFlags: Int,
    ): Boolean = packageName == XiaomiMediaControllerPackage &&
        controllerUid >= 0 && controllerUid == installedUid &&
        applicationFlags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

    private const val XiaomiMediaControllerPackage = "com.milink.service"
}
