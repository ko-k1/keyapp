package dev.kxki.keyapp.keyboard;

/**
 * Immutable keyboard mode value object.
 *
 * <p>Replaces the five interacting booleans ({@code japanese}, {@code symbols},
 * {@code numberMode}, {@code clipboard}, {@code shifted}/{@code shiftLocked}) that were
 * previously scattered across {@code KeyboardView} and {@code KeyLayout.Input}.
 * A single value makes mode transitions explicit, unit-testable, and extensible:
 * adding a new language or panel means adding a mode, not another boolean flag.
 */
public final class KeyboardMode {
    public final boolean japanese;
    public final boolean symbols;
    public final boolean numberMode;
    public final boolean clipboard;
    public final boolean shifted;
    public final boolean shiftLocked;

    public KeyboardMode(boolean japanese, boolean symbols, boolean numberMode,
            boolean clipboard, boolean shifted, boolean shiftLocked) {
        this.japanese = japanese;
        this.symbols = symbols;
        this.numberMode = numberMode;
        this.clipboard = clipboard;
        this.shifted = shifted;
        this.shiftLocked = shiftLocked;
    }

    public static KeyboardMode initial() {
        return new KeyboardMode(false, false, false, false, false, false);
    }

    /** 12-key flick entry is active (Japanese kana panel, not symbols/numbers). */
    public boolean isFlickEntry() {
        return japanese && !symbols && !numberMode && !clipboard;
    }

    /** Backspace flick ranges only apply on the kana panel. */
    public boolean isBackspaceFlickEnabled() {
        return isFlickEntry();
    }

    /** Space triggers conversion only on the kana panel. */
    public boolean isConversionSpace() {
        return isFlickEntry();
    }

    public KeyboardMode withJapanese(boolean enabled) {
        return new KeyboardMode(enabled, symbols, numberMode, clipboard, shifted, shiftLocked);
    }

    public KeyboardMode withSymbols(boolean enabled) {
        return new KeyboardMode(japanese, enabled, numberMode, clipboard, shifted, shiftLocked);
    }

    public KeyboardMode withNumberMode(boolean enabled) {
        return new KeyboardMode(japanese, symbols, enabled, clipboard, shifted, shiftLocked);
    }

    public KeyboardMode withClipboard(boolean enabled) {
        return new KeyboardMode(japanese, symbols, numberMode, enabled, shifted, shiftLocked);
    }

    public KeyboardMode withShift(boolean shifted, boolean locked) {
        return new KeyboardMode(japanese, symbols, numberMode, clipboard, shifted, locked);
    }

    public KeyboardMode toggledShift() {
        if (shiftLocked) return withShift(false, false);
        return withShift(!shifted, false);
    }

    public KeyboardMode resetForInput() {
        return initial();
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof KeyboardMode)) return false;
        KeyboardMode that = (KeyboardMode) other;
        return japanese == that.japanese && symbols == that.symbols
                && numberMode == that.numberMode && clipboard == that.clipboard
                && shifted == that.shifted && shiftLocked == that.shiftLocked;
    }

    @Override public int hashCode() {
        int result = japanese ? 1 : 0;
        result = 31 * result + (symbols ? 1 : 0);
        result = 31 * result + (numberMode ? 1 : 0);
        result = 31 * result + (clipboard ? 1 : 0);
        result = 31 * result + (shifted ? 1 : 0);
        result = 31 * result + (shiftLocked ? 1 : 0);
        return result;
    }

    @Override public String toString() {
        return "KeyboardMode{japanese=" + japanese + ", symbols=" + symbols
                + ", numberMode=" + numberMode + ", clipboard=" + clipboard
                + ", shifted=" + shifted + ", shiftLocked=" + shiftLocked + '}';
    }
}
