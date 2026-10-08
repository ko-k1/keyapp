package dev.kxki.keyapp.kana;

/** Pure kana editing helpers without Android dependencies. */
public final class KanaTable {
    private static final String[] KANA_MODIFIER_CYCLES = {
            "あぁ", "いぃ", "うぅゔ", "えぇ", "おぉ",
            "かゕが", "きぎ", "くぐ", "けゖげ", "こご",
            "さざ", "しじ", "すず", "せぜ", "そぞ",
            "ただ", "ちぢ", "つっづ", "てで", "とど",
            "はばぱ", "ひびぴ", "ふぶぷ", "へべぺ", "ほぼぽ",
            "やゃ", "ゆゅ", "よょ", "わゎ"
    };

    private KanaTable() {}

    public static String transformLast(String text) {
        if (text.isEmpty()) return text;
        int start = text.offsetByCodePoints(text.length(), -1);
        String last = text.substring(start);
        for (String cycle : KANA_MODIFIER_CYCLES) {
            int index = cycle.indexOf(last);
            if (index >= 0) {
                return text.substring(0, start)
                        + cycle.charAt((index + 1) % cycle.length());
            }
        }
        return text;
    }

    public static String transformBeforeCursor(String text, int cursor) {
        int end = text.offsetByCodePoints(0, clamp(cursor, 0, codePointLength(text)));
        return transformLast(text.substring(0, end)) + text.substring(end);
    }

    public static String insertCodePoints(String text, int cursor, String inserted) {
        int offset = text.offsetByCodePoints(0, clamp(cursor, 0, codePointLength(text)));
        return text.substring(0, offset) + inserted + text.substring(offset);
    }

    public static String removeCodePointBefore(String text, int cursor) {
        int end = text.offsetByCodePoints(0, clamp(cursor, 0, codePointLength(text)));
        if (end == 0) return text;
        int start = text.offsetByCodePoints(end, -1);
        return text.substring(0, start) + text.substring(end);
    }

    public static int codePointLength(String text) { return text.codePointCount(0, text.length()); }

    public static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
