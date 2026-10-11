package dev.kxki.keyapp.layout;

import dev.kxki.keyapp.keyboard.KeyLayout;

import java.util.List;

/** Symbols panel (both shift layers). Shift state comes from {@link KeyLayout.Input}. */
public final class SymbolsLayout implements KeyboardLayoutProvider {
    private final boolean shifted;

    public SymbolsLayout(boolean shifted) {
        this.shifted = shifted;
    }

    @Override public KeyboardLayoutId id() {
        return shifted ? KeyboardLayoutId.SYMBOLS_SHIFTED : KeyboardLayoutId.SYMBOLS;
    }

    @Override public void build(KeyLayout.Input in, List<KeyLayout.Key> out) {
        if (shifted) {
            KeyLayout.addRow(out, in,
                    new String[]{"~", "`", "|", "^", "_", "=", "\\", "/", "[", "]"},
                    new String[]{"~", "`", "|", "^", "_", "=", "\\", "/", "[", "]"});
            KeyLayout.addRow(out, in, 1,
                    new String[]{"{", "}", "<", ">", "€", "£", "¥", "©", "®", "™"},
                    new String[]{"{", "}", "<", ">", "€", "£", "¥", "©", "®", "™"});
            KeyLayout.addRow(out, in, 2,
                    new String[]{"⇪", "•", "√", "π", "÷", "×", "°", "¶", "⌫"},
                    new String[]{"#shift", "•", "√", "π", "÷", "×", "°", "¶", "#del"});
            KeyLayout.addSpaceRow(out, in, 3,
                    new String[]{"ABC", "clip"}, new String[]{"#abc", "#clip"},
                    "space", " ",
                    new String[]{".", "↵"}, new String[]{".", "#enter"});
            return;
        }
        KeyLayout.addRow(out, in,
                KeyLayout.split("1234567890"), KeyLayout.split("1234567890"));
        KeyLayout.addRow(out, in, 1,
                new String[]{"@", "#", "$", "%", "&", "*", "-", "+", "(", ")"},
                new String[]{"@", "#", "$", "%", "&", "*", "-", "+", "(", ")"});
        KeyLayout.addRow(out, in, 2,
                new String[]{"⇧", ",", "?", "!", "'", "\"", ":", ";", "⌫"},
                new String[]{"#shift", ",", "?", "!", "'", "\"", ":", ";", "#del"});
        KeyLayout.addSpaceRow(out, in, 3,
                new String[]{"ABC", "clip"}, new String[]{"#abc", "#clip"},
                "space", " ",
                new String[]{".", "↵"}, new String[]{".", "#enter"});
    }
}
