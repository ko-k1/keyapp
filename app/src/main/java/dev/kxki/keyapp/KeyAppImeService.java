package dev.kxki.keyapp;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.SystemClock;
import android.text.InputType;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import java.util.ArrayList;
import java.util.List;

public final class KeyAppImeService extends InputMethodService
        implements KeyboardView.OnKeyboardAction, JapaneseImeController.Listener {
    static final String PREFERENCES = "keyapp";
    static final String MANUAL_PRIVATE = "manual_private";
    static final String KEY_GAP = "key_gap";
    static final String KEY_RADIUS = "key_radius";
    private static final String CLIPBOARD_COUNT = "clipboard_count";
    private static final String CLIPBOARD_ITEM = "clipboard_item_";

    private JapaneseImeController controller;
    private KeyboardView keyboard;
    private JapaneseImeController.State state;
    private long activeToken;
    private boolean forcedPrivate;
    private ClipboardManager clipboardManager;
    private ClipboardManager.OnPrimaryClipChangedListener clipboardListener;
    private final List<String> clipboardHistory = new ArrayList<>();

    @Override public void onCreate() {
        super.onCreate();
        controller = new JapaneseImeController(this, this);
        loadClipboardHistory();
        clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboardManager != null) {
            clipboardListener = this::captureClipboard;
            clipboardManager.addPrimaryClipChangedListener(clipboardListener);
            captureClipboard();
        }
    }

    @Override public View onCreateInputView() {
        keyboard = new KeyboardView(this);
        keyboard.setAction(this);
        keyboard.setClipboardHistory(clipboardHistory);
        loadKeyboardStyle(this, keyboard);
        loadKeyboardSize();
        if (state != null) applyState(state);
        Window window = getWindow().getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setBackgroundColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 31) {
            window.setBackgroundBlurRadius(30);
        }
        return keyboard;
    }

    @Override public void onWindowShown() {
        super.onWindowShown();
        loadKeyboardStyle(this, keyboard);
        loadKeyboardSize();
    }

    @Override public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        forcedPrivate = isSensitive(attribute);
        SharedPreferences preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE);
        activeToken = controller.begin(forcedPrivate || preferences.getBoolean(MANUAL_PRIVATE, false));
        if (keyboard != null) keyboard.resetForInput();
    }

    @Override public void onFinishInput() {
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) connection.finishComposingText();
        controller.abandon();
        activeToken = 0;
        state = null;
        super.onFinishInput();
    }

    @Override public void onWindowHidden() {
        controller.finish();
        super.onWindowHidden();
    }

    @Override public void onDestroy() {
        if (clipboardManager != null && clipboardListener != null) {
            clipboardManager.removePrimaryClipChangedListener(clipboardListener);
        }
        controller.close();
        super.onDestroy();
    }

    @Override public void onKana(String kana) { controller.kana(kana); }
    @Override public void onKanaModifier() { controller.modifyKana(); }
    @Override public void onBackspace(boolean japanese) { controller.backspace(japanese); }
    @Override public void onDeleteRange(KeyboardView.DeleteRange range) { controller.deleteRange(range); }
    @Override public void onSpace(boolean japanese) { controller.space(japanese); }
    @Override public void onCursorMove(int horizontal, int vertical) {
        controller.moveCursor(horizontal, vertical);
    }
    @Override public void onEnter() { controller.enter(); }
    @Override public void onRawText(String text) { controller.rawText(text); }
    @Override public void onJapaneseChanged(boolean enabled) { controller.setJapanese(enabled); }
    @Override public void onCandidate(int candidateId) { controller.selectCandidate(candidateId); }

    @Override public void onClipboardClear() {
        clipboardHistory.clear();
        saveClipboardHistory();
        if (clipboardManager != null) {
            if (Build.VERSION.SDK_INT >= 28) clipboardManager.clearPrimaryClip();
            else clipboardManager.setPrimaryClip(ClipData.newPlainText("", ""));
        }
        updateClipboardView();
    }

    @Override public void onPrivateModeToggle() {
        SharedPreferences preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE);
        boolean manual = !preferences.getBoolean(MANUAL_PRIVATE, false);
        preferences.edit().putBoolean(MANUAL_PRIVATE, manual).apply();
        controller.setPrivateMode(forcedPrivate || manual);
    }

    @Override public void onComposing(long token, String text, int cursor) {
        InputConnection connection = connection(token);
        if (connection == null) return;
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

    @Override public void onCommit(long token, String text) {
        InputConnection connection = connection(token);
        if (connection != null) connection.commitText(text, 1);
    }

    @Override public void onDeleteBefore(long token, int length) {
        InputConnection connection = connection(token);
        if (connection != null) connection.deleteSurroundingText(length, 0);
    }

    @Override public void onDeleteRange(long token, KeyboardView.DeleteRange range) {
        InputConnection connection = connection(token);
        if (connection == null) return;
        CharSequence before = range == KeyboardView.DeleteRange.TO_END
                ? null : connection.getTextBeforeCursor(Integer.MAX_VALUE, 0);
        CharSequence after = range == KeyboardView.DeleteRange.TO_START
                ? null : connection.getTextAfterCursor(Integer.MAX_VALUE, 0);
        connection.deleteSurroundingText(
                before == null ? 0 : before.length(), after == null ? 0 : after.length());
    }

    @Override public void onFinishComposing(long token) {
        InputConnection connection = connection(token);
        if (connection != null) connection.finishComposingText();
    }

    @Override public void onEditorAction(long token) {
        InputConnection connection = connection(token);
        if (connection == null) return;
        EditorInfo info = getCurrentInputEditorInfo();
        int action = info == null ? EditorInfo.IME_ACTION_UNSPECIFIED
                : info.imeOptions & EditorInfo.IME_MASK_ACTION;
        if (action != EditorInfo.IME_ACTION_UNSPECIFIED && action != EditorInfo.IME_ACTION_NONE
                && info != null && (info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0) {
            connection.performEditorAction(action);
        } else {
            connection.commitText("\n", 1);
        }
    }

    @Override public void onEditorCursorMove(long token, int horizontal, int vertical) {
        InputConnection connection = connection(token);
        if (connection == null) return;
        sendCursorKeys(connection, horizontal, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT);
        sendCursorKeys(connection, vertical, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN);
    }

    @Override public void onState(long token, JapaneseImeController.State next) {
        if (token != activeToken || activeToken == 0) return;
        state = next;
        applyState(next);
    }

    private void applyState(JapaneseImeController.State next) {
        if (keyboard == null) return;
        List<KeyboardView.Candidate> display = new ArrayList<>(next.candidates.size());
        for (JapaneseImeController.Candidate candidate : next.candidates) {
            display.add(new KeyboardView.Candidate(
                    candidate.id, candidate.value, candidate.label, candidate.focused));
        }
        keyboard.setMozcState(display, next.available, next.privateMode);
    }

    private InputConnection connection(long token) {
        return token == activeToken && activeToken != 0 ? getCurrentInputConnection() : null;
    }

    private void captureClipboard() {
        if (clipboardManager == null || forcedPrivate || state != null && state.privateMode) return;
        ClipData clip;
        try {
            clip = clipboardManager.getPrimaryClip();
        } catch (SecurityException ignored) {
            return;
        }
        ClipDescription description = clip == null ? null : clip.getDescription();
        if (description != null && description.getExtras() != null
                && description.getExtras().getBoolean(ClipDescription.EXTRA_IS_SENSITIVE)) return;
        if (clip == null) return;
        boolean changed = false;
        for (int i = clip.getItemCount() - 1; i >= 0; i--) {
            CharSequence value;
            try {
                value = clip.getItemAt(i).coerceToText(this);
            } catch (RuntimeException ignored) {
                continue;
            }
            String text = value == null ? "" : value.toString();
            if (text.isEmpty()) continue;
            int previous = clipboardHistory.indexOf(text);
            if (previous == 0) continue;
            if (previous > 0) clipboardHistory.remove(previous);
            clipboardHistory.add(0, text);
            changed = true;
        }
        if (changed) {
            saveClipboardHistory();
            updateClipboardView();
        }
    }

    private void loadClipboardHistory() {
        SharedPreferences preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE);
        int count = preferences.getInt(CLIPBOARD_COUNT, 0);
        for (int i = 0; i < count; i++) {
            String value = preferences.getString(CLIPBOARD_ITEM + i, "");
            if (!value.isEmpty() && !clipboardHistory.contains(value)) clipboardHistory.add(value);
        }
    }

    private void saveClipboardHistory() {
        SharedPreferences preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE);
        int previous = preferences.getInt(CLIPBOARD_COUNT, 0);
        SharedPreferences.Editor editor = preferences.edit().putInt(CLIPBOARD_COUNT, clipboardHistory.size());
        for (int i = 0; i < clipboardHistory.size(); i++) {
            editor.putString(CLIPBOARD_ITEM + i, clipboardHistory.get(i));
        }
        for (int i = clipboardHistory.size(); i < previous; i++) editor.remove(CLIPBOARD_ITEM + i);
        editor.apply();
    }

    private void updateClipboardView() {
        if (keyboard != null) keyboard.setClipboardHistory(clipboardHistory);
    }

    private void loadKeyboardSize() {
        if (keyboard != null) keyboard.setKeyboardFrame(
                KeyboardResizeActivity.loadWidth(this),
                KeyboardResizeActivity.loadHeight(this),
                KeyboardResizeActivity.loadLeft(this),
                KeyboardResizeActivity.loadBottomGap(this));
    }

    static void loadKeyboardStyle(android.content.Context context, KeyboardView keyboard) {
        if (keyboard == null) return;
        SharedPreferences preferences = context.getSharedPreferences(PREFERENCES, MODE_PRIVATE);
        keyboard.setKeyStyle(
                preferences.getInt(KEY_GAP, KeyboardView.DEFAULT_KEY_GAP_DP),
                preferences.getInt(KEY_RADIUS, KeyboardView.DEFAULT_KEY_RADIUS_DP));
    }

    private static void sendCursorKeys(
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

    private static boolean isSensitive(EditorInfo info) {
        if (info == null) return false;
        if ((info.imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0) return true;
        int inputClass = info.inputType & InputType.TYPE_MASK_CLASS;
        int variation = info.inputType & InputType.TYPE_MASK_VARIATION;
        if (inputClass == InputType.TYPE_CLASS_NUMBER) {
            return variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD;
        }
        if (inputClass != InputType.TYPE_CLASS_TEXT) return false;
        return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
    }
}
