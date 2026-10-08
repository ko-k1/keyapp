package dev.kxki.keyapp.ime;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.inputmethod.InputConnection;

import dev.kxki.keyapp.keyboard.DeleteRange;

/** Small helpers for driving an {@link InputConnection} from IME callbacks. */
public final class InputConnectionOps {
    private InputConnectionOps() {}

    /** Sets composing text and restores the in-preedit cursor reported by the engine. */
    public static void setComposingText(InputConnection connection, String text, int cursor) {
        connection.beginBatchEdit();
        connection.setComposingText(text, 1);
        if (cursor < text.codePointCount(0, text.length())) {
            CharSequence before = connection.getTextBeforeCursor(Integer.MAX_VALUE, 0);
            if (before != null) {
                int trailing = text.length() - text.offsetByCodePoints(0, cursor);
                int position = before.length() - trailing;
                if (position >= 0) connection.setSelection(position, position);
            }
        }
        connection.endBatchEdit();
    }

    /** Deletes editor text around the cursor for a backspace-flick range. */
    public static void deleteRange(InputConnection connection, DeleteRange range) {
        CharSequence before = range == DeleteRange.TO_END
                ? null : connection.getTextBeforeCursor(Integer.MAX_VALUE, 0);
        CharSequence after = range == DeleteRange.TO_START
                ? null : connection.getTextAfterCursor(Integer.MAX_VALUE, 0);
        connection.deleteSurroundingText(
                before == null ? 0 : before.length(), after == null ? 0 : after.length());
    }

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
