package app.echo.android

import android.view.KeyEvent

/** Mapping only; the foreground/focus owner decides whether a command may run. */
internal fun echoKeyboardCommand(key: Int, ctrl: Boolean, shift: Boolean, alt: Boolean): Int? {
    if (!ctrl || alt) return null
    if (shift) return when (key) { KeyEvent.KEYCODE_DPAD_LEFT -> 8; KeyEvent.KEYCODE_DPAD_RIGHT -> 9; else -> null }
    return when (key) {
        KeyEvent.KEYCODE_1 -> 0; KeyEvent.KEYCODE_2 -> 1; KeyEvent.KEYCODE_3 -> 2; KeyEvent.KEYCODE_4 -> 3
        KeyEvent.KEYCODE_COMMA -> 4; KeyEvent.KEYCODE_SPACE -> 5
        KeyEvent.KEYCODE_DPAD_RIGHT -> 6; KeyEvent.KEYCODE_DPAD_LEFT -> 7
        KeyEvent.KEYCODE_F -> 10; KeyEvent.KEYCODE_SLASH -> 11
        else -> null
    }
}
