package dev.kxki.keyapp.layout;

import static org.junit.Assert.assertEquals;

import dev.kxki.keyapp.keyboard.KeyLayout;

import org.junit.Test;

public final class LayoutRegistryTest {
    private static KeyLayout.Input input() {
        KeyLayout.Input in = new KeyLayout.Input();
        in.leftPx = 0;
        in.topPx = 100;
        in.widthPx = 1000;
        in.keyHeightPx = 90;
        in.pitchPx = 100;
        in.gapPx = 6;
        in.marginPx = 24;
        return in;
    }

    @Test public void resolvesPanels() {
        KeyLayout.Input in = input();
        assertEquals(KeyboardLayoutId.QWERTY, LayoutRegistry.resolve(in));
        in.japanese = true;
        assertEquals(KeyboardLayoutId.JAPANESE_FLICK, LayoutRegistry.resolve(in));
        in.numberMode = true;
        assertEquals(KeyboardLayoutId.JAPANESE_NUMBERS, LayoutRegistry.resolve(in));
        in.numberMode = false;
        in.symbols = true;
        assertEquals(KeyboardLayoutId.SYMBOLS, LayoutRegistry.resolve(in));
        in.shifted = true;
        assertEquals(KeyboardLayoutId.SYMBOLS_SHIFTED, LayoutRegistry.resolve(in));
        in.clipboard = true;
        assertEquals(KeyboardLayoutId.CLIPBOARD, LayoutRegistry.resolve(in));
    }

    @Test public void qwertyRowCountUnchanged() {
        // Same counts the legacy KeyLayoutTest asserts: registry must not change output.
        assertEquals(10 + 9 + 9 + 5, LayoutRegistry.build(input()).size());
    }
}
