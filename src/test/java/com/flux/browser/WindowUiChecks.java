package com.flux.browser;

import com.flux.browser.controller.BrowserController;
import com.flux.browser.db.DatabaseManager;
import com.flux.browser.ui.WindowGeometry;
import com.flux.browser.util.Views;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;

/** Exercises the actual undecorated FXML shell, including all edge gestures and maximize. */
public final class WindowUiChecks {
    private static Stage stage;
    private static Region root;
    private static BrowserController browser;
    public static void main(String[] args) throws Exception {
        System.setProperty("flux.profileDir", Files.createTempDirectory("flux-window-check-").toString());
        System.setProperty("flux.session", "false");
        Platform.startup(() -> Platform.setImplicitExit(false));
        try {
            fx(() -> {
                var view = Views.<BrowserController>load("BrowserWindow"); root = (Region)view.root(); browser = view.controller();
                stage = new Stage(); stage.initStyle(StageStyle.UNDECORATED); stage.setScene(new Scene(root));
                var screen = Screen.getPrimary().getVisualBounds(); WindowGeometry.minimum(stage, screen);
                WindowGeometry.apply(stage, WindowGeometry.initial(screen));
                browser.configure(stage, new DatabaseManager("jdbc:postgresql://127.0.0.1:1/flux_test", "flux", ""));
                stage.show(); return null;
            });
            await(() -> fx(() -> root.getWidth() > 900), "initial layout");
            for (double[] size : new double[][]{{1280, 740}, {1024, 640}, {1024, 555}, {920, 620}}) {
                var target = fx(() -> WindowGeometry.fit(new Rectangle2D(stage.getX(), stage.getY(), size[0], size[1]), WindowGeometry.screen(stage)));
                // Simulate the smaller usable height of a display with larger-text scaling.
                fx(() -> { stage.setMinHeight(Math.min(WindowGeometry.MIN_HEIGHT, size[1])); WindowGeometry.apply(stage, target); return null; });
                await(() -> fx(() -> Math.abs(root.getWidth() - target.getWidth()) < 2 && Math.abs(root.getHeight() - target.getHeight()) < 2), "window resize");
                fx(() -> { browser.home(); shell(); browser.easySetup(); shell(); inside(root.lookup("#easySetup")); browser.closeEasySetup(); browser.showDownloads(); shell(); browser.showLibrary(false); shell(); browser.settings(); shell(); return null; });
                if (size[1] == 555) fx(() -> {
                    var navigation = (javafx.scene.control.ScrollPane)root.lookup(".settings-navigation-scroll");
                    navigation.setVvalue(1); root.layout();
                    Node about = root.lookupAll(".settings-category").stream().filter(n -> "About".equals(n.getUserData())).findFirst().orElseThrow();
                    inside(about); snapshot("window-scaled-settings.png"); return null;
                });
            }
            fx(() -> { snapshot("window-small-settings.png"); browser.home(); return null; });
            fx(() -> { snapshot("window-small-home.png"); return null; });
            var restore = fx(() -> WindowGeometry.bounds(stage));
            fx(() -> { ((Button)root.lookup("#maximizeButton")).fire(); return null; });
            fx(() -> { shell(); BrowserChecks.check(WindowGeometry.screen(stage).contains(WindowGeometry.bounds(stage)), "maximized window excludes menu bar and Dock"); ((Button)root.lookup("#maximizeButton")).fire(); return null; });

            // Drag each edge and corner through the real root event filters.
            for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) {
                if (x == 0 && y == 0) continue;
                final int horizontal = x, vertical = y;
                fx(() -> { WindowGeometry.apply(stage, restore); return null; });
                await(() -> fx(() -> Math.abs(root.getWidth() - restore.getWidth()) < 2 && Math.abs(stage.getX() - restore.getMinX()) < 2), "reset gesture start");
                var expected = fx(() -> {
                    var start = WindowGeometry.bounds(stage); var screen = WindowGeometry.screen(stage);
                    double localX = horizontal < 0 ? 1 : horizontal > 0 ? root.getWidth() - 1 : root.getWidth()/2;
                    double localY = vertical < 0 ? 1 : vertical > 0 ? root.getHeight() - 1 : root.getHeight()/2;
                    double sx = stage.getX()+localX, sy = stage.getY()+localY;
                    mouse(MouseEvent.MOUSE_PRESSED, localX, localY, sx, sy, true);
                    mouse(MouseEvent.MOUSE_DRAGGED, localX, localY, sx+horizontal*10000, sy+vertical*10000, true);
                    mouse(MouseEvent.MOUSE_RELEASED, localX, localY, sx+horizontal*10000, sy+vertical*10000, false);
                    return WindowGeometry.resize(start, screen, horizontal, vertical, horizontal*10000, vertical*10000);
                });
                await(() -> fx(() -> Math.abs(stage.getWidth()-expected.getWidth())<2 && Math.abs(stage.getHeight()-expected.getHeight())<2), "edge/corner drag " + x + "," + y);
                fx(() -> { shell(); return null; });
            }
            System.out.println("WindowUiChecks passed: small windows, Home/Settings/Downloads/History/Easy Setup, maximize/restore and all eight resize directions.");
        } finally {
            fx(() -> { if (browser != null) browser.close(); if (stage != null) stage.close(); return null; }); Platform.exit();
        }
    }
    private static void mouse(javafx.event.EventType<MouseEvent> type, double x, double y, double sx, double sy, boolean down) {
        root.fireEvent(new MouseEvent(type, x, y, sx, sy, MouseButton.PRIMARY, 1, false, false, false, false, down, false, false, false, false, false, null));
    }
    private static void shell() {
        root.applyCss(); root.layout();
        for (String selector : new String[]{".window-rail", ".traffic-close", "#maximizeButton", "#addressBar", "#easySetupButton", "#contentHost"}) inside(root.lookup(selector));
        var rail = root.lookup(".window-rail"); BrowserChecks.check(Math.abs(rail.getLayoutBounds().getWidth()-46)<1, "sidebar width preserved");
        for (Node button : root.lookupAll(".sidebar-button")) BrowserChecks.check(Math.abs(button.getLayoutBounds().getWidth()-32)<1, "sidebar buttons remain full size");
    }
    private static void inside(Node node) {
        BrowserChecks.check(node != null && node.isVisible(), "control visible");
        var b = node.localToScene(node.getLayoutBounds());
        BrowserChecks.check(b.getMinX()>=-1 && b.getMinY()>=-1 && b.getMaxX()<=root.getWidth()+1 && b.getMaxY()<=root.getHeight()+1, "control fits window: " + node.getId() + " " + b);
    }
    private static void snapshot(String name) throws Exception {
        root.applyCss(); root.layout(); var image = root.snapshot(null, null);
        int w=(int)image.getWidth(), h=(int)image.getHeight(); int[] pixels = new int[w*h];
        image.getPixelReader().getPixels(0,0,w,h,javafx.scene.image.PixelFormat.getIntArgbInstance(),pixels,0,w);
        var output = new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_ARGB); output.setRGB(0,0,w,h,pixels,0,w);
        Files.createDirectories(Path.of("target/screenshots")); javax.imageio.ImageIO.write(output,"png",Path.of("target/screenshots",name).toFile());
    }
    private static <T>T fx(Callable<T> action) throws Exception {
        var task = new FutureTask<T>(() -> { if (root!=null) {root.applyCss();root.layout();} return action.call(); });
        Platform.runLater(task); return task.get(20,TimeUnit.SECONDS);
    }
    private static void await(Callable<Boolean> condition, String name) throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
        while(System.nanoTime()<until) { if(condition.call()) return; Thread.sleep(100); }
        throw new AssertionError("Timed out: " + name);
    }
}
