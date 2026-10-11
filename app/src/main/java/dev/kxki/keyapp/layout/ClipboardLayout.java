package dev.kxki.keyapp.layout;

import dev.kxki.keyapp.ClipboardLogic;
import dev.kxki.keyapp.keyboard.KeyLayout;

import java.util.List;

/** Clipboard history panel. */
public final class ClipboardLayout implements KeyboardLayoutProvider {
    @Override public KeyboardLayoutId id() { return KeyboardLayoutId.CLIPBOARD; }

    @Override public void build(KeyLayout.Input in, List<KeyLayout.Key> out) {
        KeyLayout.addRow(out, in,
                new String[]{"Clear", "ABC", "かな"},
                new String[]{"#clear", "#en", "#jp"});
        if (in.clipboardHistory.isEmpty()) {
            KeyLayout.addRow(out, in, 1,
                    new String[]{"Clipboard empty"}, new String[]{""});
        } else {
            for (int row = 0; row < in.visibleClipboardRows
                    && in.clipboardScroll + row < in.clipboardHistory.size(); row++) {
                int index = in.clipboardScroll + row;
                KeyLayout.addRow(out, in, row + 1,
                        new String[]{ClipboardLogic.clipboardLabel(in.clipboardHistory.get(index))},
                        new String[]{"#clipboard:" + index});
            }
        }
    }
}
