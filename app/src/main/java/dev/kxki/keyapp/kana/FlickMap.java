package dev.kxki.keyapp.kana;

/** Pure 12-key flick mapping without Android dependencies. */
public final class FlickMap {
    public static final float FLICK_THRESHOLD_DP = 20f;

    private static final String[][] FLICK_MAP = {
            {"あ", "い", "う", "え", "お"}, {"か", "き", "く", "け", "こ"},
            {"さ", "し", "す", "せ", "そ"}, {"た", "ち", "つ", "て", "と"},
            {"な", "に", "ぬ", "ね", "の"}, {"は", "ひ", "ふ", "へ", "ほ"},
            {"ま", "み", "む", "め", "も"}, {"や", "ゆ", "よ", "や", "よ"},
            {"ら", "り", "る", "れ", "ろ"}, {"わ", "を", "ん", "ー", "わ"},
            {"、", "。", "？", "！", "・"}
    };

    private FlickMap() {}

    public static String[] flickRow(String base) {
        for (String[] row : FLICK_MAP) if (row[0].equals(base)) return row;
        return null;
    }

    /** Pure index lookup; caller supplies threshold in px. */
    public static int flickIndex(float dx, float dy, float thresholdPx) {
        if (Math.abs(dx) + Math.abs(dy) <= thresholdPx) return 0;
        return Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 3 : 1) : (dy > 0 ? 4 : 2);
    }

    public static String resolveFlick(String base, float dx, float dy, float thresholdPx) {
        String[] row = flickRow(base);
        return row == null ? base : row[flickIndex(dx, dy, thresholdPx)];
    }
}
