package com.flux.browser.ui;

import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;

/** Window sizes are in logical screen points, excluding the menu bar and Dock. */
public final class WindowGeometry {
    public static final double MIN_WIDTH = 920, MIN_HEIGHT = 620;
    private WindowGeometry() { }

    public static Rectangle2D bounds(Stage stage) {
        return new Rectangle2D(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
    }

    public static Rectangle2D screen(Stage stage) {
        Rectangle2D window = bounds(stage);
        return Screen.getScreens().stream().max(java.util.Comparator.comparingDouble(s -> {
            Rectangle2D b = s.getVisualBounds();
            return Math.max(0, Math.min(window.getMaxX(), b.getMaxX()) - Math.max(window.getMinX(), b.getMinX()))
                * Math.max(0, Math.min(window.getMaxY(), b.getMaxY()) - Math.max(window.getMinY(), b.getMinY()));
        })).orElse(Screen.getPrimary()).getVisualBounds();
    }

    public static Rectangle2D initial(Rectangle2D screen) {
        double w = Math.min(screen.getWidth(), Math.max(MIN_WIDTH, Math.min(1320, screen.getWidth() - 60)));
        double h = Math.min(screen.getHeight(), Math.max(MIN_HEIGHT, Math.min(860, screen.getHeight() - 60)));
        return new Rectangle2D(screen.getMinX() + (screen.getWidth() - w) / 2,
            screen.getMinY() + (screen.getHeight() - h) / 2, w, h);
    }

    public static void minimum(Stage stage, Rectangle2D screen) {
        stage.setMinWidth(Math.min(MIN_WIDTH, screen.getWidth()));
        stage.setMinHeight(Math.min(MIN_HEIGHT, screen.getHeight()));
    }

    public static Rectangle2D fit(Rectangle2D window, Rectangle2D screen) {
        double w = Math.min(window.getWidth(), screen.getWidth()), h = Math.min(window.getHeight(), screen.getHeight());
        return new Rectangle2D(clamp(window.getMinX(), screen.getMinX(), screen.getMaxX() - w),
            clamp(window.getMinY(), screen.getMinY(), screen.getMaxY() - h), w, h);
    }

    public static void apply(Stage stage, Rectangle2D bounds) {
        stage.setX(bounds.getMinX()); stage.setY(bounds.getMinY());
        stage.setWidth(bounds.getWidth()); stage.setHeight(bounds.getHeight());
    }

    public static Rectangle2D resize(Rectangle2D start, Rectangle2D screen, int horizontal, int vertical, double dx, double dy) {
        double left = start.getMinX(), top = start.getMinY(), right = start.getMaxX(), bottom = start.getMaxY();
        double minW = Math.min(MIN_WIDTH, screen.getWidth()), minH = Math.min(MIN_HEIGHT, screen.getHeight());
        if (horizontal < 0) left = clamp(left + dx, screen.getMinX(), right - minW);
        if (horizontal > 0) right = clamp(right + dx, left + minW, screen.getMaxX());
        if (vertical < 0) top = clamp(top + dy, screen.getMinY(), bottom - minH);
        if (vertical > 0) bottom = clamp(bottom + dy, top + minH, screen.getMaxY());
        return new Rectangle2D(left, top, right - left, bottom - top);
    }

    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
}
