package dev.kxki.keyapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCandidateWindow;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands;

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
        assertEquals("ば", JapaneseImeController.transformLast("は"));
        assertEquals("ぱ", JapaneseImeController.transformLast("ば"));
        assertEquals("は", JapaneseImeController.transformLast("ぱ"));
        assertEquals("っ", JapaneseImeController.transformLast("つ"));
        assertEquals("づ", JapaneseImeController.transformLast("っ"));
        assertEquals("ゃ", JapaneseImeController.transformLast("や"));
        assertEquals("や", JapaneseImeController.transformLast("ゃ"));
        assertEquals("ん", JapaneseImeController.transformLast("ん"));
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

    @Test public void backspaceFlickMapsDeletionRanges() {
        assertEquals(KeyboardView.DeleteRange.TO_END,
                KeyboardView.backspaceFlick(0, -30, 20));
        assertEquals(KeyboardView.DeleteRange.TO_START,
                KeyboardView.backspaceFlick(0, 30, 20));
        assertEquals(KeyboardView.DeleteRange.ALL,
                KeyboardView.backspaceFlick(-30, 0, 20));
        assertEquals(null, KeyboardView.backspaceFlick(30, 0, 20));
        assertEquals(null, KeyboardView.backspaceFlick(0, 0, 20));
    }

    private static final class FakeTransport implements JapaneseImeController.Transport {
        final List<ProtoCommands.Input> inputs = new ArrayList<>();
        boolean available = true;
        boolean deleteOnBackspace;
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
                output.setIncognitoCandidateWords(ProtoCandidateWindow.CandidateList.newBuilder()
                        .setFocusedIndex(0).addCandidates(word));
                output.setAllCandidateWords(ProtoCandidateWindow.CandidateList.newBuilder()
                        .setFocusedIndex(0).addCandidates(word));
            } else if (input.getType() == ProtoCommands.Input.CommandType.SEND_COMMAND
                    && input.getCommand().getType()
                    == ProtoCommands.SessionCommand.CommandType.SELECT_CANDIDATE) {
                selectedCandidateId = input.getCommand().getId();
                output.setResult(ProtoCommands.Result.newBuilder()
                        .setType(ProtoCommands.Result.ResultType.STRING).setValue("火"));
            }
            return ProtoCommands.Command.newBuilder().setInput(input).setOutput(output).build();
        }

        ProtoCommands.Input last(ProtoCommands.Input.CommandType type) {
            for (int i = inputs.size() - 1; i >= 0; i--) {
                if (inputs.get(i).getType() == type) return inputs.get(i);
            }
            throw new AssertionError("Missing command " + type);
        }
    }

    private static final class RecordingListener implements JapaneseImeController.Listener {
        String composing = "";
        String committed = "";
        int deleted;
        boolean editorAction;
        boolean finished;
        JapaneseImeController.State state;

        @Override public void onComposing(long token, String text) { composing = text; }
        @Override public void onCommit(long token, String text) { committed += text; }
        @Override public void onDeleteBefore(long token, int length) { deleted += length; }
        @Override public void onDeleteRange(long token, KeyboardView.DeleteRange range) {}
        @Override public void onFinishComposing(long token) { finished = true; }
        @Override public void onEditorAction(long token) { editorAction = true; }
        @Override public void onState(long token, JapaneseImeController.State next) { state = next; }
    }
}
