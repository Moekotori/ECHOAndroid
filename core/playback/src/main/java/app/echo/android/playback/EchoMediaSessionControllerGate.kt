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
