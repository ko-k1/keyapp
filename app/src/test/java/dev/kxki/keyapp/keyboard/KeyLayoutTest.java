package dev.kxki.keyapp.keyboard;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public final class KeyLayoutTest {
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

    @Test public void emptyClipboardPutsPlaceholderOnItsOwnRow() {
        KeyLayout.Input in = input();
        in.clipboard = true;
        List<KeyLayout.Key> keys = KeyLayout.build(in);
        assertEquals(4, keys.size());
        assertEquals("#clear", keys.get(0).value);
        assertEquals(100f, keys.get(0).top, 0f);
        assertEquals("", keys.get(3).value);
        assertEquals("Clipboard empty", keys.get(3).label);
        assertEquals(200f, keys.get(3).top, 0f);
    }

    @Test public void clipboardHistoryRowsFollowTheHeader() {
        KeyLayout.Input in = input();
        in.clipboard = true;
        in.clipboardHistory = Arrays.asList("one", "two");
        List<KeyLayout.Key> keys = KeyLayout.build(in);
        assertEquals(5, keys.size());
        assertEquals("#clipboard:0", keys.get(3).value);
        assertEquals("one", keys.get(3).label);
        assertEquals(200f, keys.get(3).top, 0f);
        assertEquals("#clipboard:1", keys.get(4).value);
        assertEquals(300f, keys.get(4).top, 0f);
    }

    @Test public void qwertyRowsDoNotOverlap() {
        List<KeyLayout.Key> keys = KeyLayout.build(input());
        assertEquals(10 + 9 + 9 + 5, keys.size());
        for (int i = 0; i < 10; i++) assertEquals(100f, keys.get(i).top, 0f);
        assertEquals("q", keys.get(0).value);
        assertEquals(400f, keys.get(keys.size() - 1).top, 0f);
    }

    @Test public void hitTestFindsKeysAndRejectsOutside() {
        KeyLayout.Input in = input();
        in.clipboard = true;
        List<KeyLayout.Key> keys = KeyLayout.build(in);
        KeyLayout.Key header = keys.get(0);
        assertEquals(0, KeyLayout.hitTest(keys,
                (header.left + header.right) / 2f, (header.top + header.bottom) / 2f, 3, 4));
        KeyLayout.Key placeholder = keys.get(3);
        assertEquals(3, KeyLayout.hitTest(keys,
                (placeholder.left + placeholder.right) / 2f,
                (placeholder.top + placeholder.bottom) / 2f, 3, 4));
        assertEquals(-1, KeyLayout.hitTest(keys, 500f, 50f, 3, 4));
    }
}
