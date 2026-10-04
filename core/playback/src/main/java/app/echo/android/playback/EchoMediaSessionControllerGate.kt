package app.echo.android.playback

import android.content.Context
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
    ) || isAllowed(context, controllerInfo, session)

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
        return isAllowedPackage(packageName)
    }
}
