package dev.kxki.keyapp;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import java.util.ArrayList;
import java.util.List;

import dev.kxki.keyapp.clipboard.ClipboardCaptureController;
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
        if (keyboard != null) keyboard.resetForInput();
        clipboard.capture();
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
        clipboard.stop();
        controller.close();
        super.onDestroy();
    }

    @Override public void onKana(String kana) { controller.kana(kana); }
    @Override public void onKanaModifier() { controller.modifyKana(); }
    @Override public void onBackspace(boolean japanese) { controller.backspace(japanese); }
    @Override public void onDeleteRange(DeleteRange range) { controller.deleteRange(range); }
    @Override public void onSpace(boolean japanese) { controller.space(japanese); }
    @Override public void onCursorMove(int horizontal, int vertical) {
        controller.moveCursor(horizontal, vertical);
    }
    @Override public void onEnter() { controller.enter(); }
    @Override public void onRawText(String text) { controller.rawText(text); }
    @Override public void onJapaneseChanged(boolean enabled) { controller.setJapanese(enabled); }
    @Override public void onCandidate(int candidateId) { controller.selectCandidate(candidateId); }

    @Override public void onClipboardClear() {
        clipboard.clearHistory();
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
    }

    @Override public void onDeleteBefore(long token, int length) {
        InputConnection connection = connection(token);
        if (connection != null) connection.deleteSurroundingText(length, 0);
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

    private void loadKeyboardSize() {
        if (keyboard != null) keyboard.setKeyboardFrame(
                KeyboardPrefsRepository.loadWidth(this),
                KeyboardPrefsRepository.loadHeight(this, 300),
                KeyboardPrefsRepository.loadLeft(this),
                KeyboardPrefsRepository.loadBottomGap(this));
    }
}
