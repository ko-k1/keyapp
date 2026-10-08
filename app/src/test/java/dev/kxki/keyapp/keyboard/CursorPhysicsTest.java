package dev.kxki.keyapp.keyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class CursorPhysicsTest {
    @Test public void stepsQuantizeDrag() {
        assertEquals(0, CursorPhysics.steps(5f, 12f));
        assertEquals(1, CursorPhysics.steps(13f, 12f));
        assertEquals(-2, CursorPhysics.steps(-25f, 12f));
    }

    @Test public void stillSwipeKeepsStepCount() {
        assertEquals(2, CursorPhysics.accelerate(2, 0f, 1200f));
        assertEquals(-1, CursorPhysics.accelerate(-1, 10f, 1200f));
        assertEquals(0, CursorPhysics.accelerate(0, 5000f, 1200f));
    }

    @Test public void fastSwipeAcceleratesSmoothly() {
        int slow = CursorPhysics.accelerate(2, 100f, 1200f);
        int fast = CursorPhysics.accelerate(2, 1200f, 1200f);
        assertEquals(2, slow);
        assertEquals(6, fast);
        int mid = CursorPhysics.accelerate(2, 600f, 1200f);
        assertTrue(mid > slow && mid < fast);
        assertEquals(-6, CursorPhysics.accelerate(-2, 1200f, 1200f));
    }
}
