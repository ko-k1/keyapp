package dev.kxki.keyapp.kana;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class KanaTableTest {
    @Test public void transformCyclesSmallKana() {
        assertEquals("ば", KanaTable.transformLast("は"));
        assertEquals("ぱ", KanaTable.transformLast("ば"));
        assertEquals("は", KanaTable.transformLast("ぱ"));
        assertEquals("っ", KanaTable.transformLast("つ"));
        assertEquals("づ", KanaTable.transformLast("っ"));
        assertEquals("ゃ", KanaTable.transformLast("や"));
        assertEquals("や", KanaTable.transformLast("ゃ"));
        assertEquals("ん", KanaTable.transformLast("ん"));
    }

    @Test public void insertAndRemoveRespectCodePoints() {
        assertEquals("かさな", KanaTable.insertCodePoints("かな", 1, "さ"));
        assertEquals("さかな", KanaTable.insertCodePoints("かな", 0, "さ"));
        assertEquals("かな", KanaTable.removeCodePointBefore("かさな", 2));
        assertEquals("かな", KanaTable.insertCodePoints("かな", 99, ""));
    }

    @Test public void transformBeforeCursorOnlyEditsLeftSide() {
        assertEquals("ゕな", KanaTable.transformBeforeCursor("かな", 1));
        assertEquals("かざな", KanaTable.transformBeforeCursor("かさな", 2));
    }

    @Test public void codePointLengthAndClamp() {
        assertEquals(2, KanaTable.codePointLength("かな"));
        assertEquals(0, KanaTable.clamp(-5, 0, 2));
        assertEquals(2, KanaTable.clamp(99, 0, 2));
        assertEquals(1, KanaTable.clamp(1, 0, 2));
    }
}
