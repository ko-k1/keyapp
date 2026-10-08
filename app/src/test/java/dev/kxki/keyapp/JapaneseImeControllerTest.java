package dev.kxki.keyapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCandidateWindow;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands;

import dev.kxki.keyapp.ime.ImeListener;
import dev.kxki.keyapp.ime.ImeState;
import dev.kxki.keyapp.ime.ImeTransport;
import dev.kxki.keyapp.kana.KanaTable;
import dev.kxki.keyapp.keyboard.DeleteRange;

import java.util.ArrayList;
import java.util.List;

public final class JapaneseImeControllerTest {
    @Test public void kanaCandidateSelectionAndPrivacyUseMozcIds() {
        FakeTransport transport = new FakeTransport();
        RecordingListener listener = new RecordingListener();
        JapaneseImeController controller =
                new JapaneseImeController(transport, listener, Runnable::run, Runnable::run);

        controller.begin(true);
        controller.setJapanese(true);
        controller.kana("か");

        ProtoCommands.Input keyInput = transport.last(ProtoCommands.Input.CommandType.SEND_KEY);
        assertEquals("か", keyInput.getKey().getKeyString());
        assertEquals(ProtoCommands.KeyEvent.InputStyle.AS_IS, keyInput.getKey().getInputStyle());
        assertTrue(keyInput.getRequest().getIsIncognitoMode());
        assertEquals("か", listener.composing);
        assertEquals(42, listener.state.candidates.get(0).id);
        assertTrue(listener.state.candidates.get(0).focused);

        controller.space(true);
        assertEquals(2, listener.state.candidates.size());
        assertTrue(listener.state.candidates.get(1).focused);
        assertEquals("", listener.committed);

        controller.selectCandidate(42);
        assertEquals(42, transport.selectedCandidateId);
        assertEquals("火", listener.committed);
    }

    @Test public void unavailableEngineKeepsSafeRawKana() {
        FakeTransport transport = new FakeTransport();
        transport.available = false;
        RecordingListener listener = new RecordingListener();
        JapaneseImeController controller =
                new JapaneseImeController(transport, listener, Runnable::run, Runnable::run);

        controller.begin(false);
        controller.setJapanese(true);
        controller.kana("は");
        controller.modifyKana();
        assertEquals("ば", listener.composing);
        controller.enter();
        assertEquals("ば", listener.committed);
        assertTrue(!listener.editorAction);
        controller.enter();
        assertTrue(listener.editorAction);
    }

    @Test public void deletionRangeAndKanaTransformsAreProjected() {
        FakeTransport transport = new FakeTransport();
        RecordingListener listener = new RecordingListener();
        JapaneseImeController controller =
                new JapaneseImeController(transport, listener, Runnable::run, Runnable::run);

        controller.begin(false);
        controller.setJapanese(true);
        controller.kana("か");
        transport.deleteOnBackspace = true;
        controller.backspace(true);
        assertEquals(1, listener.deleted);
        assertEquals("ば", KanaTable.transformLast("は"));
        assertEquals("ぱ", KanaTable.transformLast("ば"));
        assertEquals("は", KanaTable.transformLast("ぱ"));
        assertEquals("っ", KanaTable.transformLast("つ"));
        assertEquals("づ", KanaTable.transformLast("っ"));
        assertEquals("ゃ", KanaTable.transformLast("や"));
        assertEquals("や", KanaTable.transformLast("ゃ"));
        assertEquals("ん", KanaTable.transformLast("ん"));
    }

    @Test public void backspaceClearsTheFinalKana() {
        FakeTransport transport = new FakeTransport();
        RecordingListener listener = new RecordingListener();
        JapaneseImeController controller =
                new JapaneseImeController(transport, listener, Runnable::run, Runnable::run);

        controller.begin(false);
        controller.setJapanese(true);
        controller.kana("か");
        controller.backspace(true);

        assertEquals("", listener.composing);
        assertTrue(listener.finished);
    }

    @Test public void cursorMovementEditsMozcPreeditAtTheNewPosition() {
        FakeTransport transport = new FakeTransport();
        transport.cursorEditing = true;
        RecordingListener listener = new RecordingListener();
        JapaneseImeController controller =
                new JapaneseImeController(transport, listener, Runnable::run, Runnable::run);

        controller.begin(false);
        controller.setJapanese(true);
        controller.kana("かな");
        controller.moveCursor(-1, 0);
        assertEquals(1, transport.lastMoveCursor());
        assertEquals(1, listener.composingCursor);

        controller.kana("さ");
        assertEquals("かさな", listener.composing);
        assertEquals(2, listener.composingCursor);
        controller.backspace(true);
        assertEquals("かな", listener.composing);
        assertEquals(1, listener.composingCursor);
        controller.modifyKana();
        assertEquals("ゕな", listener.composing);
        assertEquals(1, listener.composingCursor);
    }

