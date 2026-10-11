package dev.kxki.keyapp.keyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class KeyboardModeTest {
    @Test public void initialIsEnglishUnshifted() {
        KeyboardMode mode = KeyboardMode.initial();
        assertFalse(mode.japanese);
        assertFalse(mode.symbols);
        assertFalse(mode.clipboard);
        assertFalse(mode.isFlickEntry());
    }

    @Test public void flickOnlyOnKanaPanel() {
        assertTrue(new KeyboardMode(true, false, false, false, false, false).isFlickEntry());
        assertFalse(new KeyboardMode(true, true, false, false, false, false).isFlickEntry());
        assertFalse(new KeyboardMode(true, false, true, false, false, false).isFlickEntry());
        assertFalse(new KeyboardMode(true, false, false, true, false, false).isFlickEntry());
    }

    @Test public void shiftToggleAndLockRelease() {
        KeyboardMode mode = KeyboardMode.initial().withShift(true, false);
        assertTrue(mode.shifted);
        KeyboardMode toggled = mode.toggledShift();
        assertFalse(toggled.shifted);
        KeyboardMode locked = KeyboardMode.initial().withShift(true, true);
        assertTrue(locked.toggledShift().shifted == false);
    }

    @Test public void resetForInputClearsPanels() {
        KeyboardMode mode = new KeyboardMode(true, true, true, true, true, true).resetForInput();
        assertEquals(KeyboardMode.initial(), mode);
    }
}
