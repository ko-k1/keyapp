package dev.kxki.keyapp;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
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

import dev.kxki.keyapp.clipboard.ClipboardRepository;
import dev.kxki.keyapp.ime.InputConnectionOps;
import dev.kxki.keyapp.prefs.KeyboardPrefsRepository;
import dev.kxki.keyapp.prefs.PrefsKeys;
import dev.kxki.keyapp.privacy.PrivacyPolicy;

public final class KeyAppImeService extends InputMethodService
        implements KeyboardView.OnKeyboardAction, JapaneseImeController.Listener {

    private JapaneseImeController controller;
    private KeyboardView keyboard;
    private JapaneseImeController.State state;
    private long activeToken;
    private boolean forcedPrivate;
    private boolean manualPrivate;
    private ClipboardManager clipboardManager;
    private ClipboardManager.OnPrimaryClipChangedListener clipboardListener;
    private final List<String> clipboardHistory = new ArrayList<>();

    @Override public void onCreate() {
        super.onCreate();
        controller = new JapaneseImeController(this, this);
        loadClipboardHistory();
        manualPrivate = getSharedPreferences(PrefsKeys.PREFERENCES, MODE_PRIVATE)
                .getBoolean(PrefsKeys.MANUAL_PRIVATE, false);
        clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboardManager != null) {
            clipboardListener = this::captureClipboard;
            clipboardManager.addPrimaryClipChangedListener(clipboardListener);
            // Do not read the system clipboard here: there is no active input yet,
            // and on Android 12+ every read shows a system overlay. The latest clip
            // is captured in onStartInput/onWindowShown instead.
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
        manualPrivate = getSharedPreferences(PrefsKeys.PREFERENCES, MODE_PRIVATE)
                .getBoolean(PrefsKeys.MANUAL_PRIVATE, false);
        captureClipboard();
    }

    @Override public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        forcedPrivate = isSensitive(attribute);
        SharedPreferences preferences = getSharedPreferences(PrefsKeys.PREFERENCES, MODE_PRIVATE);
        manualPrivate = preferences.getBoolean(PrefsKeys.MANUAL_PRIVATE, false);
        activeToken = controller.begin(forcedPrivate || manualPrivate);
        if (keyboard != null) keyboard.resetForInput();
        captureClipboard();
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
        // History-only: never touch the system clipboard here. Wiping the shared
        // clipboard from a keyboard key is surprising and destructive.
        clipboardHistory.clear();
        saveClipboardHistory();
        updateClipboardView();
    }

    @Override public void onPrivateModeToggle() {
        SharedPreferences preferences = getSharedPreferences(PrefsKeys.PREFERENCES, MODE_PRIVATE);
        boolean manual = !preferences.getBoolean(PrefsKeys.MANUAL_PRIVATE, false);
        preferences.edit().putBoolean(PrefsKeys.MANUAL_PRIVATE, manual).apply();
        manualPrivate = manual;
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
        if (connection(token) == null) return;
        sendKeyChar('\n');
    }

    @Override public void onEditorCursorMove(long token, int horizontal, int vertical) {
        InputConnection connection = connection(token);
        if (connection == null) return;
        InputConnectionOps.sendCursorLeftRight(connection, horizontal);
        InputConnectionOps.sendCursorUpDown(connection, vertical);
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
        // Synchronous gate: forcedPrivate/manualPrivate are set in onStartInput and
        // onPrivateModeToggle on the same thread, so unlike controller state they
        // cannot be stale when a clip changes mid-transition. The panel itself stays
        // visible in private mode; only capturing is blocked.
        if (shouldBlockClipboardCapture(forcedPrivate, manualPrivate)
                || clipboardManager == null) return;
        ClipData clip;
        try {
            clip = clipboardManager.getPrimaryClip();
        } catch (SecurityException ignored) {
            return;
        } catch (RuntimeException ignored) {
            return;
        }
        if (clip == null) return;
        ClipDescription description = clip.getDescription();
        if (isSensitiveClip(description)) return;
        if (!hasTextMime(description)) return;
        boolean changed = false;
        for (int i = clip.getItemCount() - 1; i >= 0; i--) {
            CharSequence value;
            try {
                value = clip.getItemAt(i).coerceToText(this);
            } catch (RuntimeException ignored) {
                continue;
            }
            String text = normalizeClipboardText(value == null ? "" : value.toString());
            if (text == null) continue;
            changed |= mergeClipboardItem(clipboardHistory, text);
        }
        if (changed) {
            saveClipboardHistory();
            updateClipboardView();
        }
    }

    static boolean shouldBlockClipboardCapture(boolean forcedPrivate, boolean manualPrivate) {
        return PrivacyPolicy.shouldBlockCapture(forcedPrivate, manualPrivate);
    }

    static boolean isSensitiveClip(ClipDescription description) {
        // EXTRA_IS_SENSITIVE exists on API 33+. The constant is inlined at compile
        // time, so this string lookup is safe on older releases where the extra is
        // simply never present.
        return PrivacyPolicy.isSensitiveClip(description);
    }

    static boolean hasTextMime(ClipDescription description) {
        return PrivacyPolicy.hasTextMime(description);
    }

    /** Returns null for empty/whitespace-only clips, otherwise the original text. */
    static String normalizeClipboardText(String text) {
        return ClipboardLogic.normalizeText(text);
    }

    /**
     * Prepends {@code text} with dedup. Returns true when the history changed.
     * Index 0 already holding the text means no change.
     */
    static boolean mergeClipboardItem(List<String> history, String text) {
        return ClipboardLogic.mergeItem(history, text);
    }

    private void loadClipboardHistory() {
        SharedPreferences preferences = getSharedPreferences(PrefsKeys.PREFERENCES, MODE_PRIVATE);
        ClipboardRepository.loadInto(clipboardHistory, preferences);
    }

    private void saveClipboardHistory() {
        SharedPreferences preferences = getSharedPreferences(PrefsKeys.PREFERENCES, MODE_PRIVATE);
        ClipboardRepository.save(clipboardHistory, preferences);
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
        SharedPreferences preferences = context.getSharedPreferences(PrefsKeys.PREFERENCES, MODE_PRIVATE);
        KeyboardPrefsRepository.applyTo(preferences, keyboard);
    }

    private static boolean isSensitive(EditorInfo info) {
        return PrivacyPolicy.isSensitive(info);
    }
}
