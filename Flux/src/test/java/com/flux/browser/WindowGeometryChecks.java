package com.flux.browser;

import com.flux.browser.ui.WindowGeometry;
import javafx.geometry.Rectangle2D;

final class WindowGeometryChecks {
    static void run() {
        // Common logical display sizes, a larger-text setting, and a monitor left of primary.
        for (var screen : new Rectangle2D[]{new Rectangle2D(0, 25, 1280, 720),
                new Rectangle2D(0, 25, 1440, 810), new Rectangle2D(0, 25, 1024, 555),
                new Rectangle2D(-1280, -300, 1280, 720)}) {
            var initial = WindowGeometry.initial(screen);
            BrowserChecks.check(screen.contains(initial), "startup stays inside usable screen: " + screen);
            for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) {
                for (double delta : new double[]{-5000, 5000}) {
                    var resized = WindowGeometry.resize(initial, screen, x, y, delta, delta);
                    BrowserChecks.check(screen.contains(resized), "resize cannot hide controls beyond screen");
                    BrowserChecks.check(resized.getWidth() >= Math.min(WindowGeometry.MIN_WIDTH, screen.getWidth()), "minimum width");
                    BrowserChecks.check(resized.getHeight() >= Math.min(WindowGeometry.MIN_HEIGHT, screen.getHeight()), "minimum height adapts to display");
                    if (x < 0) BrowserChecks.equal(resized.getMaxX(), initial.getMaxX());
                    if (y < 0) BrowserChecks.equal(resized.getMaxY(), initial.getMaxY());
                }
            }
            BrowserChecks.check(screen.contains(WindowGeometry.fit(new Rectangle2D(-2000, -1000, 3000, 2000), screen)), "fit a window moved from a larger monitor");
        }
        System.out.println("WindowGeometryChecks passed: small/scaled displays, all edges, minimum sizes and offscreen bounds.");
    }
}
