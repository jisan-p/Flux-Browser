package com.flux.browser;

import com.flux.browser.util.Views;
import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.PixelFormat;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import com.flux.browser.web.BrowserPage;
import com.flux.browser.web.NativeWebPage;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Real FXML, real WebKit, local HTTP pages, and an explicitly disposable PostgreSQL database. */
public final class BrowserSmokeChecks {
    private static Stage stage;
    private static FluxBrowser app;
    private static final AtomicReference<Throwable> uncaught = new AtomicReference<>();

    public static void main(String[] args) throws Exception {
        String url = System.getenv("FLUX_DB_URL");
        if (url == null || !url.matches("jdbc:postgresql://[^/]+/flux_test(?:\\?.*)?")) {
            throw new IllegalArgumentException("Set FLUX_DB_URL to a disposable database named flux_test before running ui-check.");
        }
        boolean expectStorage = !"false".equals(System.getenv("FLUX_EXPECT_STORAGE"));
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> { error.printStackTrace(); uncaught.set(error); });
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService httpThreads = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(httpThreads);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/slow")) {
                try { Thread.sleep(5000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }
            String title = switch (path) {
                case "/one" -> "Page One"; case "/two" -> "Page Two"; case "/popup" -> "Popup Page";
                case "/dial" -> "Dial Page"; default -> "Slow Page";
            };
            byte[] body = ("<!doctype html><html><head><title>" + title + "</title></head>"
                    + "<body style='font:20px system-ui;background:#eef3fa;padding:60px'><h1>" + title + "</h1>"
                    + "<p>Local WebKit verification page.</p><a id='next' href='/two'>Next page</a>"
                    + "<p><button onclick=\"window.open('/popup','_blank')\">Open popup</button></p></body></html>").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            try {
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } finally { exchange.close(); }
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        String bookmarkTitle = "UI Bookmark " + server.getAddress().getPort();
        String dialTitle = "UI Dial " + server.getAddress().getPort();
        Platform.startup(() -> Platform.setImplicitExit(false));
        try {
            fx(() -> {
                // These additional views ensure every standalone Scene Builder component loads.
                for (String name : new String[]{"EntryDialog", "MessageDialog", "LibraryRow", "DialTile", "TabHeader"}) Views.load(name);
                app = new FluxBrowser(); stage = new Stage(); app.start(stage);
                stage.setWidth(1280); stage.setHeight(820); stage.centerOnScreen();
                return null;
            });
            await("storage initialization", () -> !label("databaseStatus").getText().contains("CONNECTING"));
            BrowserChecks.equal(fx(() -> label("databaseStatus").getText().contains("CONNECTED")), expectStorage);
            await("starter dials", () -> root().lookupAll(".dial-tile").size() >= 6);
            BrowserChecks.check(fx(() -> web() == null), "Fresh home does not allocate WebKit");
            Node firstTile = fx(() -> activeContent().lookup(".dial-tile"));
            fire("reloadButton");
            Thread.sleep(200);
            BrowserChecks.check(fx(() -> firstTile == activeContent().lookup(".dial-tile")), "Unchanged Speed Dial reuses its tiles");
            screenshot("01-speed-dial");

            navigate(base + "/one");
            loaded("Page One");
            BrowserChecks.check(fx(() -> stage.getTitle().contains("Page One")), "tab/window title resolves");
            script("document.getElementById('next').click(); true");
            loaded("Page Two");
            fire("backButton"); loaded("Page One");
            fire("forwardButton"); loaded("Page Two");
            fire("reloadButton"); loaded("Page Two");
            fire("homeButton");
            BrowserChecks.check(fx(() -> !button("backButton").isDisabled()), "Back from Home is available");
            fire("backButton"); loaded("Page Two");
            screenshot("02-web-page");
            if (fx(() -> web() instanceof NativeWebPage)) {
                BrowserChecks.equal(script("typeof MediaSource !== 'undefined'"), "true");
                // App-local Cocoa event, avoiding OS-wide key injection/Accessibility permissions.
                fx(() -> { web().focus(); return null; });
                Thread.sleep(100);
                fx(() -> { ((NativeWebPage) web()).postShortcutForTesting("l"); return null; });
                await("native Cmd+L focuses omnibox", () -> text("addressBar").isFocused());
            }

            if (expectStorage) {
                await("bookmark action enabled", () -> !button("bookmarkButton").isDisabled());
                fire("bookmarkButton");
                await("bookmark saved", () -> button("bookmarkButton").getText().equals("★"));
                await("bookmark removal enabled", () -> !button("bookmarkButton").isDisabled());
                fire("bookmarkButton");
                await("bookmark toggled off", () -> button("bookmarkButton").getText().equals("☆") && !button("bookmarkButton").isDisabled());
                fire("bookmarkButton");
                await("bookmark toggled on", () -> button("bookmarkButton").getText().equals("★"));
                fire("bookmarksButton");
                await("saved bookmark in library", () -> entries().getItems().size() >= 1);
                screenshot("03-bookmarks");
                fire("addButton");
                fillEditor(bookmarkTitle, base + "/saved", "Demo");
                filter(bookmarkTitle);
                await("new bookmark", () -> entries().getItems().size() == 1);
                fx(() -> { ((Button) entries().lookup("#editButton")).fire(); return null; });
                fillEditor("Updated " + bookmarkTitle, base + "/saved-edit", "Presentation");
                await("bookmark edited", () -> entries().getItems().toString().contains("Updated " + bookmarkTitle));
                fx(() -> { ((Button) entries().lookup("#deleteButton")).fire(); return null; });
                await("bookmark deleted", () -> entries().getItems().isEmpty());

                fire("historyButton");
                await("chronological history", () -> entries().getItems().size() >= 4);
                screenshot("04-history");
                Platform.runLater(() -> button("clearButton").fire());
                await("history confirmation", () -> dialog() != null);
                fx(() -> { ((Button) dialog().getScene().lookup("#acceptButton")).fire(); return null; });
                await("history cleared", () -> dialog() == null && entries().getItems().isEmpty());

                fire("homeButton");
                fire("addDialButton");
                fillEditor(dialTitle, base + "/dial", null);
                await("dial created", () -> dial(dialTitle) != null);
                fx(() -> { ((Button) dial(dialTitle).lookup("#openButton")).fire(); return null; });
                loaded("Dial Page");
                fire("homeButton");
                await("saved dial reloaded", () -> dial(dialTitle) != null);
                fx(() -> { ((Button) dial(dialTitle).lookup("#editButton")).fire(); return null; });
                fillEditor("Edited " + dialTitle, base + "/dial", null);
                await("dial edited", () -> dial("Edited " + dialTitle) != null);
                fx(() -> { ((Button) dial("Edited " + dialTitle).lookup("#deleteButton")).fire(); return null; });
                await("dial removed", () -> dial("Edited " + dialTitle) == null);
            }

            shortcut(KeyCode.T);
            BrowserChecks.equal(fx(BrowserSmokeChecks::tabCount), 2);
            BrowserChecks.check(fx(() -> web() == null), "New blank tab does not allocate WebKit");
            navigate(base + "/one"); loaded("Page One");
            script("window.open('/popup','_blank'); true");
            loaded("Popup Page");
            BrowserChecks.equal(fx(BrowserSmokeChecks::tabCount), 3);
            BrowserChecks.equal(script("!!window.opener"), "true");
            script("window.close(); true"); loaded("Page One");
            shortcut(KeyCode.DIGIT1);
            BrowserChecks.check(fx(() -> text("addressBar").getText().equals("flux://start") || web().location.get().endsWith("/two")), "tab switch keeps independent page state");
            shortcut(KeyCode.DIGIT2); loaded("Page One");

            fire("settingsButton");
            fire("cyanButton");
            BrowserChecks.check(fx(() -> root().getStyleClass().contains("cyan-theme")), "accent toggle");
            fx(() -> { ((Slider) root().lookup("#zoomSlider")).setValue(125); return null; });
            BrowserChecks.equal(fx(() -> web().zoomLevel.get()), 1.25);
            screenshot("05-settings-cyan");
            fire("magentaButton");
            fire("homeButton");
            fx(() -> { stage.setWidth(940); stage.setHeight(650); return null; });
            screenshot("06-compact-window");
            fx(() -> { stage.setWidth(1280); stage.setHeight(820); return null; });

            navigate(base + "/slow");
            await("slow page starts", () -> !button("stopButton").isDisabled());
            fire("stopButton");
            await("cancelled navigation", () -> web().state.get() == Worker.State.CANCELLED);
            int closedPort;
            try (var socket = new java.net.ServerSocket(0)) { closedPort = socket.getLocalPort(); }
            navigate("http://127.0.0.1:" + closedPort + "/unreachable");
            await("failed page recovery", () -> activeContent().lookup("#errorPane").isVisible());
            screenshot("07-load-error");
            fire("homeButton");
            fire("backButton");
            await("error view retained across Home", () -> activeContent().lookup("#errorPane").isVisible());
            fire("homeButton");
            if (expectStorage) {
                BrowserChecks.equal(visitCount(base + "/slow"), 0);
                BrowserChecks.equal(visitCount("http://127.0.0.1:" + closedPort + "/unreachable"), 0);
            }

            if ("true".equals(System.getenv("FLUX_CHECK_WEB"))) {
                navigate("https://example.org/"); loaded("Example Domain");
                screenshot("08-live-website");
            }
            shortcut(KeyCode.W); shortcut(KeyCode.W);
            BrowserChecks.equal(fx(BrowserSmokeChecks::tabCount), 1);
            BrowserChecks.equal(fx(() -> text("addressBar").getText()), "");
            BrowserChecks.check(uncaught.get() == null, "No uncaught JavaFX exceptions");
            System.out.println("BrowserSmokeChecks passed: FXML, WebKit navigation, tabs, popups, stop/errors, settings"
                    + (expectStorage ? ", bookmark/history/dial UI CRUD" : ", offline browsing") + ". Screenshots: target/screenshots/");
        } finally {
            fx(() -> { if (app != null) app.stop(); if (stage != null) stage.close(); return null; });
            Platform.exit(); server.stop(0); httpThreads.shutdownNow();
        }
    }

