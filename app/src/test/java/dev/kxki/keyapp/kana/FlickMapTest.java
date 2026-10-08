package dev.kxki.keyapp.kana;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public final class FlickMapTest {
    @Test public void rowLookupByBase() {
        assertEquals("う", FlickMap.flickRow("あ")[2]);
        assertEquals("こ", FlickMap.flickRow("か")[4]);
        assertNull(FlickMap.flickRow("q"));
        assertNull(FlickMap.flickRow(""));
    }

    @Test public void indexUsesThreshold() {
        assertEquals(0, FlickMap.flickIndex(0, 0, 20));
        assertEquals(0, FlickMap.flickIndex(10, 5, 20));
        assertEquals(3, FlickMap.flickIndex(30, 0, 20));
        assertEquals(1, FlickMap.flickIndex(-30, 0, 20));
        assertEquals(2, FlickMap.flickIndex(0, -30, 20));
        assertEquals(4, FlickMap.flickIndex(0, 30, 20));
    }

    @Test public void resolveFallsBackToBase() {
        assertEquals("い", FlickMap.resolveFlick("あ", -30, 0, 20));
        assertEquals("お", FlickMap.resolveFlick("あ", 0, 30, 20));
        assertEquals("q", FlickMap.resolveFlick("q", 30, 0, 20));
    }
}
