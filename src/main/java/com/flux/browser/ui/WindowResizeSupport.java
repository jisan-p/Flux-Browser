package com.flux.browser.ui;

import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

/** Edge gestures for the FXML shell's undecorated window; no polling or native event hook. */
public final class WindowResizeSupport {
    private final Stage stage;
    private final Parent root;
    private Rectangle2D start, screen;
    private int horizontal, vertical;
    private double pressX, pressY;

    public WindowResizeSupport(Stage stage, Parent root) {
        this.stage = stage; this.root = root;
        root.addEventFilter(MouseEvent.MOUSE_MOVED, e -> {
            int x = horizontal(e), y = vertical(e);
            root.setCursor(stage.isMaximized() || stage.isFullScreen() ? Cursor.DEFAULT : cursor(x, y));
        });
        root.addEventFilter(MouseEvent.MOUSE_EXITED, e -> { if (e.getTarget() == root && start == null) root.setCursor(Cursor.DEFAULT); });
        root.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> begin(e, horizontal(e), vertical(e)));
        root.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::drag);
        root.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (start != null) { start = null; root.setCursor(Cursor.DEFAULT); e.consume(); }
        });
    }

    // The outer gutter stays outside the native WebKit container, so all edges receive events.
    private int horizontal(MouseEvent e) { return e.getSceneX() < 4 ? -1 : e.getSceneX() >= root.getLayoutBounds().getWidth() - 4 ? 1 : 0; }
    private int vertical(MouseEvent e) { return e.getSceneY() < 4 ? -1 : e.getSceneY() >= root.getLayoutBounds().getHeight() - 4 ? 1 : 0; }

    public void beginCorner(MouseEvent event) { begin(event, 1, 1); }
    private void begin(MouseEvent e, int x, int y) {
        if (e.getButton() != MouseButton.PRIMARY || stage.isMaximized() || stage.isFullScreen() || (x == 0 && y == 0)) return;
        screen = WindowGeometry.screen(stage);
        WindowGeometry.minimum(stage, screen);
        start = WindowGeometry.fit(WindowGeometry.bounds(stage), screen);
        WindowGeometry.apply(stage, start);
        horizontal = x; vertical = y; pressX = e.getScreenX(); pressY = e.getScreenY();
        root.setCursor(cursor(x, y)); e.consume();
    }

    public void drag(MouseEvent e) {
        if (start == null || !e.isPrimaryButtonDown()) return;
        WindowGeometry.apply(stage, WindowGeometry.resize(start, screen, horizontal, vertical,
            e.getScreenX() - pressX, e.getScreenY() - pressY));
        e.consume();
    }

    private static Cursor cursor(int x, int y) {
        if (x < 0) return y < 0 ? Cursor.NW_RESIZE : y > 0 ? Cursor.SW_RESIZE : Cursor.W_RESIZE;
        if (x > 0) return y < 0 ? Cursor.NE_RESIZE : y > 0 ? Cursor.SE_RESIZE : Cursor.E_RESIZE;
        return y < 0 ? Cursor.N_RESIZE : y > 0 ? Cursor.S_RESIZE : Cursor.DEFAULT;
    }
}
