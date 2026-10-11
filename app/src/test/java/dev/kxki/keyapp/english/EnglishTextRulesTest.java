package dev.kxki.keyapp.english;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class EnglishTextRulesTest {
    @Test public void autoCapsAtStartAndAfterSentence() {
        assertTrue(EnglishTextRules.shouldAutoCapitalize(""));
        assertTrue(EnglishTextRules.shouldAutoCapitalize("   "));
        assertTrue(EnglishTextRules.shouldAutoCapitalize("Hello. "));
        assertTrue(EnglishTextRules.shouldAutoCapitalize("Really?  "));
        assertTrue(EnglishTextRules.shouldAutoCapitalize("Hi!\n"));
        assertTrue(EnglishTextRules.shouldAutoCapitalize("line1\n"));
        assertFalse(EnglishTextRules.shouldAutoCapitalize("Hello"));
        assertFalse(EnglishTextRules.shouldAutoCapitalize("Hello, "));
        assertFalse(EnglishTextRules.shouldAutoCapitalize("3.14 "));
    }

    @Test public void doubleSpaceNeedsFastSecondSpace() {
        assertTrue(EnglishTextRules.isDoubleSpace(1000, 500, "hello "));
        assertFalse(EnglishTextRules.isDoubleSpace(5000, 500, "hello "));
        assertFalse(EnglishTextRules.isDoubleSpace(1000, Long.MIN_VALUE, "hello "));
        assertFalse(EnglishTextRules.isDoubleSpace(1000, 500, "hello"));
        assertFalse(EnglishTextRules.isDoubleSpace(1000, 500, "hello. "));
        assertFalse(EnglishTextRules.isDoubleSpace(1000, 500, "hello  "));
        assertFalse(EnglishTextRules.isDoubleSpace(1000, 500, ""));
    }

    @Test public void longPressCoversTopRowDigits() {
        assertEquals("1", EnglishTextRules.longPressAlt("q"));
        assertEquals("1", EnglishTextRules.longPressAlt("Q"));
        assertEquals("0", EnglishTextRules.longPressAlt("p"));
        assertEquals("@", EnglishTextRules.longPressAlt("a"));
        assertEquals(",", EnglishTextRules.longPressAlt("m"));
        assertNull(EnglishTextRules.longPressAlt(" "));
        assertNull(EnglishTextRules.longPressAlt("1"));
        assertNull(EnglishTextRules.longPressAlt(null));
    }
}
