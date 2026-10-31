package app.echo.android

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test

class EchoKeyboardCommandTest {
    @Test fun textKeysRemainUntouchedAndModifiedArrowsAreDistinct() {
        assertNull(echoKeyboardCommand(KeyEvent.KEYCODE_F, false, false, false))
        assertNull(echoKeyboardCommand(KeyEvent.KEYCODE_SPACE, true, false, true))
        assertEquals(6, echoKeyboardCommand(KeyEvent.KEYCODE_DPAD_RIGHT, true, false, false))
        assertEquals(9, echoKeyboardCommand(KeyEvent.KEYCODE_DPAD_RIGHT, true, true, false))
        assertEquals(11, echoKeyboardCommand(KeyEvent.KEYCODE_SLASH, true, false, false))
    }
}
