package dev.kxki.keyapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public final class ClipboardLogicTest {
    @Test public void blocksCaptureInPrivate() {
        assertTrue(ClipboardLogic.shouldBlockCapture(true, false));
        assertTrue(ClipboardLogic.shouldBlockCapture(false, true));
        assertTrue(ClipboardLogic.shouldBlockCapture(true, true));
        assertFalse(ClipboardLogic.shouldBlockCapture(false, false));
    }

    @Test public void rejectsEmptyClipsButKeepsOriginal() {
        assertNull(ClipboardLogic.normalizeText(null));
        assertNull(ClipboardLogic.normalizeText(""));
        assertNull(ClipboardLogic.normalizeText("   \n\t "));
        assertEquals("  hi  ", ClipboardLogic.normalizeText("  hi  "));
    }

    @Test public void mergeDedupsAndReportsChange() {
        List<String> history = new ArrayList<>(Arrays.asList("b", "a"));
        assertTrue(ClipboardLogic.mergeItem(history, "c"));
        assertEquals(Arrays.asList("c", "b", "a"), history);

        // Already on top: no change.
        assertFalse(ClipboardLogic.mergeItem(history, "c"));

        // Duplicate deeper moves to top.
        assertTrue(ClipboardLogic.mergeItem(history, "a"));
        assertEquals(Arrays.asList("a", "c", "b"), history);
    }

    @Test public void scrollClampsToVisibleRows() {
        assertEquals(0, ClipboardLogic.clampScroll(0, 0));
        assertEquals(0, ClipboardLogic.clampScroll(5, 2));
        assertEquals(2, ClipboardLogic.clampScroll(2, 5));
        assertEquals(2, ClipboardLogic.clampScroll(99, 5));
        assertEquals(0, ClipboardLogic.clampScroll(-3, 5));
    }

    @Test public void headChangeDetectsFreshClip() {
        assertTrue(ClipboardLogic.headChanged(new ArrayList<>(), new ArrayList<>(Arrays.asList("a"))));
        assertFalse(ClipboardLogic.headChanged(
                new ArrayList<>(Arrays.asList("a", "b")),
                new ArrayList<>(Arrays.asList("a", "c"))));
        assertTrue(ClipboardLogic.headChanged(
                new ArrayList<>(Arrays.asList("a")),
                new ArrayList<>(Arrays.asList("b", "a"))));
        assertTrue(ClipboardLogic.headChanged(
                new ArrayList<>(Arrays.asList("a")), new ArrayList<>()));
    }

    @Test public void historyIsCapped() {
        List<String> history = new ArrayList<>();
        for (int i = 0; i < ClipboardLogic.MAX_HISTORY; i++) history.add("item-" + i);
        assertTrue(ClipboardLogic.mergeItem(history, "fresh"));
        assertEquals(ClipboardLogic.MAX_HISTORY, history.size());
        assertEquals("fresh", history.get(0));
        assertEquals("item-0", history.get(1));
    }

    @Test public void labelCollapsesWhitespaceAndTruncates() {
        assertEquals("a b", ClipboardLogic.clipboardLabel("a\nb"));
        assertEquals("hi", ClipboardLogic.clipboardLabel("  hi  "));
        assertEquals("short", ClipboardLogic.clipboardLabel("short"));
        String longText = "0123456789abcdefghij0123456789";
        assertEquals(30, longText.length());
        assertEquals(longText.substring(0, 24) + "…", ClipboardLogic.clipboardLabel(longText));
    }

    @Test public void snapshotWinsOverStaleIndex() {
        List<String> history = new ArrayList<>(Arrays.asList("new", "old"));
        // User pressed "old" at index 1, then "new" arrived shifting indices.
        // Snapshot must still paste "old", not "new".
        assertEquals("old", ClipboardLogic.resolvePaste("#clipboard:1", history, "old"));
        assertEquals("old", ClipboardLogic.resolvePaste("#clipboard:0", history, "old"));
        // No snapshot: normal index lookup.
        assertEquals("new", ClipboardLogic.resolvePaste("#clipboard:0", history, null));
        assertNull(ClipboardLogic.resolvePaste("#clipboard:9", history, null));
        assertNull(ClipboardLogic.resolvePaste("#clipboard:x", history, null));
        assertNull(ClipboardLogic.resolvePaste("#other:0", history, null));
    }

    @Test public void staleSnapshotCancelsInsteadOfResurrecting() {
        // DOWN on "old", then Clear before UP: must not paste stale text.
        assertNull(ClipboardLogic.resolvePaste(
                "#clipboard:0", new ArrayList<>(), "old"));
        // Snapshot evicted and a different entry now sits at the old index:
        // must not redirect to "new".
        assertNull(ClipboardLogic.resolvePaste(
                "#clipboard:0", new ArrayList<>(Arrays.asList("new")), "old"));
        assertNull(ClipboardLogic.resolvePaste("#clipboard:0", null, "old"));
    }
}
