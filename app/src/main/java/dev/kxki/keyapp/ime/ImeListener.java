package dev.kxki.keyapp.ime;

import dev.kxki.keyapp.keyboard.DeleteRange;

/** Edit operations the controller projects from Mozc output. Runs on the main thread. */
public interface ImeListener {
    void onComposing(long token, String text, int cursor);
    void onCommit(long token, String text);
    void onDeleteBefore(long token, int length);
    void onDeleteRange(long token, DeleteRange range);
    void onFinishComposing(long token);
    void onEditorAction(long token);
    void onEditorCursorMove(long token, int horizontal, int vertical);
    void onState(long token, ImeState state);
}
