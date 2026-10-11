package dev.kxki.keyapp.keyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ResizeGeometryTest {
    @Test public void moveDragShiftsLeftAndGap() {
        ResizeGeometry.Frame start = new ResizeGeometry.Frame(360, 300, 10, 0);
        ResizeGeometry.Frame next = ResizeGeometry.applyDrag(
                ResizeGeometry.MOVE, start, 20, -10, 280, 400, 420, 200);
        assertEquals(30, next.leftDp);
        assertEquals(360, next.widthDp);
        assertEquals(10, next.bottomGapDp);
    }

    @Test public void leftDragShrinksWidthFromLeft() {
        ResizeGeometry.Frame start = new ResizeGeometry.Frame(360, 300, 0, 0);
        ResizeGeometry.Frame next = ResizeGeometry.applyDrag(
                ResizeGeometry.LEFT, start, 40, 0, 280, 400, 420, 200);
        assertEquals(40, next.leftDp);
        assertEquals(320, next.widthDp);
    }

    @Test public void detectMoveInside() {
        int edges = ResizeGeometry.detectEdges(200f, 200f, 0f, 400f, 300f, 28f);
        assertEquals(ResizeGeometry.MOVE, edges);
    }

    @Test public void detectLeftEdge() {
        int edges = ResizeGeometry.detectEdges(2f, 150f, 0f, 400f, 300f, 28f);
        assertTrue((edges & ResizeGeometry.LEFT) != 0);
    }
}
