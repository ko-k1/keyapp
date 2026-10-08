package dev.kxki.keyapp.keyboard;

/** Editor-text deletion range requested by a backspace flick gesture. */
public enum DeleteRange {
    TO_END,
    TO_START,
    ALL;

    /**
     * Maps a backspace-key flick vector to a range, or null for a plain backspace.
     * Pure function of the drag vector; caller supplies the threshold in px.
     */
    public static DeleteRange fromBackspaceFlick(float dx, float dy, float threshold) {
        if (Math.abs(dx) + Math.abs(dy) <= threshold) return null;
        if (Math.abs(dx) > Math.abs(dy)) return dx < 0 ? ALL : null;
        return dy < 0 ? TO_END : TO_START;
    }
}
