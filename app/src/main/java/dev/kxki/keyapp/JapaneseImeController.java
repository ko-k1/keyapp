package dev.kxki.keyapp;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import dev.kxki.keyapp.ime.CandidateReader;
import dev.kxki.keyapp.ime.ImeCandidate;
import dev.kxki.keyapp.ime.ImeListener;
import dev.kxki.keyapp.ime.ImeState;
import dev.kxki.keyapp.ime.ImeTransport;
import dev.kxki.keyapp.ime.SessionManager;
import dev.kxki.keyapp.ime.TextEngine;
import dev.kxki.keyapp.kana.KanaTable;
import dev.kxki.keyapp.keyboard.DeleteRange;

/** Serializes Mozc and projects its protocol output into Android edit operations. */
public final class JapaneseImeController implements TextEngine {

    private final SessionManager sessions;
    private final ImeListener listener;
    private final Executor serial;
    private final Executor callbacks;
    private final ExecutorService ownedExecutor;
    private final AtomicLong currentToken = new AtomicLong();

    // Accessed only by serial.
    private boolean japanese;
    private boolean privateMode;
    private String rawKana = "";
    private int preeditLength;
    private int preeditCursor;
    private List<ImeCandidate> candidates = Collections.emptyList();

    public JapaneseImeController(Context context, ImeListener listener) {
        this.sessions = new SessionManager(new MozcEngine(context));
        this.listener = listener;
        this.ownedExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "KeyApp-Mozc");
            thread.setDaemon(true);
            return thread;
        });
        this.serial = ownedExecutor;
        Handler main = new Handler(Looper.getMainLooper());
        this.callbacks = runnable -> main.post(runnable);
    }

    public JapaneseImeController(ImeTransport transport, ImeListener listener, Executor serial, Executor callbacks) {
        this.sessions = new SessionManager(transport);
        this.listener = listener;
        this.serial = serial;
        this.callbacks = callbacks;
        this.ownedExecutor = null;
    }

    @Override public long begin(boolean isPrivate) {
        long token = currentToken.incrementAndGet();
        serial.execute(() -> {
            if (!isCurrent(token)) return;
            sessions.deleteSession();
            japanese = false;
            privateMode = isPrivate;
            rawKana = "";
            preeditLength = 0;
            preeditCursor = 0;
            candidates = Collections.emptyList();
            sessions.open();
            emitState(token);
        });
        return token;
    }

    @Override public void setJapanese(boolean enabled) {
        long token = currentToken.get();
        serial.execute(() -> {
            if (!isCurrent(token) || japanese == enabled) return;
            if (!enabled) finishCurrent(token, true);
            japanese = enabled;
            if (enabled) ensureSession(token);
            emitState(token);
        });
    }

    @Override public void setPrivateMode(boolean enabled) {
        long token = currentToken.get();
        serial.execute(() -> {
            if (!isCurrent(token)) return;
            privateMode = enabled;
            if (sessions.hasSession() && sessions.isAvailable()) {
                sessions.updateRequest(token, privateMode);
            }
            emitState(token);
        });
    }

    @Override public void kana(String kana) {
        long token = currentToken.get();
        serial.execute(() -> {
            if (!isCurrent(token) || !japanese || kana.isEmpty()) return;
            rawKana = KanaTable.insertCodePoints(rawKana, preeditCursor, kana);
            preeditCursor += KanaTable.codePointLength(kana);
            preeditLength += KanaTable.codePointLength(kana);
            if (!ensureSession(token)) {
                emitComposing(token, rawKana, preeditCursor);
                emitState(token);
                return;
            }
            ProtoCommands.KeyEvent key = ProtoCommands.KeyEvent.newBuilder()
                    .setKeyString(kana)
                    .setInputStyle(ProtoCommands.KeyEvent.InputStyle.AS_IS)
                    .setMode(ProtoCommands.CompositionMode.HIRAGANA)
                    .setActivated(true)
                    .build();
            project(token, sessions.sendKey(key, token, privateMode));
        });
    }

    @Override public void modifyKana() {
        long token = currentToken.get();
        serial.execute(() -> {
            if (!isCurrent(token) || !japanese || rawKana.isEmpty() || preeditCursor == 0) return;
            String changed = KanaTable.transformBeforeCursor(rawKana, preeditCursor);
            if (changed.equals(rawKana)) return;
            rawKana = changed;
            if (!ensureSession(token)) {
                emitComposing(token, rawKana, preeditCursor);
                emitState(token);
                return;
            }
            int cursor = preeditCursor;
            ProtoCommands.SessionCommand.CompositionEvent event =
                    ProtoCommands.SessionCommand.CompositionEvent.newBuilder()
                            .setCompositionString(rawKana)
                            .setProbability(1)
                            .build();
            project(token, sessions.sendCommand(ProtoCommands.SessionCommand.newBuilder()
                    .setType(ProtoCommands.SessionCommand.CommandType.UPDATE_COMPOSITION)
                    .addCompositionEvents(event)
                    .build(), token, privateMode));
            if (sessions.hasSession() && preeditLength > 0 && preeditCursor != cursor) {
                project(token, sessions.moveMozcCursor(
                        Math.min(cursor, preeditLength), token, privateMode));
            }
        });
    }

    @Override public void backspace(boolean inJapaneseMode) {
        long token = currentToken.get();
        serial.execute(() -> {
            if (!isCurrent(token)) return;
            if (!inJapaneseMode || !japanese) {
                finishCurrent(token, true);
                emitDelete(token, 1);
                return;
            }
            if (rawKana.isEmpty() && candidates.isEmpty()) {
                emitDelete(token, 1);
                return;
            }
            if (!ensureSession(token)) {
                if (preeditCursor == 0) return;
                rawKana = KanaTable.removeCodePointBefore(rawKana, preeditCursor);
                preeditCursor--;
                preeditLength--;
                if (rawKana.isEmpty()) emitFinish(token);
                else emitComposing(token, rawKana, preeditCursor);
                emitState(token);
                return;
            }
            ProtoCommands.KeyEvent key = ProtoCommands.KeyEvent.newBuilder()
                    .setSpecialKey(ProtoCommands.KeyEvent.SpecialKey.BACKSPACE)
                    .build();
            project(token, sessions.sendKey(key, token, privateMode));
        });
    }

    @Override public void moveCursor(int horizontal, int vertical) {
        long token = currentToken.get();
        serial.execute(() -> {
            if (!isCurrent(token) || (horizontal == 0 && vertical == 0)) return;
            if (preeditLength == 0) {
                emitEditorCursorMove(token, horizontal, vertical);
                return;
            }
            int target = KanaTable.clamp(preeditCursor + horizontal, 0, preeditLength);
            if (target == preeditCursor) return;
            if (sessions.hasSession() && sessions.isAvailable()) {
                project(token, sessions.moveMozcCursor(target, token, privateMode));
            } else {
                preeditCursor = target;
                emitComposing(token, rawKana, preeditCursor);
            }
        });
    }

    @Override public void deleteRange(DeleteRange range) {
        long token = currentToken.get();
        serial.execute(() -> {
            if (!isCurrent(token)) return;
            finishCurrent(token, true);
            emitFinish(token);
            emitDeleteRange(token, range);
        });
    }

    @Override public void space(boolean inJapaneseMode) {
        long token = currentToken.get();
        serial.execute(() -> {
            if (!isCurrent(token)) return;
            if (!inJapaneseMode || !japanese || (rawKana.isEmpty() && candidates.isEmpty())) {
                finishCurrent(token, true);
                emitCommit(token, " ");
                return;
            }
            if (!ensureSession(token)) {
                finishCurrent(token, true);
                emitCommit(token, " ");
                return;
            }
            ProtoCommands.KeyEvent key = ProtoCommands.KeyEvent.newBuilder()
                    .setSpecialKey(ProtoCommands.KeyEvent.SpecialKey.SPACE)
                    .build();
            project(token, sessions.sendKey(key, token, privateMode));
        });
    }

    @Override public void selectCandidate(int candidateId) {
        long token = currentToken.get();
        serial.execute(() -> {
            if (!isCurrent(token) || !sessions.hasSession() || !sessions.isAvailable()) return;
            project(token, sessions.sendCommand(ProtoCommands.SessionCommand.newBuilder()
                    .setType(ProtoCommands.SessionCommand.CommandType.SUBMIT_CANDIDATE)
                    .setId(candidateId)
                    .build(), token, privateMode));
        });
    }

    @Override public void rawText(String text) {
        long token = currentToken.get();
        serial.execute(() -> {
            if (!isCurrent(token) || text.isEmpty()) return;
            finishCurrent(token, true);
            emitCommit(token, text);
        });
    }

    @Override public void enter() {
        long token = currentToken.get();
        serial.execute(() -> {
            if (!isCurrent(token)) return;
            if (!rawKana.isEmpty()) {
                finishCurrent(token, true);
                return;
            }
            emitEditorAction(token);
        });
    }

    @Override public void finish() {
        long token = currentToken.get();
        serial.execute(() -> {
            if (isCurrent(token)) finishCurrent(token, true);
        });
    }

    @Override public void abandon() {
        currentToken.incrementAndGet();
        serial.execute(() -> {
            sessions.deleteSession();
            japanese = false;
            rawKana = "";
            preeditLength = 0;
            preeditCursor = 0;
            candidates = Collections.emptyList();
        });
    }

    @Override public void close() {
        abandon();
        if (ownedExecutor != null) ownedExecutor.shutdown();
    }

    private boolean ensureSession(long token) {
        return sessions.ensureSession(token, privateMode);
    }

    private void project(long token, ProtoCommands.Command command) {
        if (command == null || !command.hasOutput()
                || command.getOutput().getErrorCode() != ProtoCommands.Output.ErrorCode.SESSION_SUCCESS) {
            sessions.invalidate();
            candidates = Collections.emptyList();
            emitComposing(token, rawKana, preeditCursor);
            emitState(token);
            return;
        }
        ProtoCommands.Output output = command.getOutput();
        if (output.hasDeletionRange() && output.getDeletionRange().getLength() > 0) {
            emitFinish(token);
            emitDelete(token, output.getDeletionRange().getLength());
        }
        if (output.hasResult() && !output.getResult().getValue().isEmpty()) {
            emitCommit(token, output.getResult().getValue());
            rawKana = "";
            preeditLength = 0;
            preeditCursor = 0;
        }

        if (output.hasPreedit()) {
            StringBuilder visible = new StringBuilder();
            StringBuilder reading = new StringBuilder();
            for (ProtoCommands.Preedit.Segment segment : output.getPreedit().getSegmentList()) {
                visible.append(segment.getValue());
                reading.append(segment.hasKey() ? segment.getKey() : segment.getValue());
            }
            rawKana = reading.toString();
            preeditLength = KanaTable.codePointLength(visible.toString());
            preeditCursor = KanaTable.clamp(output.getPreedit().getCursor(), 0, preeditLength);
            emitComposing(token, visible.toString(), preeditCursor);
        } else {
            rawKana = "";
            preeditLength = 0;
            preeditCursor = 0;
            emitFinish(token);
        }
        if (CandidateReader.hasCandidateList(output, privateMode)) {
            candidates = CandidateReader.read(output, privateMode);
        } else if (!output.hasCandidateWindow()) {
            candidates = Collections.emptyList();
        }
        if (output.hasCandidateWindow()) {
            candidates = CandidateReader.updateFocus(candidates, output.getCandidateWindow());
        }
        emitState(token);
    }

    private void finishCurrent(long token, boolean submit) {
        if (rawKana.isEmpty() && candidates.isEmpty()) return;
        String fallback = rawKana;
        if (sessions.hasSession() && sessions.isAvailable()) {
            ProtoCommands.Command result = sessions.sendCommand(ProtoCommands.SessionCommand.newBuilder()
                    .setType(submit ? ProtoCommands.SessionCommand.CommandType.SUBMIT
                            : ProtoCommands.SessionCommand.CommandType.REVERT)
                    .build(), token, privateMode);
            if (result != null) {
                project(token, result);
                sessions.sendCommand(ProtoCommands.SessionCommand.newBuilder()
                        .setType(ProtoCommands.SessionCommand.CommandType.RESET_CONTEXT)
                        .build(), token, privateMode);
                rawKana = "";
                preeditLength = 0;
                preeditCursor = 0;
                candidates = Collections.emptyList();
                emitState(token);
                return;
            }
        }
        if (submit && !fallback.isEmpty()) emitCommit(token, fallback); else emitFinish(token);
        rawKana = "";
        preeditLength = 0;
        preeditCursor = 0;
        candidates = Collections.emptyList();
        emitState(token);
    }

    private boolean isCurrent(long token) { return currentToken.get() == token; }

    private void emitState(long token) {
        ImeState state = new ImeState(sessions.isAvailable(), privateMode, candidates, sessions.error());
        post(token, () -> listener.onState(token, state));
    }

    private void emitComposing(long token, String text, int cursor) {
        post(token, () -> listener.onComposing(token, text, cursor));
    }
    private void emitCommit(long token, String text) { post(token, () -> listener.onCommit(token, text)); }
    private void emitDelete(long token, int length) { post(token, () -> listener.onDeleteBefore(token, length)); }
    private void emitDeleteRange(long token, DeleteRange range) {
        post(token, () -> listener.onDeleteRange(token, range));
    }
    private void emitFinish(long token) {
        post(token, () -> {
            listener.onComposing(token, "", 0);
            listener.onFinishComposing(token);
        });
    }
    private void emitEditorAction(long token) { post(token, () -> listener.onEditorAction(token)); }
    private void emitEditorCursorMove(long token, int horizontal, int vertical) {
        post(token, () -> listener.onEditorCursorMove(token, horizontal, vertical));
    }

    private void post(long token, Runnable callback) {
        callbacks.execute(() -> {
            if (isCurrent(token)) callback.run();
        });
    }
}
