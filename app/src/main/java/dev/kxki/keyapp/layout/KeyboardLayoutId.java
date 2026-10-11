package dev.kxki.keyapp.layout;

/**
 * Stable ids for every keyboard panel.
 *
 * <p>Adding a new language means adding an id plus a {@link KeyboardLayoutProvider},
 * then registering it in {@link LayoutRegistry} — no boolean-flag surgery in the
 * view or {@code KeyLayout}.
 */
public enum KeyboardLayoutId {
    QWERTY,
    JAPANESE_FLICK,
    JAPANESE_NUMBERS,
    SYMBOLS,
    SYMBOLS_SHIFTED,
    CLIPBOARD
}
