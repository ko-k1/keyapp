package dev.kxki.keyapp.layout;

import dev.kxki.keyapp.keyboard.KeyLayout;

import java.util.List;

/** Japanese 12-key flick panel. */
public final class JapaneseFlickLayout implements KeyboardLayoutProvider {
    @Override public KeyboardLayoutId id() { return KeyboardLayoutId.JAPANESE_FLICK; }

    @Override public void build(KeyLayout.Input in, List<KeyLayout.Key> out) {
        KeyLayout.addRow(out, in,
                new String[]{"clip", "あ", "か", "さ", "⌫"},
                new String[]{"#clip", "あ", "か", "さ", "#del"});
        KeyLayout.addRow(out, in, 1,
                new String[]{"ABC", "た", "な", "は", "space"},
                new String[]{"#en", "た", "な", "は", " "});
        KeyLayout.addRow(out, in, 2,
                new String[]{"123", "ま", "や", "ら", ""},
                new String[]{"#num", "ま", "や", "ら", ""});
        KeyLayout.addRow(out, in, 3,
                new String[]{"", "小", "わ", "、。", "return"},
                new String[]{"", "#small", "わ", "、", "#enter"});
    }
}
