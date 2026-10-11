package dev.kxki.keyapp.layout;

import dev.kxki.keyapp.keyboard.KeyLayout;

import java.util.List;

/** Raw US QWERTY panel. */
public final class QwertyLayout implements KeyboardLayoutProvider {
    @Override public KeyboardLayoutId id() { return KeyboardLayoutId.QWERTY; }

    @Override public void build(KeyLayout.Input in, List<KeyLayout.Key> out) {
        String first = in.shifted ? "QWERTYUIOP" : "qwertyuiop";
        String second = in.shifted ? "ASDFGHJKL" : "asdfghjkl";
        KeyLayout.addRow(out, in, KeyLayout.split(first), KeyLayout.split("qwertyuiop"));
        KeyLayout.addRow(out, in, 1, KeyLayout.split(second), KeyLayout.split("asdfghjkl"));
        KeyLayout.addRow(out, in, 2,
                new String[]{in.shifted ? "⇪" : "⇧", in.shifted ? "Z" : "z", in.shifted ? "X" : "x",
                        in.shifted ? "C" : "c", in.shifted ? "V" : "v", in.shifted ? "B" : "b",
                        in.shifted ? "N" : "n", in.shifted ? "M" : "m", "⌫"},
                new String[]{"#shift", "z", "x", "c", "v", "b", "n", "m", "#del"});
        KeyLayout.addSpaceRow(out, in, 3,
                new String[]{"123", "かな"}, new String[]{"#sym", "#jp"},
                "space", " ",
                new String[]{"clip", "↵"}, new String[]{"#clip", "#enter"});
    }
}
