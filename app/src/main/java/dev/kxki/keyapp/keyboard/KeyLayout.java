package dev.kxki.keyapp.keyboard;

import android.graphics.RectF;

import dev.kxki.keyapp.ClipboardLogic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure key-arrangement builder: turns keyboard modes and pixel metrics into
 * hit-testable key rects. The view computes metrics (needs density) and keeps
 * the resulting list for drawing and touch handling.
 */
public final class KeyLayout {
    private KeyLayout() {}

    public static final class Key {
        public final float left;
        public final float top;
        public final float right;
        public final float bottom;
        public final String label;
        public final String value;

        public Key(float left, float top, float right, float bottom, String label, String value) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.label = label;
            this.value = value;
        }

        /** Android rect for drawing and hit-testing; plain fields keep layout unit-testable. */
        public RectF rect() {
            return new RectF(left, top, right, bottom);
        }

        /** Value comparison without allocating rects (hot path: pressed-key highlight). */
        public boolean sameBounds(Key other) {
            return other != null && left == other.left && top == other.top
                    && right == other.right && bottom == other.bottom;
        }
    }

    /** All inputs {@link #build} needs; metrics are in px. */
    public static final class Input {
        public boolean japanese;
        public boolean symbols;
        public boolean numberMode;
        public boolean clipboard;
        public boolean shifted;
        public List<String> clipboardHistory = Collections.emptyList();
        public int clipboardScroll;
        public int visibleClipboardRows = ClipboardLogic.VISIBLE_ROWS;
        public float leftPx;
        public float topPx;
        public float widthPx;
        public float keyHeightPx;
        public float pitchPx;
        public float gapPx;
        public float marginPx;
    }

    public static List<Key> build(Input in) {
        return dev.kxki.keyapp.layout.LayoutRegistry.build(in);
    }

    public static void addRow(List<Key> keys, Input in, String[] labels, String[] values) {
        addRow(keys, in, 0, labels, values);
    }

    public static void addRow(List<Key> keys, Input in, int row, String[] labels, String[] values) {
        float top = in.topPx + in.pitchPx * row;
        float x = in.leftPx + in.marginPx;
        float keyWidth = (in.widthPx - in.marginPx * 2 - in.gapPx * (labels.length - 1)) / labels.length;
        for (int i = 0; i < labels.length; i++) {
            keys.add(new Key(x, top, x + keyWidth, top + in.keyHeightPx, labels[i], values[i]));
            x += keyWidth + in.gapPx;
        }
    }

    public static void addSpaceRow(List<Key> keys, Input in, int row, String[] leftLabels,
            String[] leftValues, String centerLabel, String centerValue,
            String[] rightLabels, String[] rightValues) {
        float top = in.topPx + in.pitchPx * row;
        float gap = in.gapPx;
        float keyWidth = (in.widthPx - in.marginPx * 2 - gap * (leftLabels.length + rightLabels.length))
                / (leftLabels.length + rightLabels.length + 4);
        float x = in.leftPx + in.marginPx;
        for (int i = 0; i < leftLabels.length; i++) {
            keys.add(new Key(x, top, x + keyWidth, top + in.keyHeightPx, leftLabels[i], leftValues[i]));
            x += keyWidth + gap;
        }
        keys.add(new Key(x, top, x + keyWidth * 4, top + in.keyHeightPx, centerLabel, centerValue));
        x += keyWidth * 4 + gap;
        for (int i = 0; i < rightLabels.length; i++) {
            keys.add(new Key(x, top, x + keyWidth, top + in.keyHeightPx, rightLabels[i], rightValues[i]));
            x += keyWidth + gap;
        }
    }

    /**
     * Nearest-key hit test with slop. Returns the key index, or -1 when the
     * touch is outside the key grid.
     */
    public static int hitTest(
            List<Key> keys, float x, float y, float horizontalSlopPx, float verticalSlopPx) {
        if (keys.isEmpty()) return -1;
        float left = Float.MAX_VALUE;
        float top = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        float bottom = -Float.MAX_VALUE;
        for (Key key : keys) {
            left = Math.min(left, key.left);
            top = Math.min(top, key.top);
            right = Math.max(right, key.right);
            bottom = Math.max(bottom, key.bottom);
        }
        if (x < left || x >= right || y < top || y >= bottom) return -1;

        int best = -1;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0; i < keys.size(); i++) {
            Key key = keys.get(i);
            if (x < key.left - horizontalSlopPx || x >= key.right + horizontalSlopPx
                    || y < key.top - verticalSlopPx || y >= key.bottom + verticalSlopPx) continue;
            float dx = Math.max(key.left - x, Math.max(0, x - key.right));
            float dy = Math.max(key.top - y, Math.max(0, y - key.bottom));
            float distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                best = i;
                bestDistance = distance;
            }
        }
        return best;
    }

    public static String[] split(String text) {
        String[] values = new String[text.length()];
        for (int i = 0; i < text.length(); i++) values[i] = String.valueOf(text.charAt(i));
        return values;
    }
}
