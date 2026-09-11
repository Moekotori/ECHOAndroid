package app.echo.android.playback

import android.media.AudioDeviceInfo
import app.echo.android.model.playback.EchoOutputDeviceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EchoOutputRoutePolicyTest {
    @Test
    fun classifiesCommonDeviceTypes() {
        assertEquals(EchoOutputDeviceKind.Speaker, EchoOutputRoutePolicy.kind(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
        assertEquals(EchoOutputDeviceKind.Wired, EchoOutputRoutePolicy.kind(AudioDeviceInfo.TYPE_WIRED_HEADPHONES))
        assertEquals(EchoOutputDeviceKind.Bluetooth, EchoOutputRoutePolicy.kind(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP))
        assertEquals(EchoOutputDeviceKind.Usb, EchoOutputRoutePolicy.kind(AudioDeviceInfo.TYPE_USB_DEVICE))
    }

    @Test
    fun prefersHeadphonesOverSpeaker() {
        val speaker = EchoOutputDeviceCandidate(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "Speaker")
        val bt = EchoOutputDeviceCandidate(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "WH-1000XM5", "AA:BB")
        val picked = EchoOutputRoutePolicy.pickDevice(listOf(speaker, bt))
        assertEquals("WH-1000XM5", picked?.name)
        assertEquals(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, picked?.type)
    }

    @Test
    fun preferUsbPicksDacWhenAsked() {
        val bt = EchoOutputDeviceCandidate(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "Buds")
        val usb = EchoOutputDeviceCandidate(AudioDeviceInfo.TYPE_USB_DEVICE, "Moondrop Dawn")
        val picked = EchoOutputRoutePolicy.pickDevice(listOf(bt, usb), preferUsb = true)
        assertEquals("Moondrop Dawn", picked?.name)
    }

    @Test
    fun emptyDeviceListIsNull() {
        assertNull(EchoOutputRoutePolicy.pickDevice(emptyList()))
    }

    @Test
    fun bluetoothCodecNamesCoverCommonTypes() {
        assertEquals("SBC", EchoOutputRoutePolicy.bluetoothCodecName(0))
        assertEquals("AAC", EchoOutputRoutePolicy.bluetoothCodecName(1))
        assertEquals("aptX", EchoOutputRoutePolicy.bluetoothCodecName(2))
        assertEquals("aptX HD", EchoOutputRoutePolicy.bluetoothCodecName(3))
        assertEquals("LDAC", EchoOutputRoutePolicy.bluetoothCodecName(4))
        assertNull(EchoOutputRoutePolicy.bluetoothCodecName(99))
    }

    @Test
    fun routeLabelIncludesNameAndCodec() {
        assertEquals(
            "bluetooth: WH-1000XM5 / LDAC",
            EchoOutputRoutePolicy.routeLabel(EchoOutputDeviceKind.Bluetooth, "WH-1000XM5", "LDAC"),
        )
        assertEquals("speaker", EchoOutputRoutePolicy.routeLabel(EchoOutputDeviceKind.Speaker, null, null))
    }
}
