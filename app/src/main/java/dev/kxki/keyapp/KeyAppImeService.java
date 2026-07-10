package dev.kxki.keyapp;

import android.content.SharedPreferences;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.text.InputType;
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

    private JapaneseImeController controller;
    private KeyboardView keyboard;
    private JapaneseImeController.State state;
    private long activeToken;
    private boolean forcedPrivate;

    @Override public void onCreate() {
        super.onCreate();
        controller = new JapaneseImeController(this, this);
    }

    @Override public View onCreateInputView() {
        keyboard = new KeyboardView(this);
        keyboard.setAction(this);
        if (state != null) applyState(state);
        if (Build.VERSION.SDK_INT >= 31) {
            Window window = getWindow().getWindow();
            window.setBackgroundBlurRadius(30);
            window.getDecorView().setBackgroundColor(android.graphics.Color.TRANSPARENT);
        }
        return keyboard;
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
        controller.close();
        super.onDestroy();
    }

    @Override public void onKana(String kana) { controller.kana(kana); }
    @Override public void onKanaModifier(KeyboardView.KanaModifier modifier) { controller.modifyKana(modifier); }
    @Override public void onBackspace(boolean japanese) { controller.backspace(japanese); }
    @Override public void onSpace(boolean japanese) { controller.space(japanese); }
    @Override public void onEnter() { controller.enter(); }
    @Override public void onRawText(String text) { controller.rawText(text); }
    @Override public void onJapaneseChanged(boolean enabled) { controller.setJapanese(enabled); }
    @Override public void onCandidate(int candidateId) { controller.selectCandidate(candidateId); }

    @Override public void onPrivateModeToggle() {
        SharedPreferences preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE);
        boolean manual = !preferences.getBoolean(MANUAL_PRIVATE, false);
        preferences.edit().putBoolean(MANUAL_PRIVATE, manual).apply();
        controller.setPrivateMode(forcedPrivate || manual);
    }

    @Override public void onComposing(long token, String text) {
        InputConnection connection = connection(token);
        if (connection != null) connection.setComposingText(text, 1);
    }

    @Override public void onCommit(long token, String text) {
        InputConnection connection = connection(token);
        if (connection != null) connection.commitText(text, 1);
    }

    @Override public void onDeleteBefore(long token, int length) {
        InputConnection connection = connection(token);
        if (connection != null) connection.deleteSurroundingText(length, 0);
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

    @Override public void onState(long token, JapaneseImeController.State next) {
        if (token != activeToken || activeToken == 0) return;
        state = next;
        applyState(next);
    }

    private void applyState(JapaneseImeController.State next) {
        if (keyboard == null) return;
        List<KeyboardView.Candidate> display = new ArrayList<>(next.candidates.size());
        for (JapaneseImeController.Candidate candidate : next.candidates) {
            display.add(new KeyboardView.Candidate(candidate.id, candidate.value, candidate.label));
        }
        keyboard.setMozcState(display, next.available, next.privateMode);
    }

    private InputConnection connection(long token) {
        return token == activeToken && activeToken != 0 ? getCurrentInputConnection() : null;
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
