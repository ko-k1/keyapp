package dev.kxki.keyapp.ime;

import dev.kxki.keyapp.keyboard.DeleteRange;

/**
 * Engine behind the IME service.
 *
 * <p>{@code JapaneseImeController} is the shipped Mozc-backed implementation. New
 * languages or backends (direct-commit Latin, voice, predictions) implement this
 * interface and are constructed via {@link TextEngineFactory}, so the service never
 * hardcodes a single controller again.
 */
public interface TextEngine extends AutoCloseable {
    long begin(boolean isPrivate);

    void setJapanese(boolean enabled);

    void setPrivateMode(boolean enabled);

    void kana(String kana);

    void modifyKana();

    void backspace(boolean inJapaneseMode);

    void moveCursor(int horizontal, int vertical);

    void deleteRange(DeleteRange range);

    void space(boolean inJapaneseMode);

    void selectCandidate(int candidateId);

    void rawText(String text);

    void enter();

    void finish();

    void abandon();

    @Override void close();
}
