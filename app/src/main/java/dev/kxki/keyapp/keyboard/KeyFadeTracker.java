package dev.kxki.keyapp.keyboard;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Pure release-fade state tracker.
 *
 * <p>Extracted from the view so fade timing is unit-testable with an explicit clock.
 * Rects are stored as float tuples to keep this class JVM-pure (no {@code RectF}).
 */
public final class KeyFadeTracker {
    public static final int MAX_FADES = 10;

    private static final class Entry {
        final float left;
        final float top;
        final float right;
        final float bottom;
        final float radiusPx;
        final long startMs;

        Entry(float left, float top, float right, float bottom, float radiusPx, long startMs) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.radiusPx = radiusPx;
            this.startMs = startMs;
        }
    }

    public static final class FadeFrame {
        public final float left;
        public final float top;
        public final float right;
        public final float bottom;
        public final float radiusPx;
        /** 0..1 progress of the fade. */
        public final float progress;

        FadeFrame(float left, float top, float right, float bottom, float radiusPx, float progress) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.radiusPx = radiusPx;
            this.progress = progress;
        }
    }

    private final Deque<Entry> entries = new ArrayDeque<>();
    private final int maxEntries;
    private final long durationMs;

    public KeyFadeTracker(long durationMs) {
        this(durationMs, MAX_FADES);
    }

    public KeyFadeTracker(long durationMs, int maxEntries) {
        this.durationMs = Math.max(0, durationMs);
        this.maxEntries = Math.max(1, maxEntries);
    }

    public void add(float left, float top, float right, float bottom, float radiusPx, long nowMs) {
        if (durationMs <= 0) return;
        while (entries.size() >= maxEntries) entries.pollFirst();
        entries.addLast(new Entry(left, top, right, bottom, radiusPx, nowMs));
    }

    /** Removes finished fades as of {@code nowMs}; returns the number removed. */
    public int prune(long nowMs) {
        if (entries.isEmpty() || durationMs <= 0) {
            int size = entries.size();
            entries.clear();
            return size;
        }
        int removed = 0;
        java.util.Iterator<Entry> iterator = entries.iterator();
        while (iterator.hasNext()) {
            if (nowMs - iterator.next().startMs >= durationMs) {
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }

    public void clear() {
        entries.clear();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }

    /** Live frames as of {@code nowMs}; finished ones are excluded. */
    public List<FadeFrame> frames(long nowMs) {
        List<FadeFrame> out = new ArrayList<>(entries.size());
        if (durationMs <= 0) return out;
        for (Entry entry : entries) {
            float progress = (float) (nowMs - entry.startMs) / durationMs;
            if (progress < 0f) progress = 0f;
            if (progress >= 1f) continue;
            out.add(new FadeFrame(entry.left, entry.top, entry.right, entry.bottom,
                    entry.radiusPx, progress));
        }
        return out;
    }

    /**
     * Newest-first progress 0..1 for the key at the given rect, or 1 when not fading.
     * Newest wins so rapid re-taps track the freshest overlay.
     */
    public float progressFor(float left, float top, float right, float bottom, long nowMs) {
        if (entries.isEmpty() || durationMs <= 0) return 1f;
        int size = entries.size();
        Entry[] array = entries.toArray(new Entry[0]);
        for (int i = size - 1; i >= 0; i--) {
            Entry entry = array[i];
            if (entry.left == left && entry.top == top
                    && entry.right == right && entry.bottom == bottom) {
                float progress = (float) (nowMs - entry.startMs) / durationMs;
                return Math.min(1f, Math.max(0f, progress));
            }
        }
        return 1f;
    }
}
