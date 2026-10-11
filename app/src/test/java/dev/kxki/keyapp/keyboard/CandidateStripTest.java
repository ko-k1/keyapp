package dev.kxki.keyapp.keyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class CandidateStripTest {
    private static List<CandidateStrip.Item> items(boolean... focused) {
        java.util.List<CandidateStrip.Item> out = new java.util.ArrayList<>();
        for (int i = 0; i < focused.length; i++) {
            out.add(new CandidateStrip.Item(i, "v" + i, "", focused[i]));
        }
        return out;
    }

    @Test public void sameContentIgnoresFocus() {
        // Focus is tracked separately; content equality must not reset scroll on focus moves.
        assertTrue(CandidateStrip.sameContent(items(true, false), items(false, false)));
    }

    @Test public void differentValuesAreDifferent() {
        List<CandidateStrip.Item> a = Collections.singletonList(
                new CandidateStrip.Item(1, "火", "", false));
        List<CandidateStrip.Item> b = Collections.singletonList(
                new CandidateStrip.Item(1, "蚊", "", false));
        assertTrue(!CandidateStrip.sameContent(a, b));
    }

    @Test public void focusedIdFindsFocused() {
        assertEquals(1, CandidateStrip.focusedId(items(false, true, false)));
        assertEquals(Integer.MIN_VALUE, CandidateStrip.focusedId(items(false, false)));
    }

    @Test public void settleRevealsFocusedChip() {
        List<CandidateStrip.Item> list = items(false, false, true);
        List<Float> widths = Arrays.asList(100f, 100f, 100f);
        // Available 150px: chips 0-1 visible at scroll 0; focused chip 2 must scroll in.
        float settled = CandidateStrip.settleScroll(list, widths, 4f, 150f, 0f);
        assertTrue(settled > 0f);
        assertTrue(settled <= CandidateStrip.maxScroll(widths, 4f, 150f) + 0.001f);
    }

    @Test public void emptyStripHasNoScroll() {
        assertEquals(0f, CandidateStrip.maxScroll(Collections.emptyList(), 4f, 150f), 0f);
    }
}
