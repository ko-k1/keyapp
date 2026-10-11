package dev.kxki.keyapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCandidateWindow;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands;

import dev.kxki.keyapp.ime.ImeListener;
import dev.kxki.keyapp.ime.ImeState;
import dev.kxki.keyapp.ime.ImeTransport;
import dev.kxki.keyapp.keyboard.DeleteRange;
import dev.kxki.keyapp.userdict.UserDictionaryEntry;
import dev.kxki.keyapp.userdict.UserDictionaryProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class UserDictionaryControllerTest {
    @Test public void userWordLeadsMozcCandidates() {
        FakeTransport transport = new FakeTransport();
        RecordingListener listener = new RecordingListener();
        JapaneseImeController controller = new JapaneseImeController(transport, listener,
                Runnable::run, Runnable::run,
                provider(new UserDictionaryEntry("か", "カスタム")));

        controller.begin(false);
        controller.setJapanese(true);
        controller.kana("か");

        assertEquals("か", controller.composingReading());
        assertTrue(listener.state.candidates.size() >= 2);
        assertEquals("カスタム", listener.state.candidates.get(0).value);
        assertTrue(listener.state.candidates.get(0).id < 0);
        assertEquals("火", listener.state.candidates.get(1).value);
    }

    @Test public void prefixReadingSuggestsLongerEntry() {
        FakeTransport transport = new FakeTransport();
        RecordingListener listener = new RecordingListener();
        JapaneseImeController controller = new JapaneseImeController(transport, listener,
                Runnable::run, Runnable::run,
                provider(new UserDictionaryEntry("きょうと", "京都")));

        controller.begin(false);
        controller.setJapanese(true);
        controller.kana("きょ");

        assertEquals("京都", listener.state.candidates.get(0).value);
    }

    @Test public void privateModeSuppressesUserWords() {
        FakeTransport transport = new FakeTransport();
        RecordingListener listener = new RecordingListener();
        JapaneseImeController controller = new JapaneseImeController(transport, listener,
                Runnable::run, Runnable::run,
                provider(new UserDictionaryEntry("か", "カスタム")));

        controller.begin(true);
        controller.setJapanese(true);
        controller.kana("か");

        for (dev.kxki.keyapp.ime.ImeCandidate candidate : listener.state.candidates) {
            assertTrue(candidate.id >= 0);
        }
    }

    @Test public void selectingUserWordCommitsAndClears() {
        FakeTransport transport = new FakeTransport();
        RecordingListener listener = new RecordingListener();
        JapaneseImeController controller = new JapaneseImeController(transport, listener,
                Runnable::run, Runnable::run,
                provider(new UserDictionaryEntry("か", "カスタム")));

        controller.begin(false);
        controller.setJapanese(true);
        controller.kana("か");
        int userId = listener.state.candidates.get(0).id;
        controller.selectCandidate(userId);

        assertEquals("カスタム", listener.committed);
        assertEquals("", listener.composing);
        assertEquals("", controller.composingReading());
    }

    @Test public void unavailableEngineStillOffersUserWords() {
        FakeTransport transport = new FakeTransport();
        transport.available = false;
        RecordingListener listener = new RecordingListener();
        JapaneseImeController controller = new JapaneseImeController(transport, listener,
                Runnable::run, Runnable::run,
                provider(new UserDictionaryEntry("は", "葉")));

        controller.begin(false);
        controller.setJapanese(true);
        controller.kana("は");

        assertEquals("は", listener.composing);
        assertEquals("葉", listener.state.candidates.get(0).value);
    }

    private static UserDictionaryProvider provider(UserDictionaryEntry... entries) {
        List<UserDictionaryEntry> list = Arrays.asList(entries);
        return new UserDictionaryProvider() {
            @Override public List<UserDictionaryEntry> entries() {
                return new ArrayList<>(list);
            }
            @Override public boolean add(String reading, String word) { return false; }
        };
    }

    private static final class FakeTransport implements ImeTransport {
        boolean available = true;

        @Override public boolean open() { return available; }
        @Override public boolean isAvailable() { return available; }
        @Override public boolean clearLearning() { return true; }
        @Override public String error() { return available ? "" : "unavailable"; }

        @Override public ProtoCommands.Command evaluate(ProtoCommands.Command command) {
            ProtoCommands.Input input = command.getInput();
            ProtoCommands.Output.Builder output = ProtoCommands.Output.newBuilder();
            if (input.getType() == ProtoCommands.Input.CommandType.CREATE_SESSION) {
                output.setId(7);
            } else if (input.getType() == ProtoCommands.Input.CommandType.SEND_KEY
                    && !input.getKey().hasSpecialKey()) {
                String key = input.getKey().getKeyString();
                ProtoCommands.Preedit.Segment segment = ProtoCommands.Preedit.Segment.newBuilder()
                        .setAnnotation(ProtoCommands.Preedit.Segment.Annotation.UNDERLINE)
                        .setValue(key).setValueLength(1).setKey(key).build();
                output.setPreedit(ProtoCommands.Preedit.newBuilder().setCursor(1).addSegment(segment));
                ProtoCandidateWindow.CandidateWord word =
                        ProtoCandidateWindow.CandidateWord.newBuilder()
                                .setId(42).setIndex(0).setKey(key).setValue("火").build();
                output.setAllCandidateWords(ProtoCandidateWindow.CandidateList.newBuilder()
                        .setFocusedIndex(0).addCandidates(word));
            }
            return ProtoCommands.Command.newBuilder().setInput(input).setOutput(output).build();
        }
    }

    private static final class RecordingListener implements ImeListener {
        String composing = "";
        String committed = "";
        ImeState state;

        @Override public void onComposing(long token, String text, int cursor) { composing = text; }
        @Override public void onCommit(long token, String text) { committed += text; }
        @Override public void onDeleteBefore(long token, int length) {}
        @Override public void onDeleteRange(long token, DeleteRange range) {}
        @Override public void onFinishComposing(long token) { composing = ""; }
        @Override public void onEditorAction(long token) {}
        @Override public void onEditorCursorMove(long token, int horizontal, int vertical) {}
        @Override public void onState(long token, ImeState next) { state = next; }
    }
}
