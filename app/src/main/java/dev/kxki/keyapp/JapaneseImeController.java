package dev.kxki.keyapp;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.mozc.android.inputmethod.japanese.protobuf.ProtoCandidateWindow;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/** Serializes Mozc and projects its protocol output into Android edit operations. */
final class JapaneseImeController implements AutoCloseable {
    private static final String[] KANA_MODIFIER_CYCLES = {
            "あぁ", "いぃ", "うぅゔ", "えぇ", "おぉ",
            "かゕが", "きぎ", "くぐ", "けゖげ", "こご",
            "さざ", "しじ", "すず", "せぜ", "そぞ",
            "ただ", "ちぢ", "つっづ", "てで", "とど",
            "はばぱ", "ひびぴ", "ふぶぷ", "へべぺ", "ほぼぽ",
            "やゃ", "ゆゅ", "よょ", "わゎ"
    };

    interface Transport {
        boolean open();
        boolean isAvailable();
        ProtoCommands.Command evaluate(ProtoCommands.Command command);
        boolean clearLearning();
        String error();
    }

    interface Listener {
        void onComposing(long token, String text, int cursor);
        void onCommit(long token, String text);
        void onDeleteBefore(long token, int length);
        void onDeleteRange(long token, KeyboardView.DeleteRange range);
        void onFinishComposing(long token);
        void onEditorAction(long token);
        void onEditorCursorMove(long token, int horizontal, int vertical);
        void onState(long token, State state);
    }

    static final class Candidate {
        final int id;
        final String value;
        final String label;
        final boolean focused;

        Candidate(int id, String value, String label, boolean focused) {
            this.id = id;
            this.value = value;
            this.label = label;
            this.focused = focused;
        }
    }

    static final class State {
        final boolean available;
        final boolean privateMode;
        final List<Candidate> candidates;
        final String error;

        State(boolean available, boolean privateMode, List<Candidate> candidates, String error) {
            this.available = available;
            this.privateMode = privateMode;
            this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
            this.error = error;
        }
    }

    private final Transport transport;
    private final Listener listener;
    private final Executor serial;
    private final Executor callbacks;
    private final ExecutorService ownedExecutor;
    private final AtomicLong currentToken = new AtomicLong();

    // Accessed only by serial.
    private long sessionId;
    private boolean japanese;
    private boolean privateMode;
    private String rawKana = "";
    private int preeditLength;
    private int preeditCursor;
    private List<Candidate> candidates = Collections.emptyList();

