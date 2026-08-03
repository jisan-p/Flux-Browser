package org.custombrowser.ui.window;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import javafx.scene.Cursor;

class WindowResizeSupportTest {

    @Test
    void returnsDefaultAwayFromEdges() {
        assertEquals(
                Cursor.DEFAULT,
                WindowResizeSupport.cursorFor(false, false, false, false));
    }

    @Test
    void resolvesCardinalEdges() {
        assertEquals(
                Cursor.W_RESIZE,
                WindowResizeSupport.cursorFor(true, false, false, false));
        assertEquals(
                Cursor.E_RESIZE,
                WindowResizeSupport.cursorFor(false, true, false, false));
        assertEquals(
                Cursor.N_RESIZE,
                WindowResizeSupport.cursorFor(false, false, true, false));
        assertEquals(
                Cursor.S_RESIZE,
                WindowResizeSupport.cursorFor(false, false, false, true));
    }

    @Test
    void resolvesCornerEdges() {
        assertEquals(
                Cursor.NW_RESIZE,
                WindowResizeSupport.cursorFor(true, false, true, false));
        assertEquals(
                Cursor.NE_RESIZE,
                WindowResizeSupport.cursorFor(false, true, true, false));
        assertEquals(
                Cursor.SW_RESIZE,
                WindowResizeSupport.cursorFor(true, false, false, true));
        assertEquals(
                Cursor.SE_RESIZE,
                WindowResizeSupport.cursorFor(false, true, false, true));
    }
}
