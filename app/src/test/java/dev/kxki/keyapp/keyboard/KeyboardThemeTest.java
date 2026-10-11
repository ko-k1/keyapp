package dev.kxki.keyapp.keyboard;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class KeyboardThemeTest {
    @Test public void blendEndpoints() {
        assertEquals(KeyboardTheme.PRESSED_LABEL,
                KeyboardTheme.blend(KeyboardTheme.PRESSED_LABEL, KeyboardTheme.KEY_LABEL, 0f));
        assertEquals(KeyboardTheme.KEY_LABEL,
                KeyboardTheme.blend(KeyboardTheme.PRESSED_LABEL, KeyboardTheme.KEY_LABEL, 1f));
    }

    @Test public void fadeAlphaDecays() {
        int start = KeyboardTheme.fadeAlpha(0f);
        int end = KeyboardTheme.fadeAlpha(1f);
        assertEquals(0, end);
        assertEquals(0xff, start);
    }
}
