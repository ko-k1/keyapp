package dev.kxki.keyapp.keyboard;

/**
 * Centralized keyboard appearance.
 *
 * <p>Previously colors and dimensions were hardcoded at each draw call site inside the
 * 1200-line view. Centralizing them here gives general-purpose app behavior for free:
 * one place to support night mode, high-contrast accessibility, or user themes later.
 *
 * <p>JVM-pure on purpose (raw ARGB literals, bit-math blending): no
 * {@code android.graphics.Color} calls so plain unit tests can exercise it.
 */
public final class KeyboardTheme {
    private KeyboardTheme() {}

    // Dimensions (dp).
    public static final float CANDIDATE_BAR_DP = 40f;
    public static final float EXPANDED_CANDIDATES_DP = 116f;
    public static final float FLICK_TILE_DP = 48f;
    public static final float CURSOR_STEP_DP = 12f;
    public static final float CURSOR_ACCELERATION_DP_PER_SECOND = 1200f;

    // Colors (ARGB literals; previously Color.argb(...) at call sites).
    /** 0xff50506e highlight. */
    public static final int HIGHLIGHT = 0xff50506e;
    /** argb(160, 30, 30, 40) scrim. */
    public static final int SCRIM = 0xa01e1e28;
    /** argb(190, 50, 50, 65) key background. */
    public static final int KEY_BACKGROUND = 0xbe323241;
    /** rgb(220, 220, 230) key label. */
    public static final int KEY_LABEL = 0xffdcdce6;
    /** argb(120, 68, 68, 92) candidate strip chip. */
    public static final int CANDIDATE_CHIP = 0x7844445c;
    /** argb(140, 68, 68, 92) expanded candidate row. */
    public static final int CANDIDATE_ROW = 0x8c44445c;
    /** rgb(225, 225, 238) candidate text. */
    public static final int CANDIDATE_LABEL = 0xffe1e1ee;
    /** rgb(255, 204, 110) private-mode badge. */
    public static final int PRIVATE_BADGE = 0xffffcc6e;
    /** rgb(180, 190, 210) normal-mode badge. */
    public static final int NORMAL_BADGE = 0xffb4bed2;
    /** rgb(255, 194, 120) kana-only warning. */
    public static final int WARNING_TEXT = 0xffffc278;
    /** White pressed label. */
    public static final int PRESSED_LABEL = 0xffffffff;

    public static int keyBackground(boolean highlighted) {
        return highlighted ? HIGHLIGHT : KEY_BACKGROUND;
    }

    /** Blends {@code from} to {@code to} by {@code t} in [0, 1] using channel bit-math. */
    public static int blend(int from, int to, float t) {
        float clamped = t < 0f ? 0f : (t > 1f ? 1f : t);
        int a = Math.round(alpha(from) + (alpha(to) - alpha(from)) * clamped);
        int r = Math.round(red(from) + (red(to) - red(from)) * clamped);
        int g = Math.round(green(from) + (green(to) - green(from)) * clamped);
        int b = Math.round(blue(from) + (blue(to) - blue(from)) * clamped);
        return (a << 24) | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
    }

    /** Remaining highlight alpha for a release fade at {@code progress} in [0, 1]. */
    public static int fadeAlpha(float progress) {
        float clamped = progress < 0f ? 0f : (progress > 1f ? 1f : progress);
        return Math.round(alpha(HIGHLIGHT) * (1f - clamped));
    }

    public static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00ffffff);
    }

    private static int alpha(int color) { return (color >>> 24) & 0xff; }
    private static int red(int color) { return (color >> 16) & 0xff; }
    private static int green(int color) { return (color >> 8) & 0xff; }
    private static int blue(int color) { return color & 0xff; }
}
