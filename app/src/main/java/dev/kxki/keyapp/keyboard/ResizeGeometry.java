package dev.kxki.keyapp.keyboard;

/**
 * Pure keyboard-frame resize math.
 *
 * <p>Extracted from the view's resize touch handler so the edge/drag arithmetic is
 * unit-testable without touch events or display metrics.
 */
public final class ResizeGeometry {
    private ResizeGeometry() {}

    public static final int LEFT = 1;
    public static final int TOP = 2;
    public static final int RIGHT = 4;
    public static final int BOTTOM = 8;
    public static final int MOVE = 16;

    public static final class Frame {
        public final int widthDp;
        public final int heightDp;
        public final int leftDp;
        public final int bottomGapDp;

        public Frame(int widthDp, int heightDp, int leftDp, int bottomGapDp) {
            this.widthDp = widthDp;
            this.heightDp = heightDp;
            this.leftDp = leftDp;
            this.bottomGapDp = bottomGapDp;
        }
    }

    public static int detectEdges(float x, float y, float keyboardLeftPx, float keyboardRightPx,
            float keyboardBottomPx, float slopPx) {
        boolean withinWidth = x >= keyboardLeftPx - slopPx && x <= keyboardRightPx + slopPx;
        boolean withinHeight = y >= 0 && y <= keyboardBottomPx + slopPx;
        int edges = 0;
        if (withinHeight && Math.abs(x - keyboardLeftPx) <= slopPx) edges |= LEFT;
        if (withinWidth && y <= slopPx) edges |= TOP;
        if (withinHeight && Math.abs(x - keyboardRightPx) <= slopPx) edges |= RIGHT;
        if (withinWidth && Math.abs(y - keyboardBottomPx) <= slopPx) edges |= BOTTOM;
        if (edges == 0 && x > keyboardLeftPx && x < keyboardRightPx
                && y > 0 && y < keyboardBottomPx) {
            edges = MOVE;
        }
        return edges;
    }

    /**
     * Applies a drag delta to a start frame.
     *
     * @param minWidthDp smallest allowed width
     * @param maxRightDp right bound for the frame
     * @param maxHeightDp tallest allowed keyboard
     * @param maxGapDp largest allowed bottom gap
     */
    public static Frame applyDrag(int edges, Frame start, int dxDp, int dyDp,
            int minWidthDp, int maxRightDp, int maxHeightDp, int maxGapDp) {
        int width = start.widthDp;
        int height = start.heightDp;
        int left = start.leftDp;
        int gap = start.bottomGapDp;
        if ((edges & LEFT) != 0) {
            int right = start.leftDp + start.widthDp;
            left = clamp(start.leftDp + dxDp, 0, right - minWidthDp);
            width = right - left;
        } else if ((edges & RIGHT) != 0) {
            int right = clamp(start.leftDp + start.widthDp + dxDp,
                    start.leftDp + minWidthDp, maxRightDp);
            width = right - start.leftDp;
        } else if ((edges & MOVE) != 0) {
            left = clamp(start.leftDp + dxDp, 0, Math.max(0, maxRightDp - start.widthDp));
        }
        if ((edges & TOP) != 0) {
            height = clamp(start.heightDp - dyDp,
                    Math.min(KeyboardGeometry.MIN_HEIGHT_DP, maxHeightDp), maxHeightDp);
        } else if ((edges & BOTTOM) != 0) {
            int total = start.heightDp + start.bottomGapDp;
            height = clamp(start.heightDp + dyDp,
                    Math.min(KeyboardGeometry.MIN_HEIGHT_DP, total),
                    Math.min(KeyboardGeometry.MAX_HEIGHT_DP, total));
            gap = total - height;
        } else if ((edges & MOVE) != 0) {
            gap = clamp(start.bottomGapDp - dyDp, 0, maxGapDp);
        }
        return new Frame(width, height, left, gap);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