    JapaneseImeController(Context context, Listener listener) {
        this.transport = new MozcEngine(context);
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

    JapaneseImeController(Transport transport, Listener listener, Executor serial, Executor callbacks) {
        this.transport = transport;
        this.listener = listener;
        this.serial = serial;
        this.callbacks = callbacks;
        this.ownedExecutor = null;
    }

    long begin(boolean isPrivate) {
        long token = currentToken.incrementAndGet();
        serial.execute(() -> {
            if (!isCurrent(token)) return;
            deleteSession();
            japanese = false;
            privateMode = isPrivate;
            rawKana = "";
            preeditLength = 0;
            preeditCursor = 0;
            candidates = Collections.emptyList();
            transport.open();
            emitState(token);
        });
        return token;
    }

    void setJapanese(boolean enabled) {
        long token = currentToken.get();
        serial.execute(() -> {
            if (!isCurrent(token) || japanese == enabled) return;
            if (!enabled) finishCurrent(token, true);
            japanese = enabled;
            if (enabled) ensureSession(token);
            emitState(token);
        });
    }

    void setPrivateMode(boolean enabled) {
        long token = currentToken.get();
        serial.execute(() -> {
            if (!isCurrent(token)) return;
            privateMode = enabled;
            if (sessionId != 0 && transport.isAvailable()) {
                evaluate(ProtoCommands.Input.newBuilder()
                        .setType(ProtoCommands.Input.CommandType.SET_REQUEST)
                        .setId(sessionId)
                        .setRequest(request())
                        .build());
            }
            emitState(token);
        });
    }

    void kana(String kana) {
        long token = currentToken.get();
        serial.execute(() -> {
            if (!isCurrent(token) || !japanese || kana.isEmpty()) return;
            rawKana = insertCodePoints(rawKana, preeditCursor, kana);
            preeditCursor += codePointLength(kana);
            preeditLength += codePointLength(kana);
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
            project(token, evaluate(sessionInput(ProtoCommands.Input.CommandType.SEND_KEY).setKey(key).build()));
        });
    }

    void modifyKana() {
        long token = currentToken.get();
        serial.execute(() -> {
            if (!isCurrent(token) || !japanese || rawKana.isEmpty() || preeditCursor == 0) return;
            String changed = transformBeforeCursor(rawKana, preeditCursor);
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
            project(token, sendCommand(ProtoCommands.SessionCommand.newBuilder()
                    .setType(ProtoCommands.SessionCommand.CommandType.UPDATE_COMPOSITION)
                    .addCompositionEvents(event)
                    .build()));
            if (sessionId != 0 && preeditLength > 0 && preeditCursor != cursor) {
                moveMozcCursor(token, Math.min(cursor, preeditLength));
            }
        });
    }

    void backspace(boolean inJapaneseMode) {
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
                rawKana = removeCodePointBefore(rawKana, preeditCursor);
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
            project(token, evaluate(sessionInput(ProtoCommands.Input.CommandType.SEND_KEY).setKey(key).build()));
        });
    }

    void moveCursor(int horizontal, int vertical) {
        long token = currentToken.get();
        serial.execute(() -> {
            if (!isCurrent(token) || (horizontal == 0 && vertical == 0)) return;
            if (preeditLength == 0) {
                emitEditorCursorMove(token, horizontal, vertical);
                return;
            }
            int target = clamp(preeditCursor + horizontal, 0, preeditLength);
            if (target == preeditCursor) return;
            if (sessionId != 0 && transport.isAvailable()) moveMozcCursor(token, target);
            else {
                preeditCursor = target;
                emitComposing(token, rawKana, preeditCursor);
            }
        });
    }

    void deleteRange(KeyboardView.DeleteRange range) {
        long token = currentToken.get();
        serial.execute(() -> {
            if (!isCurrent(token)) return;
            finishCurrent(token, true);
            emitFinish(token);
            emitDeleteRange(token, range);
        });
    }

    void space(boolean inJapaneseMode) {
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
            project(token, evaluate(sessionInput(ProtoCommands.Input.CommandType.SEND_KEY).setKey(key).build()));
        });
    }

    void selectCandidate(int candidateId) {
        long token = currentToken.get();
        serial.execute(() -> {
            if (!isCurrent(token) || sessionId == 0 || !transport.isAvailable()) return;
            project(token, sendCommand(ProtoCommands.SessionCommand.newBuilder()
                    .setType(ProtoCommands.SessionCommand.CommandType.SUBMIT_CANDIDATE)
                    .setId(candidateId)
                    .build()));
        });
    }

    void rawText(String text) {
        long token = currentToken.get();
        serial.execute(() -> {
            if (!isCurrent(token) || text.isEmpty()) return;
            finishCurrent(token, true);
            emitCommit(token, text);
        });
    }

    void enter() {
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

    void finish() {
        long token = currentToken.get();
        serial.execute(() -> {
            if (isCurrent(token)) finishCurrent(token, true);
        });
    }

    void abandon() {
        currentToken.incrementAndGet();
        serial.execute(() -> {
            deleteSession();
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
        if (sessionId != 0 && transport.isAvailable()) return true;
        if (!transport.open()) return false;
        ProtoCommands.Input input = ProtoCommands.Input.newBuilder()
                .setType(ProtoCommands.Input.CommandType.CREATE_SESSION)
                .setRequest(request())
                .setCapability(capability())
                .setContext(context(token))
                .build();
        ProtoCommands.Command created = evaluate(input);
        if (created == null || !created.hasOutput()
                || created.getOutput().getErrorCode() != ProtoCommands.Output.ErrorCode.SESSION_SUCCESS
                || created.getOutput().getId() == 0) {
            sessionId = 0;
            return false;
        }
        sessionId = created.getOutput().getId();
        sendCommand(ProtoCommands.SessionCommand.newBuilder()
                .setType(ProtoCommands.SessionCommand.CommandType.TURN_ON_IME)
                .setCompositionMode(ProtoCommands.CompositionMode.HIRAGANA)
                .build());
        return transport.isAvailable();
    }

    private ProtoCommands.Input.Builder sessionInput(ProtoCommands.Input.CommandType type) {
        return ProtoCommands.Input.newBuilder()
                .setType(type)
                .setId(sessionId)
                .setRequest(request())
                .setContext(context(currentToken.get()))
                .setCapability(capability());
    }

    private ProtoCommands.Command sendCommand(ProtoCommands.SessionCommand command) {
        return evaluate(sessionInput(ProtoCommands.Input.CommandType.SEND_COMMAND)
                .setCommand(command)
                .build());
    }

    private void moveMozcCursor(long token, int position) {
        project(token, sendCommand(ProtoCommands.SessionCommand.newBuilder()
                .setType(ProtoCommands.SessionCommand.CommandType.MOVE_CURSOR)
                .setCursorPosition(position)
                .build()));
    }

    private ProtoCommands.Command evaluate(ProtoCommands.Input input) {
        return transport.evaluate(ProtoCommands.Command.newBuilder().setInput(input).build());
    }

    private ProtoCommands.Request request() {
        return ProtoCommands.Request.newBuilder()
                .setZeroQuerySuggestion(true)
                .setMixedConversion(true)
                .setKanaModifierInsensitiveConversion(true)
                .setCandidatePageSize(9)
                .setCandidatesSizeLimit(100)
                .setFillIncognitoCandidateWords(privateMode)
                .setIsIncognitoMode(privateMode)
                .build();
    }

    private ProtoCommands.Capability capability() {
        return ProtoCommands.Capability.newBuilder()
                .setTextDeletion(ProtoCommands.Capability.TextDeletionCapabilityType.DELETE_PRECEDING_TEXT)
                .build();
    }

    private ProtoCommands.Context context(long token) {
        return ProtoCommands.Context.newBuilder()
                .setInputFieldType(ProtoCommands.Context.InputFieldType.NORMAL)
                .setRevision((int) token)
                .build();
    }

    private void project(long token, ProtoCommands.Command command) {
        if (command == null || !command.hasOutput()
                || command.getOutput().getErrorCode() != ProtoCommands.Output.ErrorCode.SESSION_SUCCESS) {
            sessionId = 0;
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
            preeditLength = codePointLength(visible.toString());
            preeditCursor = clamp(output.getPreedit().getCursor(), 0, preeditLength);
            emitComposing(token, visible.toString(), preeditCursor);
        } else {
            rawKana = "";
            preeditLength = 0;
            preeditCursor = 0;
            emitFinish(token);
        }
        if (hasCandidateList(output)) {
            candidates = readCandidates(output);
        } else if (!output.hasCandidateWindow()) {
            candidates = Collections.emptyList();
        }
        if (output.hasCandidateWindow()) {
            candidates = updateCandidateFocus(candidates, output.getCandidateWindow());
        }
        emitState(token);
    }

    private boolean hasCandidateList(ProtoCommands.Output output) {
        return (privateMode && output.hasIncognitoCandidateWords()) || output.hasAllCandidateWords();
    }

    private List<Candidate> readCandidates(ProtoCommands.Output output) {
        ProtoCandidateWindow.CandidateList list;
        if (privateMode && output.hasIncognitoCandidateWords()) {
            list = output.getIncognitoCandidateWords();
        } else if (output.hasAllCandidateWords()) {
            list = output.getAllCandidateWords();
        } else {
            return Collections.emptyList();
        }
        List<Candidate> result = new ArrayList<>(list.getCandidatesCount());
        for (int i = 0; i < list.getCandidatesCount(); i++) {
            ProtoCandidateWindow.CandidateWord word = list.getCandidates(i);
            String label = word.hasAnnotation() ? word.getAnnotation().getDescription() : "";
            result.add(new Candidate(word.getId(), word.getValue(), label,
                    list.hasFocusedIndex() && list.getFocusedIndex() == i));
        }
        return result;
    }

    private List<Candidate> updateCandidateFocus(
            List<Candidate> source, ProtoCandidateWindow.CandidateWindow window) {
        Integer focusedId = null;
        if (window.hasFocusedIndex()) {
            for (ProtoCandidateWindow.CandidateWindow.Candidate candidate : window.getCandidateList()) {
                if (candidate.getIndex() == window.getFocusedIndex() && candidate.hasId()) {
                    focusedId = candidate.getId();
                    break;
                }
            }
            if (focusedId == null && window.getFocusedIndex() < window.getCandidateCount()) {
                ProtoCandidateWindow.CandidateWindow.Candidate focused =
                        window.getCandidate(window.getFocusedIndex());
                if (focused.hasId()) focusedId = focused.getId();
            }
        }
        List<Candidate> result = new ArrayList<>(source.size());
        for (Candidate candidate : source) {
            result.add(new Candidate(candidate.id, candidate.value, candidate.label,
                    focusedId != null && candidate.id == focusedId));
        }
        return result;
    }

    private void finishCurrent(long token, boolean submit) {
        if (rawKana.isEmpty() && candidates.isEmpty()) return;
        String fallback = rawKana;
        if (sessionId != 0 && transport.isAvailable()) {
            ProtoCommands.Command result = sendCommand(ProtoCommands.SessionCommand.newBuilder()
                    .setType(submit ? ProtoCommands.SessionCommand.CommandType.SUBMIT
                            : ProtoCommands.SessionCommand.CommandType.REVERT)
                    .build());
            if (result != null) {
                project(token, result);
                sendCommand(ProtoCommands.SessionCommand.newBuilder()
                        .setType(ProtoCommands.SessionCommand.CommandType.RESET_CONTEXT)
                        .build());
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

    private void deleteSession() {
        if (sessionId != 0 && transport.isAvailable()) {
            evaluate(ProtoCommands.Input.newBuilder()
                    .setType(ProtoCommands.Input.CommandType.DELETE_SESSION)
                    .setId(sessionId)
                    .build());
        }
        sessionId = 0;
    }

    private boolean isCurrent(long token) { return currentToken.get() == token; }

    private void emitState(long token) {
        State state = new State(transport.isAvailable(), privateMode, candidates, transport.error());
        post(token, () -> listener.onState(token, state));
    }

    private void emitComposing(long token, String text, int cursor) {
        post(token, () -> listener.onComposing(token, text, cursor));
    }
    private void emitCommit(long token, String text) { post(token, () -> listener.onCommit(token, text)); }
    private void emitDelete(long token, int length) { post(token, () -> listener.onDeleteBefore(token, length)); }
    private void emitDeleteRange(long token, KeyboardView.DeleteRange range) {
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

    static String transformLast(String text) {
        if (text.isEmpty()) return text;
        int start = text.offsetByCodePoints(text.length(), -1);
        String last = text.substring(start);
        for (String cycle : KANA_MODIFIER_CYCLES) {
            int index = cycle.indexOf(last);
            if (index >= 0) {
                return text.substring(0, start)
                        + cycle.charAt((index + 1) % cycle.length());
            }
        }
        return text;
    }

    private static String transformBeforeCursor(String text, int cursor) {
        int end = text.offsetByCodePoints(0, clamp(cursor, 0, codePointLength(text)));
        return transformLast(text.substring(0, end)) + text.substring(end);
    }

    private static String insertCodePoints(String text, int cursor, String inserted) {
        int offset = text.offsetByCodePoints(0, clamp(cursor, 0, codePointLength(text)));
        return text.substring(0, offset) + inserted + text.substring(offset);
    }

    private static String removeCodePointBefore(String text, int cursor) {
        int end = text.offsetByCodePoints(0, clamp(cursor, 0, codePointLength(text)));
        if (end == 0) return text;
        int start = text.offsetByCodePoints(end, -1);
        return text.substring(0, start) + text.substring(end);
    }

    private static int codePointLength(String text) { return text.codePointCount(0, text.length()); }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