    private static Parent root() { return stage.getScene().getRoot(); }
    private static Button button(String id) {
        Node inTab = activeContent().lookup("#" + id);
        return (Button) (inTab == null ? root().lookup("#" + id) : inTab);
    }
    private static Label label(String id) { return (Label) root().lookup("#" + id); }
    private static TextField text(String id) { return (TextField) root().lookup("#" + id); }
    private static ListView<?> entries() { return (ListView<?>) root().lookup("#entries"); }
    private static Parent activeContent() {
        return root().lookupAll(".web-tab").stream().filter(Node::isVisible).map(node -> (Parent) node).findFirst().orElseThrow();
    }
    private static BrowserPage web() { return (BrowserPage) activeContent().getProperties().get("browserPage"); }
    private static String script(String js) throws Exception { return fx(() -> web().evaluate(js)).get(15, TimeUnit.SECONDS); }
    private static int tabCount() { return ((HBox) root().lookup("#tabHeaders")).getChildren().size(); }
    private static Stage dialog() {
        return Window.getWindows().stream().filter(window -> window instanceof Stage && window != stage && window.isShowing())
                .map(window -> (Stage) window).findFirst().orElse(null);
    }
    private static Parent dial(String name) {
        return activeContent().lookupAll(".dial-tile").stream().filter(node -> node.lookup("#titleLabel") instanceof Label label && label.getText().equals(name))
                .map(node -> (Parent) node).findFirst().orElse(null);
    }
    private static void fire(String id) throws Exception { fx(() -> { button(id).fire(); return null; }); }
    private static void filter(String value) throws Exception { fx(() -> { text("filterField").setText(value); return null; }); }
    private static void navigate(String url) throws Exception {
        fx(() -> { text("addressBar").setText(url); text("addressBar").fireEvent(new ActionEvent()); return null; });
    }
    private static void loaded(String title) throws Exception {
        await("load " + title, () -> web().state.get() == Worker.State.SUCCEEDED && title.equals(web().title.get()));
    }
    private static int visitCount(String url) throws Exception {
        try (var connection = java.sql.DriverManager.getConnection(System.getenv("FLUX_DB_URL"),
                System.getenv().getOrDefault("FLUX_DB_USER", "flux"), System.getenv().getOrDefault("FLUX_DB_PASSWORD", ""));
             var statement = connection.prepareStatement("SELECT count(*) FROM history WHERE url = ?")) {
            statement.setString(1, url);
            try (var result = statement.executeQuery()) { result.next(); return result.getInt(1); }
        }
    }
    private static void shortcut(KeyCode code) throws Exception {
        boolean mac = System.getProperty("os.name").startsWith("Mac");
        fx(() -> { root().fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, !mac, false, mac)); return null; });
    }
    private static void fillEditor(String title, String url, String category) throws Exception {
        await("entry editor", () -> dialog() != null && dialog().getScene().lookup("#titleField") != null);
        fx(() -> {
            var scene = dialog().getScene();
            ((TextField) scene.lookup("#titleField")).setText(title);
            ((TextField) scene.lookup("#urlField")).setText(url);
            if (category != null) ((TextField) scene.lookup("#categoryField")).setText(category);
            ((Button) scene.lookup("#saveButton")).fire();
            return null;
        });
        await("entry saved", () -> dialog() == null);
    }
    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(() -> {
            if (stage != null && stage.getScene() != null) { root().applyCss(); root().layout(); }
            return action.call();
        });
        Platform.runLater(task);
        return task.get(15, TimeUnit.SECONDS);
    }
    private static void await(String description, BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(25).toNanos();
        while (System.nanoTime() < deadline) {
            if (uncaught.get() != null) throw new AssertionError("JavaFX event failure", uncaught.get());
            if (fx(condition::getAsBoolean)) return;
            Thread.sleep(100);
        }
        System.err.println(fx(() -> "UI state: " + (web() == null ? "No WebKit instance" : web().state.get() + " / "
                + web().location.get()) + " / error panel=" + activeContent().lookup("#errorPane").isVisible()));
        screenshot("failure");
        throw new AssertionError("Timed out: " + description);
    }
    private static void screenshot(String name) throws Exception {
        Thread.sleep(180); // Allow a rendered pulse after a layout or scene change.
        BufferedImage image = fx(() -> {
            root().applyCss(); root().layout();
            var snapshot = stage.getScene().snapshot(null);
            int width = (int) snapshot.getWidth(), height = (int) snapshot.getHeight();
            int[] pixels = new int[width * height];
            snapshot.getPixelReader().getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
            BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            result.setRGB(0, 0, width, height, pixels, 0, width);
            return result;
        });
        Path directory = Path.of("target", "screenshots");
        Files.createDirectories(directory);
        ImageIO.write(image, "png", directory.resolve(name + ".png").toFile());
        NativeWebPage nativePage = fx(() -> web() instanceof NativeWebPage p && p.view().isVisible() ? p : null);
        if (nativePage != null) fx(() -> nativePage.snapshot(directory.resolve(name + "-native-page.png"))).get(20, TimeUnit.SECONDS);
    }
}
