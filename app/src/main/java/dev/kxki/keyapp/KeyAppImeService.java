package dev.kxki.keyapp;

import android.content.SharedPreferences;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import java.util.ArrayList;
import java.util.List;

import dev.kxki.keyapp.clipboard.ClipboardCaptureController;
import dev.kxki.keyapp.english.EnglishTextRules;
import dev.kxki.keyapp.ime.ImeCandidate;
import dev.kxki.keyapp.ime.ImeListener;
import dev.kxki.keyapp.ime.ImeState;
import dev.kxki.keyapp.ime.InputConnectionOps;
import dev.kxki.keyapp.ime.TextEngine;
import dev.kxki.keyapp.ime.TextEngineFactory;
import dev.kxki.keyapp.keyboard.DeleteRange;
import dev.kxki.keyapp.prefs.KeyboardPrefsRepository;
import dev.kxki.keyapp.prefs.PrefsKeys;
import dev.kxki.keyapp.privacy.PrivacyPolicy;

public final class KeyAppImeService extends InputMethodService
        implements KeyboardView.OnKeyboardAction, ImeListener {

    private TextEngine controller;
    private KeyboardView keyboard;
    private ClipboardCaptureController clipboard;
    private ImeState state;
    private long activeToken;
    private boolean forcedPrivate;
    private boolean manualPrivate;
    private long lastSpaceMs = Long.MIN_VALUE;

    @Override public void onCreate() {
        super.onCreate();
        controller = TextEngineFactory.create(this, this);
        clipboard = new ClipboardCaptureController(this);
        clipboard.setOnChanged(this::updateClipboardView);
        clipboard.start();
        manualPrivate = KeyboardPrefsRepository.isManualPrivate(
                getSharedPreferences(PrefsKeys.PREFERENCES, MODE_PRIVATE));
        clipboard.setPrivate(manualPrivate);
    }

    @Override public View onCreateInputView() {
        keyboard = new KeyboardView(this);
        keyboard.setAction(this);
        keyboard.setClipboardHistory(clipboard.history());
        KeyboardPrefsRepository.applyTo(
                getSharedPreferences(PrefsKeys.PREFERENCES, MODE_PRIVATE), keyboard);
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
        KeyboardPrefsRepository.applyTo(
                getSharedPreferences(PrefsKeys.PREFERENCES, MODE_PRIVATE), keyboard);
        loadKeyboardSize();
        manualPrivate = KeyboardPrefsRepository.isManualPrivate(
                getSharedPreferences(PrefsKeys.PREFERENCES, MODE_PRIVATE));
        clipboard.setPrivate(forcedPrivate || manualPrivate);
        clipboard.capture();
    }

    @Override public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        forcedPrivate = PrivacyPolicy.isSensitive(attribute);
        SharedPreferences preferences = getSharedPreferences(PrefsKeys.PREFERENCES, MODE_PRIVATE);
        manualPrivate = KeyboardPrefsRepository.isManualPrivate(preferences);
        clipboard.setPrivate(forcedPrivate || manualPrivate);
        activeToken = controller.begin(forcedPrivate || manualPrivate);
        lastSpaceMs = Long.MIN_VALUE;
        if (keyboard != null) keyboard.resetForInput();
        clipboard.capture();
        refreshAutoCaps();
    }

    @Override public void onFinishInput() {
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) connection.finishComposingText();
        controller.abandon();
        activeToken = 0;
        state = null;
        lastSpaceMs = Long.MIN_VALUE;
        super.onFinishInput();
    }

    @Override public void onWindowHidden() {
        controller.finish();
        super.onWindowHidden();
    }

    @Override public void onDestroy() {
        clipboard.stop();
        controller.close();
        super.onDestroy();
    }

    @Override public void onKana(String kana) { lastSpaceMs = Long.MIN_VALUE; controller.kana(kana); }
    @Override public void onKanaModifier() { controller.modifyKana(); }
    @Override public void onBackspace(boolean japanese) {
        lastSpaceMs = Long.MIN_VALUE;
        controller.backspace(japanese);
    }
    @Override public void onDeleteRange(DeleteRange range) {
        lastSpaceMs = Long.MIN_VALUE;
        controller.deleteRange(range);
    }
    @Override public void onSpace(boolean japanese) {
        if (!japanese && tryDoubleSpacePeriod()) return;
        lastSpaceMs = japanese ? Long.MIN_VALUE : SystemClock.uptimeMillis();
        controller.space(japanese);
    }
    @Override public void onCursorMove(int horizontal, int vertical) {
        controller.moveCursor(horizontal, vertical);
    }
    @Override public void onEnter() {
        lastSpaceMs = Long.MIN_VALUE;
        controller.enter();
    }
    @Override public void onRawText(String text) {
        lastSpaceMs = Long.MIN_VALUE;
        controller.rawText(text);
    }
    @Override public void onJapaneseChanged(boolean enabled) { controller.setJapanese(enabled); }
    @Override public void onCandidate(int candidateId) { controller.selectCandidate(candidateId); }

    @Override public void onClipboardClear() {
        clipboard.clearHistory();
    }

    @Override public void onAddWordRequested() {
        String reading = "";
        try {
            reading = controller.composingReading();
        } catch (RuntimeException ignored) {
            reading = "";
        }
        Intent intent = new Intent(this, UserDictionaryActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (reading != null && !reading.isEmpty()) {
            intent.putExtra(UserDictionaryActivity.EXTRA_READING, reading);
        }
        try {
            startActivity(intent);
        } catch (RuntimeException ignored) {
            // Never break typing when the activity cannot start.
        }
    }

    @Override public void onPrivateModeToggle() {
        SharedPreferences preferences = getSharedPreferences(PrefsKeys.PREFERENCES, MODE_PRIVATE);
        boolean manual = !KeyboardPrefsRepository.isManualPrivate(preferences);
        KeyboardPrefsRepository.setManualPrivate(preferences, manual);
        manualPrivate = manual;
        clipboard.setPrivate(forcedPrivate || manual);
        controller.setPrivateMode(forcedPrivate || manual);
    }

    @Override public void onComposing(long token, String text, int cursor) {
        InputConnection connection = connection(token);
        if (connection == null) return;
        InputConnectionOps.setComposingText(connection, text, cursor);
    }

    @Override public void onCommit(long token, String text) {
        InputConnection connection = connection(token);
        if (connection != null) connection.commitText(text, 1);
        refreshAutoCaps();
    }

    @Override public void onDeleteBefore(long token, int length) {
        InputConnection connection = connection(token);
        if (connection != null) connection.deleteSurroundingText(length, 0);
        refreshAutoCaps();
    }

    @Override public void onDeleteRange(long token, DeleteRange range) {
        InputConnection connection = connection(token);
        if (connection == null) return;
        InputConnectionOps.deleteRange(connection, range);
    }

    @Override public void onFinishComposing(long token) {
        InputConnection connection = connection(token);
        if (connection != null) connection.finishComposingText();
    }

    @Override public void onEditorAction(long token) {
        if (connection(token) == null) return;
        sendKeyChar('\n');
    }

    @Override public void onEditorCursorMove(long token, int horizontal, int vertical) {
        InputConnection connection = connection(token);
        if (connection == null) return;
        InputConnectionOps.sendCursorLeftRight(connection, horizontal);
        InputConnectionOps.sendCursorUpDown(connection, vertical);
    }

    @Override public void onState(long token, ImeState next) {
        if (token != activeToken || activeToken == 0) return;
        state = next;
        applyState(next);
    }

    private void applyState(ImeState next) {
        if (keyboard == null) return;
        List<KeyboardView.Candidate> display = new ArrayList<>(next.candidates.size());
        for (ImeCandidate candidate : next.candidates) {
            display.add(new KeyboardView.Candidate(
                    candidate.id, candidate.value, candidate.label, candidate.focused));
        }
        keyboard.setMozcState(display, next.available, next.privateMode);
    }

    private InputConnection connection(long token) {
        return token == activeToken && activeToken != 0 ? getCurrentInputConnection() : null;
    }

    private void updateClipboardView() {
        if (keyboard != null) keyboard.setClipboardHistory(clipboard.history());
    }

    /** Converts a fast second space into ". " on the English panel. */
    private boolean tryDoubleSpacePeriod() {
        SharedPreferences preferences =
                getSharedPreferences(PrefsKeys.PREFERENCES, MODE_PRIVATE);
        if (!KeyboardPrefsRepository.doubleSpacePeriodEnabled(preferences, true)) return false;
        InputConnection connection = getCurrentInputConnection();
        if (connection == null || activeToken == 0) return false;
        CharSequence before;
        try {
            before = connection.getTextBeforeCursor(2, 0);
        } catch (Exception ignored) {
            return false;
        }
        long now = SystemClock.uptimeMillis();
        if (!EnglishTextRules.isDoubleSpace(now, lastSpaceMs, before)) return false;
        try {
            connection.beginBatchEdit();
            connection.deleteSurroundingText(1, 0);
            connection.commitText(". ", 1);
        } catch (RuntimeException ignored) {
            return false;
        } finally {
            try {
                connection.endBatchEdit();
            } catch (RuntimeException ignored) {
                // Batch bookkeeping must never break typing.
            }
        }
        lastSpaceMs = Long.MIN_VALUE;
        refreshAutoCaps();
        return true;
    }

    /** Enables shift at sentence starts on the English panel. */
    private void refreshAutoCaps() {
        if (keyboard == null || !keyboard.isEnglishPanel()) return;
        SharedPreferences preferences =
                getSharedPreferences(PrefsKeys.PREFERENCES, MODE_PRIVATE);
        if (!KeyboardPrefsRepository.autoCapsEnabled(preferences, false)) return;
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) return;
        CharSequence before;
        try {
            before = connection.getTextBeforeCursor(EnglishTextRules.AUTO_CAPS_LOOKBACK, 0);
        } catch (Exception ignored) {
            return;
        }
        if (before == null) return;
        keyboard.setShiftForAutoCaps(EnglishTextRules.shouldAutoCapitalize(before));
    }

    private void loadKeyboardSize() {
        if (keyboard != null) keyboard.setKeyboardFrame(
                KeyboardPrefsRepository.loadWidth(this),
                KeyboardPrefsRepository.loadHeight(this, 300),
                KeyboardPrefsRepository.loadLeft(this),
                KeyboardPrefsRepository.loadBottomGap(this));
    }
}
