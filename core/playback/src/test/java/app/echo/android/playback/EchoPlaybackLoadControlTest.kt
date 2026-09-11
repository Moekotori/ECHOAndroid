package app.echo.android.playback

import androidx.media3.exoplayer.LoadControl
import org.junit.Assert.assertEquals
import org.junit.Test

class EchoPlaybackLoadControlTest {
    @Test
    fun implementsEveryCurrentMedia3CallbackInsteadOfInheritingLegacyDefaults() {
        // Media3's Java defaults compile successfully but can throw at player startup,
        // preparation, track selection, stop, or release. Check the actual interface contract.
        LoadControl::class.java.declaredMethods
            .filterNot { it.isAnnotationPresent(java.lang.Deprecated::class.java) }
            .forEach { callback ->
                val implementation = EchoPlaybackLoadControl::class.java.getMethod(
                    callback.name,
                    *callback.parameterTypes,
                )
                assertEquals(
                    "Media3 callback must not inherit its interface default: $callback",
                    EchoPlaybackLoadControl::class.java,
                    implementation.declaringClass,
                )
            }
    }
}
