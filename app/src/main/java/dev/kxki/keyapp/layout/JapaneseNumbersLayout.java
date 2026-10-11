package dev.kxki.keyapp.layout;

import dev.kxki.keyapp.keyboard.KeyLayout;

import java.util.List;

/** Japanese numeric panel reachable from the flick panel. */
public final class JapaneseNumbersLayout implements KeyboardLayoutProvider {
    @Override public KeyboardLayoutId id() { return KeyboardLayoutId.JAPANESE_NUMBERS; }

    @Override public void build(KeyLayout.Input in, List<KeyLayout.Key> out) {
        KeyLayout.addRow(out, in,
                new String[]{"clip", "1", "2", "3", "⌫"},
                new String[]{"#clip", "1", "2", "3", "#del"});
        KeyLayout.addRow(out, in, 1,
                new String[]{"ABC", "4", "5", "6", "space"},
                new String[]{"#en", "4", "5", "6", " "});
        KeyLayout.addRow(out, in, 2,
                new String[]{"かな", "7", "8", "9", ","},
                new String[]{"#jp", "7", "8", "9", ","});
        KeyLayout.addRow(out, in, 3,
                new String[]{"-", ".", "0", "/", "return"},
                new String[]{"-", ".", "0", "/", "#enter"});
    }
}
