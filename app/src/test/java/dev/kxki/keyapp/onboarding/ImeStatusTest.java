package dev.kxki.keyapp.onboarding;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ImeStatusTest {
    private static final String IME = "com.example/.KeyAppImeService";

    @Test public void detectsEnabledEntry() {
        assertTrue(ImeStatus.isEnabledInList("a:" + IME + ":b", IME));
        assertFalse(ImeStatus.isEnabledInList("a:b", IME));
        assertFalse(ImeStatus.isEnabledInList("", IME));
        assertFalse(ImeStatus.isEnabledInList(null, IME));
    }

    @Test public void detectsSelectedIme() {
        assertTrue(ImeStatus.isSelected(IME + "/extra", IME));
        assertFalse(ImeStatus.isSelected("com.other/.Ime", IME));
        assertFalse(ImeStatus.isSelected(null, IME));
    }
}
