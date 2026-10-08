package dev.kxki.keyapp;

import java.util.List;

/** Pure clipboard helpers without Android dependencies, so they stay unit-testable. */
public final class ClipboardLogic {
    public static final int VISIBLE_ROWS = 3;
    public static final int MAX_HISTORY = 30;

    private ClipboardLogic() {}

    public static boolean shouldBlockCapture(boolean forcedPrivate, boolean manualPrivate) {
        return forcedPrivate || manualPrivate;
    }

    /** Returns null for null/empty/whitespace-only clips, otherwise the original text. */
    public static String normalizeText(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        return text;
    }

    /**
     * Prepends {@code text} with dedup. Returns true when the history changed.
     * Index 0 already holding the text means no change.
     */
    public static boolean mergeItem(List<String> history, String text) {
        int previous = history.indexOf(text);
        if (previous == 0) return false;
        if (previous > 0) history.remove(previous);
        history.add(0, text);
        while (history.size() > MAX_HISTORY) history.remove(history.size() - 1);
        return true;
    }

    public static int clampScroll(int scroll, int size) {
        return Math.max(0, Math.min(scroll, Math.max(0, size - VISIBLE_ROWS)));
    }

    /** Single-line label for a history row, truncated to 24 code points. */
    public static String clipboardLabel(String value) {
        String text = value.replace('\n', ' ').replace('\r', ' ').trim();
        int count = text.codePointCount(0, text.length());
        return count <= 24 ? text : text.substring(0, text.offsetByCodePoints(0, 24)) + "…";
    }

    /** Returns true when the head changed (fresh clip arrived) and view should jump to top. */
    public static boolean headChanged(List<String> previous, List<String> next) {
        String oldHead = (previous == null || previous.isEmpty()) ? null : previous.get(0);
        String newHead = (next == null || next.isEmpty()) ? null : next.get(0);
        return oldHead == null ? newHead != null : !oldHead.equals(newHead);
    }

    /**
     * Prefers the DOWN-time snapshot so a history update between DOWN/UP cannot
     * redirect the paste to a different entry. Falls back to index lookup.
     * A snapshot no longer in history (e.g. cleared mid-press) cancels the paste
     * instead of resurrecting stale text or redirecting to a different entry.
     */
    public static String resolvePaste(String keyValue, List<String> history, String snapshot) {
        if (snapshot != null && !snapshot.isEmpty()) {
            if (history != null && history.contains(snapshot)) return snapshot;
            return null;
        }
        if (keyValue == null || !keyValue.startsWith("#clipboard:")) return null;
        if (history == null) return null;
        int index;
        try {
            index = Integer.parseInt(keyValue.substring("#clipboard:".length()));
        } catch (NumberFormatException notAnIndex) {
            return null;
        }
        if (index < 0 || index >= history.size()) return null;
        return history.get(index);
    }
}
