package app.echo.android.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoUsbExclusiveApplyPolicyTest {
    @Test
    fun permissionGrantWhileExclusiveReapplies() {
        assertTrue(
            EchoUsbExclusiveApplyPolicy.shouldReapplyAfterHostPermissionGranted(
                exclusiveEnabled = true,
                previouslyGranted = false,
                currentlyGranted = true,
            ),
        )
    }

    @Test
    fun alreadyGrantedOrDisabledDoesNotReapply() {
        assertFalse(
            EchoUsbExclusiveApplyPolicy.shouldReapplyAfterHostPermissionGranted(
                exclusiveEnabled = true,
                previouslyGranted = true,
                currentlyGranted = true,
            ),
        )
        assertFalse(
            EchoUsbExclusiveApplyPolicy.shouldReapplyAfterHostPermissionGranted(
                exclusiveEnabled = false,
                previouslyGranted = false,
                currentlyGranted = true,
            ),
        )
        assertFalse(
            EchoUsbExclusiveApplyPolicy.shouldReapplyAfterHostPermissionGranted(
                exclusiveEnabled = true,
                previouslyGranted = false,
                currentlyGranted = false,
            ),
        )
    }
}
