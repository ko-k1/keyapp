package dev.kxki.keyapp.keyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public final class DeleteRangeTest {
    @Test public void backspaceFlickMapsDeletionRanges() {
        assertEquals(DeleteRange.TO_END, DeleteRange.fromBackspaceFlick(0, -30, 20));
        assertEquals(DeleteRange.TO_START, DeleteRange.fromBackspaceFlick(0, 30, 20));
        assertEquals(DeleteRange.ALL, DeleteRange.fromBackspaceFlick(-30, 0, 20));
        assertNull(DeleteRange.fromBackspaceFlick(30, 0, 20));
        assertNull(DeleteRange.fromBackspaceFlick(0, 0, 20));
    }
}
