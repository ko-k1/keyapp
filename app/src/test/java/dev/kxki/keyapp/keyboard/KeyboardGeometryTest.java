package dev.kxki.keyapp.keyboard;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class KeyboardGeometryTest {
    @Test public void clampIntsAndFloats() {
        assertEquals(0, KeyboardGeometry.clamp(-3, 0, 5));
        assertEquals(5, KeyboardGeometry.clamp(99, 0, 5));
        assertEquals(2, KeyboardGeometry.clamp(2, 0, 5));
        assertEquals(0f, KeyboardGeometry.clamp(-1f, 0f, 5f), 0f);
        assertEquals(5f, KeyboardGeometry.clamp(99f, 0f, 5f), 0f);
    }

    @Test public void dpConversionsUseDensity() {
        assertEquals(20, KeyboardGeometry.dpToPx(10f, 2f));
        assertEquals(10, KeyboardGeometry.pxToDp(20f, 2f));
    }

    @Test public void frameConstraints() {
        assertEquals(220, KeyboardGeometry.constrainHeight(10));
        assertEquals(420, KeyboardGeometry.constrainHeight(999));
        assertEquals(300, KeyboardGeometry.constrainHeight(300));
        assertEquals(280, KeyboardGeometry.constrainWidth(10, 1080));
        assertEquals(1080, KeyboardGeometry.constrainWidth(2000, 1080));
        assertEquals(0, KeyboardGeometry.constrainLeft(-5, 300, 1080));
        assertEquals(0, KeyboardGeometry.constrainBottomGap(-4, 300, 600));
    }
}
