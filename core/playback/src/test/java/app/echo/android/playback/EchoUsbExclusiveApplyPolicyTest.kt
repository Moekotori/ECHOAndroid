package app.echo.android.playback

import app.echo.android.usbaudio.UsbExclusiveOutputState
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
    fun disconnectWhileExclusiveRebuildsSink() {
        assertTrue(
            EchoUsbExclusiveApplyPolicy.shouldRebuildSinkAfterUsbRouteChange(
                exclusiveEnabled = true,
                wasConnected = true,
                isConnected = false,
                previouslyGranted = true,
                currentlyGranted = false,
            ),
        )
        assertFalse(
            EchoUsbExclusiveApplyPolicy.shouldRebuildSinkAfterUsbRouteChange(
                exclusiveEnabled = false,
                wasConnected = true,
                isConnected = false,
                previouslyGranted = true,
                currentlyGranted = false,
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

    @Test
    fun mixerFallbackOnlyWhenUsbIsMissingOrUnauthorized() {
        assertTrue(
            EchoUsbExclusiveApplyPolicy.shouldFallBackToMixer(
                connected = false,
                permissionGranted = false,
                openState = UsbExclusiveOutputState.DeviceUnavailable,
            ),
        )
        assertTrue(
            EchoUsbExclusiveApplyPolicy.shouldFallBackToMixer(
                connected = true,
                permissionGranted = false,
                openState = UsbExclusiveOutputState.PermissionDenied,
            ),
        )
        assertFalse(
            EchoUsbExclusiveApplyPolicy.shouldFallBackToMixer(
                connected = true,
                permissionGranted = true,
                openState = UsbExclusiveOutputState.OpenFailed,
            ),
        )
        assertFalse(
            EchoUsbExclusiveApplyPolicy.shouldFallBackToMixer(
                connected = true,
                permissionGranted = true,
                openState = UsbExclusiveOutputState.FormatUnavailable,
            ),
        )
        assertFalse(
            EchoUsbExclusiveApplyPolicy.shouldFallBackToMixer(
                connected = true,
                permissionGranted = true,
                openState = UsbExclusiveOutputState.UnsupportedTransport,
            ),
        )
    }
}