    @Test public void compositionLocksHorizontalMovementAndIgnoresVerticalMovement() {
        FakeTransport transport = new FakeTransport();
        transport.cursorEditing = true;
        RecordingListener listener = new RecordingListener();
        JapaneseImeController controller =
                new JapaneseImeController(transport, listener, Runnable::run, Runnable::run);

        controller.begin(false);
        controller.setJapanese(true);
        controller.kana("かな");
        controller.moveCursor(-5, -1);
        assertEquals(0, transport.lastMoveCursor());
        assertEquals("", listener.committed);
        assertEquals(0, listener.editorHorizontal);
        assertEquals(0, listener.editorVertical);

        controller.moveCursor(0, -1);
        controller.kana("さ");
        assertEquals("さかな", listener.composing);
        assertEquals(1, listener.composingCursor);
    }

    @Test public void unavailableEngineEditsFallbackKanaAtItsCursor() {
        FakeTransport transport = new FakeTransport();
        transport.available = false;
        RecordingListener listener = new RecordingListener();
        JapaneseImeController controller =
                new JapaneseImeController(transport, listener, Runnable::run, Runnable::run);

        controller.begin(false);
        controller.setJapanese(true);
        controller.kana("かな");
        controller.moveCursor(-1, 0);
        controller.kana("さ");
        controller.modifyKana();
        assertEquals("かざな", listener.composing);
        assertEquals(2, listener.composingCursor);
        controller.backspace(true);
        assertEquals("かな", listener.composing);
        assertEquals(1, listener.composingCursor);
        controller.moveCursor(-5, 1);
        controller.kana("さ");
        assertEquals("さかな", listener.composing);
        assertEquals(1, listener.composingCursor);
    }

    private static final class FakeTransport implements ImeTransport {
        final List<ProtoCommands.Input> inputs = new ArrayList<>();
        boolean available = true;
        boolean deleteOnBackspace;
        boolean cursorEditing;
        String preedit = "";
        int preeditCursor;
        int selectedCandidateId = -1;

        @Override public boolean open() { return available; }
        @Override public boolean isAvailable() { return available; }
        @Override public boolean clearLearning() { return true; }
        @Override public String error() { return available ? "" : "unavailable"; }

        @Override public ProtoCommands.Command evaluate(ProtoCommands.Command command) {
            ProtoCommands.Input input = command.getInput();
            inputs.add(input);
            ProtoCommands.Output.Builder output = ProtoCommands.Output.newBuilder();
            if (input.getType() == ProtoCommands.Input.CommandType.CREATE_SESSION) {
                output.setId(7);
            } else if (cursorEditing && input.getType() == ProtoCommands.Input.CommandType.SEND_COMMAND
                    && input.getCommand().getType()
                    == ProtoCommands.SessionCommand.CommandType.MOVE_CURSOR) {
                preeditCursor = input.getCommand().getCursorPosition();
                setPreedit(output);
            } else if (cursorEditing && input.getType() == ProtoCommands.Input.CommandType.SEND_COMMAND
                    && input.getCommand().getType()
                    == ProtoCommands.SessionCommand.CommandType.UPDATE_COMPOSITION) {
                preedit = input.getCommand().getCompositionEvents(0).getCompositionString();
                preeditCursor = preedit.codePointCount(0, preedit.length());
                setPreedit(output);
            } else if (cursorEditing && input.getType() == ProtoCommands.Input.CommandType.SEND_COMMAND
                    && input.getCommand().getType()
                    == ProtoCommands.SessionCommand.CommandType.SUBMIT) {
                output.setResult(ProtoCommands.Result.newBuilder()
                        .setType(ProtoCommands.Result.ResultType.STRING).setValue(preedit));
                preedit = "";
                preeditCursor = 0;
            } else if (cursorEditing && input.getType() == ProtoCommands.Input.CommandType.SEND_KEY
                    && !input.getKey().hasSpecialKey()) {
                preedit = insert(preedit, preeditCursor, input.getKey().getKeyString());
                preeditCursor += input.getKey().getKeyString().codePointCount(
                        0, input.getKey().getKeyString().length());
                setPreedit(output);
            } else if (cursorEditing && input.getType() == ProtoCommands.Input.CommandType.SEND_KEY
                    && input.getKey().getSpecialKey() == ProtoCommands.KeyEvent.SpecialKey.BACKSPACE) {
                if (preeditCursor > 0) {
                    int end = preedit.offsetByCodePoints(0, preeditCursor);
                    int start = preedit.offsetByCodePoints(end, -1);
                    preedit = preedit.substring(0, start) + preedit.substring(end);
                    preeditCursor--;
                }
                setPreedit(output);
            } else if (input.getType() == ProtoCommands.Input.CommandType.SEND_KEY
                    && input.getKey().getSpecialKey() == ProtoCommands.KeyEvent.SpecialKey.BACKSPACE
                    && deleteOnBackspace) {
                output.setDeletionRange(ProtoCommands.DeletionRange.newBuilder()
                        .setOffset(-1).setLength(1));
            } else if (input.getType() == ProtoCommands.Input.CommandType.SEND_KEY
                    && !input.getKey().hasSpecialKey()) {
                ProtoCommands.Preedit.Segment segment = ProtoCommands.Preedit.Segment.newBuilder()
                        .setAnnotation(ProtoCommands.Preedit.Segment.Annotation.UNDERLINE)
                        .setValue("か").setValueLength(1).setKey("か").build();
                output.setPreedit(ProtoCommands.Preedit.newBuilder().setCursor(1).addSegment(segment));
                ProtoCandidateWindow.CandidateWord word =
                        ProtoCandidateWindow.CandidateWord.newBuilder()
                                .setId(42).setIndex(0).setKey("か").setValue("火").build();
                ProtoCandidateWindow.CandidateWord second =
                        ProtoCandidateWindow.CandidateWord.newBuilder()
                                .setId(43).setIndex(1).setKey("か").setValue("蚊").build();
                output.setIncognitoCandidateWords(ProtoCandidateWindow.CandidateList.newBuilder()
                        .setFocusedIndex(0).addCandidates(word).addCandidates(second));
                output.setAllCandidateWords(ProtoCandidateWindow.CandidateList.newBuilder()
                        .setFocusedIndex(0).addCandidates(word).addCandidates(second));
            } else if (input.getType() == ProtoCommands.Input.CommandType.SEND_KEY
                    && input.getKey().getSpecialKey() == ProtoCommands.KeyEvent.SpecialKey.SPACE) {
                ProtoCommands.Preedit.Segment segment = ProtoCommands.Preedit.Segment.newBuilder()
                        .setAnnotation(ProtoCommands.Preedit.Segment.Annotation.HIGHLIGHT)
                        .setValue("蚊").setValueLength(1).setKey("か").build();
                output.setPreedit(ProtoCommands.Preedit.newBuilder().setCursor(1).addSegment(segment));
                output.setCandidateWindow(ProtoCandidateWindow.CandidateWindow.newBuilder()
                        .setSize(2).setPosition(0).setFocusedIndex(1)
                        .addCandidate(ProtoCandidateWindow.CandidateWindow.Candidate.newBuilder()
                                .setIndex(0).setId(42).setValue("火"))
                        .addCandidate(ProtoCandidateWindow.CandidateWindow.Candidate.newBuilder()
                                .setIndex(1).setId(43).setValue("蚊")));
            } else if (input.getType() == ProtoCommands.Input.CommandType.SEND_COMMAND
                    && input.getCommand().getType()
                    == ProtoCommands.SessionCommand.CommandType.SUBMIT_CANDIDATE) {
                selectedCandidateId = input.getCommand().getId();
                output.setResult(ProtoCommands.Result.newBuilder()
                        .setType(ProtoCommands.Result.ResultType.STRING).setValue("火"));
            }
            return ProtoCommands.Command.newBuilder().setInput(input).setOutput(output).build();
        }

