package dev.kxki.keyapp;

import java.util.List;

/** Pure clipboard helpers without Android dependencies, so they stay unit-testable. */
final class ClipboardLogic {
    static final int VISIBLE_ROWS = 3;

    private ClipboardLogic() {}

    static boolean shouldBlockCapture(boolean forcedPrivate, boolean manualPrivate) {
        return forcedPrivate || manualPrivate;
    }

    /** Returns null for null/empty/whitespace-only clips, otherwise the original text. */
    static String normalizeText(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        return text;
    }

    /**
     * Prepends {@code text} with dedup. Returns true when the history changed.
     * Index 0 already holding the text means no change.
     */
    static boolean mergeItem(List<String> history, String text) {
        int previous = history.indexOf(text);
        if (previous == 0) return false;
        if (previous > 0) history.remove(previous);
        history.add(0, text);
        return true;
    }

    static int clampScroll(int scroll, int size) {
        return Math.max(0, Math.min(scroll, Math.max(0, size - VISIBLE_ROWS)));
    }

    /** Returns true when the head changed (fresh clip arrived) and view should jump to top. */
    static boolean headChanged(List<String> previous, List<String> next) {
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
    static String resolvePaste(String keyValue, List<String> history, String snapshot) {
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
