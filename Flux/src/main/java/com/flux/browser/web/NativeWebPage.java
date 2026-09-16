package com.flux.browser.web;

import com.flux.browser.controller.NativeWebContentController;
import com.flux.browser.util.Views;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.concurrent.Worker;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;

/** JNI commands enqueue on AppKit; native callbacks enqueue on FX. UI dispatch never waits for a web-content script result. */
public final class NativeWebPage extends BrowserPage {
    private static final Map<Long, NativeWebPage> PAGES = new ConcurrentHashMap<>(); // Native callbacks may check liveness; page state stays on FX.
    private static final AtomicLong IDS = new AtomicLong();
    private static boolean libraryLoaded;
    private final long id;
    private final Stage owner;
    private final StackPane viewport;
    private final Map<Long, CompletableFuture<String>> scripts = new HashMap<>();
    private final InvalidationListener layoutListener = o -> scheduleBounds();
    private boolean visible, closed, boundsQueued;
    private long request;
    private double[] lastBounds;

    public static boolean enabled() {
        return System.getProperty("os.name").startsWith("Mac")
                && !"javafx".equals(System.getProperty("flux.engine", "native"));
    }
    public NativeWebPage(Stage owner) { this(owner, IDS.incrementAndGet(), false); }
    private NativeWebPage(Stage owner, long id, boolean adopted) {
        loadLibrary();
        this.id = id; this.owner = owner;
        viewport = Views.<NativeWebContentController>load("NativeWebContent").controller().view();
        viewport.getProperties().put("browserPage", this);
        long handle = windowHandle(owner);
        PAGES.put(id, this);
        if (adopted) attach(id, handle); else create(id, handle);
        viewport.layoutBoundsProperty().addListener(layoutListener);
        viewport.localToSceneTransformProperty().addListener(layoutListener);
        owner.widthProperty().addListener(layoutListener); owner.heightProperty().addListener(layoutListener);
        owner.showingProperty().addListener(layoutListener);
        // A shell focus change must return Cocoa's first responder from WKWebView to Glass.
        owner.getScene().focusOwnerProperty().addListener(focusListener);
    }
    private final javafx.beans.value.ChangeListener<Node> focusListener = (o, before, after) -> shellFocus(after);
    private void shellFocus(Node after) { if (!closed && after != null && after != viewport) command(id, "blur", ""); }
    private static synchronized void loadLibrary() {
        if (libraryLoaded) return;
        String resource = "/native/" + System.getProperty("os.arch") + "/libfluxwebkit.dylib";
        try (var input = NativeWebPage.class.getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Missing " + resource + "; run mvn compile on macOS with Xcode Command Line Tools installed");
            Path lib = Files.createTempFile("flux-webkit-", ".dylib");
            Files.copy(input, lib, StandardCopyOption.REPLACE_EXISTING); lib.toFile().deleteOnExit();
            System.load(lib.toAbsolutePath().toString()); libraryLoaded = true;
        } catch (IOException e) { throw new IllegalStateException("Cannot load native macOS WebKit", e); }
    }
    private static long windowHandle(Stage stage) {
        try {
            Class<?> helper = Class.forName("com.sun.javafx.stage.WindowHelper");
            Object peer = helper.getMethod("getPeer", Window.class).invoke(null, stage);
            if (peer == null) throw new IllegalStateException("Show the browser window before opening a native page");
            return (long) Class.forName("com.sun.javafx.tk.TKStage").getMethod("getRawHandle").invoke(peer);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Native viewport requires the JavaFX exports configured in pom.xml", e);
        }
    }
    private void scheduleBounds() {
        if (closed || boundsQueued) return;
        boundsQueued = true;
        Platform.runLater(() -> { boundsQueued = false; syncBounds(); });
    }
    private void syncBounds() {
        if (closed) return;
        Bounds b = viewport.localToScene(viewport.getBoundsInLocal());
        boolean shown = visible && owner.isShowing() && b.getWidth() > 0 && b.getHeight() > 0;
        double[] values = { b.getMinX(), b.getMinY(), b.getWidth(), b.getHeight(), shown ? 1 : 0 };
        if (!Arrays.equals(lastBounds, values)) {
            frame(id, values[0], values[1], values[2], values[3], shown);
            lastBounds = values;
        }
    }
    public Node view() { return viewport; }
    public void load(String url) { state.set(Worker.State.SCHEDULED); command(id, "load", url); }
    public void back() { state.set(Worker.State.SCHEDULED); command(id, "back", ""); }
    public void forward() { state.set(Worker.State.SCHEDULED); command(id, "forward", ""); }
    public void reload() { state.set(Worker.State.SCHEDULED); command(id, "reload", ""); }
    public void stop() { command(id, "stop", ""); if (state.get() == Worker.State.RUNNING || state.get() == Worker.State.SCHEDULED) state.set(Worker.State.CANCELLED); }
    public void zoom(double value) { zoomLevel.set(value); command(id, "zoom", Double.toString(value)); }
    public void focus() { if (visible && !closed) { viewport.requestFocus(); command(id, "focus", ""); } }
    public void visible(boolean value) {
        visible = value; viewport.setVisible(value); viewport.setManaged(value);
        // Hide immediately; bounds after layout for showing/resizing.
        if (!value) { command(id, "hide", ""); lastBounds = null; }
        scheduleBounds();
    }
    public CompletableFuture<String> evaluate(String script) {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Tab closed"));
        long token = ++request;
        var future = new CompletableFuture<String>(); scripts.put(token, future);
        evaluate(id, token, script);
        future.orTimeout(15, TimeUnit.SECONDS).whenComplete((v, e) -> Platform.runLater(() -> scripts.remove(token)));
        return future;
    }
    /** Native page-only snapshot: JavaFX Scene.snapshot cannot capture an embedded NSView. */
    public CompletableFuture<String> snapshot(Path destination) {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Tab closed"));
        long token = ++request;
        var future = new CompletableFuture<String>(); scripts.put(token, future);
        snapshot(id, token, destination.toAbsolutePath().toString());
        future.orTimeout(15, TimeUnit.SECONDS).whenComplete((v, e) -> Platform.runLater(() -> scripts.remove(token)));
        return future;
    }
    public void close() {
        if (closed) return;
        closed = true; PAGES.remove(id); destroy(id);
        viewport.layoutBoundsProperty().removeListener(layoutListener);
        viewport.localToSceneTransformProperty().removeListener(layoutListener);
        owner.widthProperty().removeListener(layoutListener); owner.heightProperty().removeListener(layoutListener);
        owner.showingProperty().removeListener(layoutListener);
        owner.getScene().focusOwnerProperty().removeListener(focusListener);
        scripts.values().forEach(f -> f.completeExceptionally(new CancellationException("Tab closed"))); scripts.clear();
    }
    // Called from AppKit via JNI. Keep this entry point nonblocking, even for popups and JS evaluation.
    private static void event(long id, String kind, String value, long token, double number) {
        // Do not schedule into Glass after its last page has been disposed during toolkit shutdown.
        if (!PAGES.containsKey(id)) { if (kind.equals("popup")) destroy(token); return; }
        Platform.runLater(() -> {
            NativeWebPage p = PAGES.get(id);
            if (p == null) { if (kind.equals("popup")) destroy(token); return; }
            switch (kind) {
                case "url" -> p.location.set(value);
                case "title" -> p.title.set(value);
                case "progress" -> p.progress.set(number);
                case "history" -> { p.back.set((token & 1) != 0); p.forward.set((token & 2) != 0); }
                case "start" -> { p.state.set(Worker.State.SCHEDULED); p.state.set(Worker.State.RUNNING); }
                case "finish" -> p.state.set(Worker.State.SUCCEEDED);
                case "error" -> p.state.set(Worker.State.FAILED);
                case "close" -> p.closeRequested.run();
                case "popup" -> p.popup.accept(new NativeWebPage(p.owner, token, true));
                case "shortcut" -> p.shortcut.accept(value);
                case "script", "scriptError" -> {
                    var f = p.scripts.remove(token);
                    if (f != null) { if (kind.equals("script")) f.complete(value); else f.completeExceptionally(new IllegalStateException(value)); }
                }
                default -> { }
            }
        });
    }
    /** Desktop test hook: posts inside this application's AppKit event queue, never to other apps. */
    public void postShortcutForTesting(String key) {
        if (!Boolean.getBoolean("flux.testInput")) throw new IllegalStateException("Test input is disabled");
        command(id, "testKey", key);
    }
    private static native void create(long id, long window);
    private static native void attach(long id, long window);
    private static native void frame(long id, double x, double y, double width, double height, boolean visible);
    private static native void command(long id, String name, String value);
    private static native void evaluate(long id, long request, String script);
    private static native void snapshot(long id, long request, String destination);
    private static native void destroy(long id);
}
