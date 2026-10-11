package dev.kxki.keyapp.keyboard;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure candidate-strip model extracted from the view.
 *
 * <p>Owns content-equality, focus tracking, and horizontal strip-scroll settlement so the
 * view only draws. Works on its own {@link Item} POJO to stay decoupled from both the
 * IME layer ({@code ImeCandidate}) and the view layer ({@code KeyboardView.Candidate}):
 * callers map in, this class decides.
 */
public final class CandidateStrip {
    private CandidateStrip() {}

    public static final class Item {
        public final int id;
        public final String value;
        public final String label;
        public final boolean focused;

        public Item(int id, String value, String label, boolean focused) {
            this.id = id;
            this.value = value == null ? "" : value;
            this.label = label == null ? "" : label;
            this.focused = focused;
        }
    }

    public static boolean sameContent(List<Item> first, List<Item> second) {
        if (first == second) return true;
        if (first == null || second == null || first.size() != second.size()) return false;
        for (int i = 0; i < first.size(); i++) {
            Item a = first.get(i);
            Item b = second.get(i);
            if (a.id != b.id || !a.value.equals(b.value) || !a.label.equals(b.label)) return false;
        }
        return true;
    }

    public static int focusedId(List<Item> source) {
        if (source == null) return Integer.MIN_VALUE;
        for (Item item : source) if (item.focused) return item.id;
        return Integer.MIN_VALUE;
    }

    /**
     * Settles the strip scroll so the focused chip is visible.
     *
     * @param widths content widths per candidate in px (parallel to {@code items})
     * @param gapPx inter-chip gap in px
     * @param availableWidthPx visible strip width in px
     * @param currentScroll current scroll in px
     * @return settled scroll in px, clamped to [0, maxScroll]
     */
    public static float settleScroll(List<Item> items, List<Float> widths, float gapPx,
            float availableWidthPx, float currentScroll) {
        if (items == null || items.isEmpty() || widths == null
                || widths.size() != items.size() || availableWidthPx <= 0) {
            return 0f;
        }
        float contentWidth = -gapPx;
        float focusedLeft = -1f;
        float focusedRight = -1f;
        for (int i = 0; i < items.size(); i++) {
            float width = widths.get(i);
            if (items.get(i).focused) {
                focusedLeft = contentWidth + gapPx;
                focusedRight = focusedLeft + width - gapPx;
            }
            contentWidth += width + gapPx;
        }
        contentWidth = Math.max(0f, contentWidth);
        float maxScroll = Math.max(0f, contentWidth - availableWidthPx);
        float settled = Math.max(0f, Math.min(currentScroll, maxScroll));
        if (focusedLeft >= 0) {
            if (focusedLeft < settled) settled = focusedLeft;
            else if (focusedRight > settled + availableWidthPx) settled = focusedRight - availableWidthPx;
        }
        return Math.max(0f, Math.min(settled, maxScroll));
    }

    public static float maxScroll(List<Float> widths, float gapPx, float availableWidthPx) {
        if (widths == null || widths.isEmpty() || availableWidthPx <= 0) return 0f;
        float content = -gapPx;
        for (float width : widths) content += width + gapPx;
        return Math.max(0f, Math.max(0f, content) - availableWidthPx);
    }

    public static List<Item> copyOf(List<Item> source) {
        List<Item> out = new ArrayList<>(source == null ? 0 : source.size());
        if (source != null) out.addAll(source);
        return out;
    }
}