        private void setPreedit(ProtoCommands.Output.Builder output) {
            if (preedit.isEmpty()) return;
            output.setPreedit(ProtoCommands.Preedit.newBuilder().setCursor(preeditCursor)
                    .addSegment(ProtoCommands.Preedit.Segment.newBuilder()
                            .setAnnotation(ProtoCommands.Preedit.Segment.Annotation.UNDERLINE)
                            .setValue(preedit).setValueLength(preedit.codePointCount(0, preedit.length()))
                            .setKey(preedit)));
        }

        private static String insert(String text, int cursor, String value) {
            int offset = text.offsetByCodePoints(0, cursor);
            return text.substring(0, offset) + value + text.substring(offset);
        }

        int lastMoveCursor() {
            for (int i = inputs.size() - 1; i >= 0; i--) {
                ProtoCommands.Input input = inputs.get(i);
                if (input.getType() == ProtoCommands.Input.CommandType.SEND_COMMAND
                        && input.getCommand().getType()
                        == ProtoCommands.SessionCommand.CommandType.MOVE_CURSOR) {
                    return input.getCommand().getCursorPosition();
                }
            }
            throw new AssertionError("Missing MOVE_CURSOR");
        }

        ProtoCommands.Input last(ProtoCommands.Input.CommandType type) {
            for (int i = inputs.size() - 1; i >= 0; i--) {
                if (inputs.get(i).getType() == type) return inputs.get(i);
            }
            throw new AssertionError("Missing command " + type);
        }
    }

    private static final class RecordingListener implements ImeListener {
        String composing = "";
        String committed = "";
        int deleted;
        int composingCursor;
        int editorHorizontal;
        int editorVertical;
        boolean editorAction;
        boolean finished;
        ImeState state;

        @Override public void onComposing(long token, String text, int cursor) {
            composing = text;
            composingCursor = cursor;
        }
        @Override public void onCommit(long token, String text) { committed += text; }
        @Override public void onDeleteBefore(long token, int length) { deleted += length; }
        @Override public void onDeleteRange(long token, DeleteRange range) {}
        @Override public void onFinishComposing(long token) { finished = true; }
        @Override public void onEditorAction(long token) { editorAction = true; }
        @Override public void onEditorCursorMove(long token, int horizontal, int vertical) {
            editorHorizontal = horizontal;
            editorVertical = vertical;
        }
        @Override public void onState(long token, ImeState next) { state = next; }
    }
}
