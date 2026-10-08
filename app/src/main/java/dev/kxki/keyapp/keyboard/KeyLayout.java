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
        List<Key> keys = new ArrayList<>();
        if (in.clipboard) {
            addRow(keys, in, new String[]{"Clear", "ABC", "かな"},
                    new String[]{"#clear", "#en", "#jp"});
            if (in.clipboardHistory.isEmpty()) {
                addRow(keys, in, 1, new String[]{"Clipboard empty"}, new String[]{""});
            } else {
                for (int row = 0; row < in.visibleClipboardRows
                        && in.clipboardScroll + row < in.clipboardHistory.size(); row++) {
                    int index = in.clipboardScroll + row;
                    addRow(keys, in, row + 1,
                            new String[]{ClipboardLogic.clipboardLabel(in.clipboardHistory.get(index))},
                            new String[]{"#clipboard:" + index});
                }
            }
        } else if (in.symbols) {
            buildSymbols(keys, in);
        } else if (in.japanese && in.numberMode) {
            buildJapaneseNumbers(keys, in);
        } else if (in.japanese) {
            buildJapanese(keys, in);
        } else {
            buildQwerty(keys, in);
        }
        return keys;
    }

    private static void buildSymbols(List<Key> keys, Input in) {
        if (in.shifted) {
            addRow(keys, in, new String[]{"~", "`", "|", "^", "_", "=", "\\", "/", "[", "]"}, new String[]{"~", "`", "|", "^", "_", "=", "\\", "/", "[", "]"});
            addRow(keys, in, 1, new String[]{"{", "}", "<", ">", "€", "£", "¥", "©", "®", "™"}, new String[]{"{", "}", "<", ">", "€", "£", "¥", "©", "®", "™"});
            addRow(keys, in, 2, new String[]{"⇪", "•", "√", "π", "÷", "×", "°", "¶", "⌫"}, new String[]{"#shift", "•", "√", "π", "÷", "×", "°", "¶", "#del"});
            addSpaceRow(keys, in, 3, new String[]{"ABC", "clip"}, new String[]{"#abc", "#clip"}, "space", " ", new String[]{".", "↵"}, new String[]{".", "#enter"});
            return;
        }
        addRow(keys, in, split("1234567890"), split("1234567890"));
        addRow(keys, in, 1, new String[]{"@", "#", "$", "%", "&", "*", "-", "+", "(", ")"}, new String[]{"@", "#", "$", "%", "&", "*", "-", "+", "(", ")"});
        addRow(keys, in, 2, new String[]{"⇧", ",", "?", "!", "'", "\"", ":", ";", "⌫"}, new String[]{"#shift", ",", "?", "!", "'", "\"", ":", ";", "#del"});
        addSpaceRow(keys, in, 3, new String[]{"ABC", "clip"}, new String[]{"#abc", "#clip"}, "space", " ", new String[]{".", "↵"}, new String[]{".", "#enter"});
    }

    private static void buildQwerty(List<Key> keys, Input in) {
        String first = in.shifted ? "QWERTYUIOP" : "qwertyuiop";
        String second = in.shifted ? "ASDFGHJKL" : "asdfghjkl";
        addRow(keys, in, split(first), split("qwertyuiop"));
        addRow(keys, in, 1, split(second), split("asdfghjkl"));
        addRow(keys, in, 2, new String[]{in.shifted ? "⇪" : "⇧", in.shifted ? "Z" : "z", in.shifted ? "X" : "x", in.shifted ? "C" : "c", in.shifted ? "V" : "v", in.shifted ? "B" : "b", in.shifted ? "N" : "n", in.shifted ? "M" : "m", "⌫"}, new String[]{"#shift", "z", "x", "c", "v", "b", "n", "m", "#del"});
        addSpaceRow(keys, in, 3, new String[]{"123", "かな"}, new String[]{"#sym", "#jp"}, "space", " ", new String[]{"clip", "↵"}, new String[]{"#clip", "#enter"});
    }

    private static void buildJapanese(List<Key> keys, Input in) {
        addRow(keys, in, new String[]{"clip", "あ", "か", "さ", "⌫"}, new String[]{"#clip", "あ", "か", "さ", "#del"});
        addRow(keys, in, 1, new String[]{"ABC", "た", "な", "は", "space"}, new String[]{"#en", "た", "な", "は", " "});
        addRow(keys, in, 2, new String[]{"123", "ま", "や", "ら", ""}, new String[]{"#num", "ま", "や", "ら", ""});
        addRow(keys, in, 3, new String[]{"", "小", "わ", "、。", "return"}, new String[]{"", "#small", "わ", "、", "#enter"});
    }

    private static void buildJapaneseNumbers(List<Key> keys, Input in) {
        addRow(keys, in, new String[]{"clip", "1", "2", "3", "⌫"}, new String[]{"#clip", "1", "2", "3", "#del"});
        addRow(keys, in, 1, new String[]{"ABC", "4", "5", "6", "space"}, new String[]{"#en", "4", "5", "6", " "});
        addRow(keys, in, 2, new String[]{"かな", "7", "8", "9", ","}, new String[]{"#jp", "7", "8", "9", ","});
        addRow(keys, in, 3, new String[]{"-", ".", "0", "/", "return"}, new String[]{"-", ".", "0", "/", "#enter"});
    }

    private static void addRow(List<Key> keys, Input in, String[] labels, String[] values) {
        addRow(keys, in, 0, labels, values);
    }

    private static void addRow(List<Key> keys, Input in, int row, String[] labels, String[] values) {
        float top = in.topPx + in.pitchPx * row;
        float x = in.leftPx + in.marginPx;
        float keyWidth = (in.widthPx - in.marginPx * 2 - in.gapPx * (labels.length - 1)) / labels.length;
        for (int i = 0; i < labels.length; i++) {
            keys.add(new Key(x, top, x + keyWidth, top + in.keyHeightPx, labels[i], values[i]));
            x += keyWidth + in.gapPx;
        }
    }

    private static void addSpaceRow(List<Key> keys, Input in, int row, String[] leftLabels,
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

    private static String[] split(String text) {
        String[] values = new String[text.length()];
        for (int i = 0; i < text.length(); i++) values[i] = String.valueOf(text.charAt(i));
        return values;
    }
}
