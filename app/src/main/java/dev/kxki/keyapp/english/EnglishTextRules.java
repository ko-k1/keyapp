package dev.kxki.keyapp.english;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Pure English typing helpers without Android dependencies.
 *
 * <p>Covers the small quality-of-life rules missing from the raw QWERTY panel:
 * double-tap shift for caps lock, double-space for period, auto-caps after
 * sentence enders, and long-press alternates for letters.
 */
public final class EnglishTextRules {
    /** Max gap between two spaces to convert to a period. */
    public static final long DOUBLE_SPACE_TIMEOUT_MS = 800;
    /** How many chars before the cursor auto-caps inspects. */
    public static final int AUTO_CAPS_LOOKBACK = 32;

    private static final Map<String, String> LONG_PRESS_ALTS = buildLongPressAlts();

    private EnglishTextRules() {}

    /** Sentence-ending punctuation that triggers auto-caps. */
    public static boolean isSentenceEnder(int codePoint) {
        return codePoint == '.' || codePoint == '?' || codePoint == '!';
    }

    /**
     * Whether the next Latin letter should be auto-capitalized given the text
     * before the cursor. True at empty input, after a newline, or after a
     * sentence ender followed by whitespace.
     */
    public static boolean shouldAutoCapitalize(CharSequence beforeCursor) {
        if (beforeCursor == null || beforeCursor.length() == 0) return true;
        int end = beforeCursor.length();
        int index = end - 1;
        while (index >= 0) {
            char current = beforeCursor.charAt(index);
            if (current == ' ' || current == '\t') {
                index--;
                continue;
            }
            if (current == '\n') return true;
            return isSentenceEnder(current);
        }
        // Only whitespace before the cursor: start of the field.
        return true;
    }

    /**
     * Whether a second fast space should become a period. Guards triple-space
     * by refusing when the text already ends with a sentence ender + space or
     * a doubled space/newline.
     */
    public static boolean isDoubleSpace(
            long nowMs, long lastSpaceMs, CharSequence beforeCursor) {
        if (lastSpaceMs == Long.MIN_VALUE) return false;
        if (nowMs - lastSpaceMs > DOUBLE_SPACE_TIMEOUT_MS) return false;
        if (beforeCursor == null || beforeCursor.length() == 0) return false;
        int end = beforeCursor.length();
        if (beforeCursor.charAt(end - 1) != ' ') return false;
        if (end == 1) return true;
        char previous = beforeCursor.charAt(end - 2);
        return previous != ' ' && previous != '\n'
                && !isSentenceEnder(previous);
    }

    /** Long-press alternate for a QWERTY letter value, or null when none. */
    public static String longPressAlt(String value) {
        if (value == null || value.length() != 1) return null;
        return LONG_PRESS_ALTS.get(value.toLowerCase(java.util.Locale.ROOT));
    }

    public static Map<String, String> longPressAlts() {
        return LONG_PRESS_ALTS;
    }

    private static Map<String, String> buildLongPressAlts() {
        Map<String, String> map = new HashMap<>();
        String top = "qwertyuiop";
        String digits = "1234567890";
        for (int i = 0; i < top.length(); i++) {
            map.put(String.valueOf(top.charAt(i)), String.valueOf(digits.charAt(i)));
        }
        map.put("a", "@");
        map.put("s", "#");
        map.put("d", "$");
        map.put("f", "%");
        map.put("g", "&");
        map.put("h", "*");
        map.put("j", "-");
        map.put("k", "+");
        map.put("l", "/");
        map.put("z", "?");
        map.put("x", "!");
        map.put("c", "'");
        map.put("v", "\"");
        map.put("b", ":");
        map.put("n", ";");
        map.put("m", ",");
        return Collections.unmodifiableMap(map);
    }
}
