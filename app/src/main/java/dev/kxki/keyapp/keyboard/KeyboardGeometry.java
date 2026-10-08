package dev.kxki.keyapp.keyboard;

/** Pure keyboard frame math without Android dependencies. */
public final class KeyboardGeometry {
    public static final int MIN_HEIGHT_DP = 220;
    public static final int MAX_HEIGHT_DP = 420;
    public static final int MIN_WIDTH_DP = 280;

    private KeyboardGeometry() {}

    public static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static int dpToPx(float dp, float density) {
        return (int) (dp * density + .5f);
    }

    public static int pxToDp(float px, float density) {
        return Math.round(px / density);
    }

    public static int constrainHeight(int heightDp) {
        return clamp(heightDp, MIN_HEIGHT_DP, MAX_HEIGHT_DP);
    }

    public static int constrainWidth(int widthDp, int availableWidthDp) {
        if (availableWidthDp == Integer.MAX_VALUE) return Math.max(1, widthDp);
        return clamp(widthDp, Math.min(MIN_WIDTH_DP, availableWidthDp), availableWidthDp);
    }

    public static int constrainLeft(int leftDp, int widthDp, int availableWidthDp) {
        if (availableWidthDp == Integer.MAX_VALUE) return Math.max(0, leftDp);
        return clamp(leftDp, 0, Math.max(0, availableWidthDp - widthDp));
    }

    public static int constrainBottomGap(int gapDp, int heightDp, int availableHeightDp) {
        if (availableHeightDp == Integer.MAX_VALUE) return Math.max(0, gapDp);
        return clamp(gapDp, 0, Math.max(0, availableHeightDp - heightDp));
    }
}
