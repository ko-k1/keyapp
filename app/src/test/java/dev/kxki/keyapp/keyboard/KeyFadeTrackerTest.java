package dev.kxki.keyapp.keyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class KeyFadeTrackerTest {
    @Test public void newestProgressWinsOnRetap() {
        KeyFadeTracker tracker = new KeyFadeTracker(160);
        tracker.add(0, 0, 10, 10, 4, 1000);
        tracker.add(0, 0, 10, 10, 4, 1100);
        float progress = tracker.progressFor(0, 0, 10, 10, 1150);
        // Newest started at 1100, now 1150, duration 160 -> 50/160.
        assertEquals(50f / 160f, progress, 0.01f);
    }

    @Test public void pruneRemovesFinished() {
        KeyFadeTracker tracker = new KeyFadeTracker(100);
        tracker.add(0, 0, 10, 10, 4, 0);
        assertEquals(1, tracker.prune(200));
        assertTrue(tracker.isEmpty());
    }

    @Test public void capKeepsNewest() {
        KeyFadeTracker tracker = new KeyFadeTracker(1000, 2);
        tracker.add(0, 0, 1, 1, 1, 0);
        tracker.add(1, 1, 2, 2, 1, 0);
        tracker.add(2, 2, 3, 3, 1, 0);
        assertEquals(2, tracker.size());
    }
}
