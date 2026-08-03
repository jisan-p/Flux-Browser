package org.custombrowser.ui.window;

import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

/**
 * Cross-platform edge resizing for an undecorated JavaFX stage.
 */
public final class WindowResizeSupport {

    private static final double RESIZE_MARGIN = 6.0;

    private Cursor resizeCursor = Cursor.DEFAULT;
    private double startScreenX;
    private double startScreenY;
    private double startX;
    private double startY;
    private double startWidth;
    private double startHeight;

    private final Scene scene;
    private final Stage stage;

    private WindowResizeSupport(Scene scene, Stage stage) {
        this.scene = scene;
        this.stage = stage;
    }

    public static void install(Scene scene, Stage stage) {
        WindowResizeSupport support = new WindowResizeSupport(scene, stage);
        scene.addEventFilter(MouseEvent.MOUSE_MOVED, support::updateCursor);
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, support::beginResize);
        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, support::resize);
    }

    private void updateCursor(MouseEvent event) {
        if (stage.isMaximized() || stage.isFullScreen()) {
            resizeCursor = Cursor.DEFAULT;
            scene.setCursor(Cursor.DEFAULT);
            return;
        }

        boolean left = event.getSceneX() <= RESIZE_MARGIN;
        boolean right = event.getSceneX() >= scene.getWidth() - RESIZE_MARGIN;
        boolean top = event.getSceneY() <= RESIZE_MARGIN;
        boolean bottom = event.getSceneY() >= scene.getHeight() - RESIZE_MARGIN;

        resizeCursor = cursorFor(left, right, top, bottom);
        scene.setCursor(resizeCursor);
    }

    private void beginResize(MouseEvent event) {
        if (resizeCursor == Cursor.DEFAULT) {
            return;
        }
        startScreenX = event.getScreenX();
        startScreenY = event.getScreenY();
        startX = stage.getX();
        startY = stage.getY();
        startWidth = stage.getWidth();
        startHeight = stage.getHeight();
        event.consume();
    }

    private void resize(MouseEvent event) {
        if (resizeCursor == Cursor.DEFAULT
                || stage.isMaximized()
                || stage.isFullScreen()) {
            return;
        }

        double deltaX = event.getScreenX() - startScreenX;
        double deltaY = event.getScreenY() - startScreenY;

        if (isEast(resizeCursor)) {
            stage.setWidth(Math.max(stage.getMinWidth(), startWidth + deltaX));
        }
        if (isSouth(resizeCursor)) {
            stage.setHeight(Math.max(stage.getMinHeight(), startHeight + deltaY));
        }
        if (isWest(resizeCursor)) {
            double width = Math.max(stage.getMinWidth(), startWidth - deltaX);
            stage.setX(startX + startWidth - width);
            stage.setWidth(width);
        }
        if (isNorth(resizeCursor)) {
            double height = Math.max(stage.getMinHeight(), startHeight - deltaY);
            stage.setY(startY + startHeight - height);
            stage.setHeight(height);
        }
        event.consume();
    }

    static Cursor cursorFor(
            boolean left,
            boolean right,
            boolean top,
            boolean bottom) {
        if (left && top) {
            return Cursor.NW_RESIZE;
        }
        if (right && top) {
            return Cursor.NE_RESIZE;
        }
        if (left && bottom) {
            return Cursor.SW_RESIZE;
        }
        if (right && bottom) {
            return Cursor.SE_RESIZE;
        }
        if (left) {
            return Cursor.W_RESIZE;
        }
        if (right) {
            return Cursor.E_RESIZE;
        }
        if (top) {
            return Cursor.N_RESIZE;
        }
        if (bottom) {
            return Cursor.S_RESIZE;
        }
        return Cursor.DEFAULT;
    }

    private static boolean isEast(Cursor cursor) {
        return cursor == Cursor.E_RESIZE
                || cursor == Cursor.NE_RESIZE
                || cursor == Cursor.SE_RESIZE;
    }

    private static boolean isWest(Cursor cursor) {
        return cursor == Cursor.W_RESIZE
                || cursor == Cursor.NW_RESIZE
                || cursor == Cursor.SW_RESIZE;
    }

    private static boolean isNorth(Cursor cursor) {
        return cursor == Cursor.N_RESIZE
                || cursor == Cursor.NE_RESIZE
                || cursor == Cursor.NW_RESIZE;
    }

    private static boolean isSouth(Cursor cursor) {
        return cursor == Cursor.S_RESIZE
                || cursor == Cursor.SE_RESIZE
                || cursor == Cursor.SW_RESIZE;
    }
}
