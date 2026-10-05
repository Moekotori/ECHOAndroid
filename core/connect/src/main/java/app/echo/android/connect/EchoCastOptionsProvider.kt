package app.echo.android.connect

import android.content.Context
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.media.NotificationOptions

/** SDK-only platform configuration; the app continues to own its LAN file publication. */
class EchoCastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions {
        val launcher = context.packageManager.getLaunchIntentForPackage(context.packageName)?.component?.className
        val notification = NotificationOptions.Builder().apply { launcher?.let(::setTargetActivityClassName) }.build()
        return CastOptions.Builder().setReceiverApplicationId(EchoChromecastCastPolicy.DefaultReceiverAppId)
            .setRemoteToLocalEnabled(true).setShowSystemOutputSwitcherOnCastIconClick(true)
            .setStopReceiverApplicationWhenEndingSession(true)
            .setCastMediaOptions(CastMediaOptions.Builder().setMediaSessionEnabled(true).setNotificationOptions(notification).build())
            .build()
    }

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
