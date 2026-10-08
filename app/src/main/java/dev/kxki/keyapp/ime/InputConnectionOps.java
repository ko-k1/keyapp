package dev.kxki.keyapp.ime;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.inputmethod.InputConnection;

/** Small helpers for sending DPAD cursor keys via {@link InputConnection}. */
public final class InputConnectionOps {
    private InputConnectionOps() {}

    public static void sendCursorKeys(
            InputConnection connection, int movement, int negativeKey, int positiveKey) {
        int keyCode = movement < 0 ? negativeKey : positiveKey;
        for (int i = 0; i < Math.abs(movement); i++) {
            long time = SystemClock.uptimeMillis();
            connection.sendKeyEvent(new KeyEvent(time, time, KeyEvent.ACTION_DOWN, keyCode, 0, 0,
                    KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_SOFT_KEYBOARD,
                    InputDevice.SOURCE_KEYBOARD));
            connection.sendKeyEvent(new KeyEvent(time, time, KeyEvent.ACTION_UP, keyCode, 0, 0,
                    KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_SOFT_KEYBOARD,
                    InputDevice.SOURCE_KEYBOARD));
        }
    }

    public static void sendCursorLeftRight(InputConnection connection, int horizontal) {
        sendCursorKeys(connection, horizontal, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT);
    }

    public static void sendCursorUpDown(InputConnection connection, int vertical) {
        sendCursorKeys(connection, vertical, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN);
    }
}
