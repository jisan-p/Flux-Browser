package com.flux.browser.web;

import com.flux.browser.controller.WebContentController;
import com.flux.browser.util.Dialogs;
import com.flux.browser.util.Views;
import java.util.concurrent.CompletableFuture;
import com.flux.browser.feature.ErudaScript;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.web.WebHistory;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

/** Compatibility engine for non-macOS hosts and explicit comparison runs. */
public final class JavaFxPage extends BrowserPage {
    private final WebView web;
    private final Stage owner;
    private boolean closed, toolsWanted;
    private long documentVersion, toolsRequest;
    public JavaFxPage(Stage owner) {
        this.owner = owner;
        JavaFxConsole.installIfRequested();
        web = Views.<WebContentController>load("WebContent").controller().view();
        var engine = web.getEngine();
        engine.getHistory().setMaxSize(100);
        engine.getLoadWorker().stateProperty().addListener((o,before,after) -> {
            if (after == Worker.State.SCHEDULED) documentVersion++;
            if (after == Worker.State.SUCCEEDED && toolsWanted && !closed)
                developerTools("show").exceptionally(error -> { toolsWanted = false; return null; });
        });
        location.bind(engine.locationProperty()); title.bind(engine.titleProperty());
        progress.bind(engine.getLoadWorker().progressProperty()); state.bind(engine.getLoadWorker().stateProperty());
        engine.getHistory().currentIndexProperty().addListener(o -> historyChanged());
        engine.getHistory().getEntries().addListener((ListChangeListener<WebHistory.Entry>) c -> historyChanged());
        engine.setCreatePopupHandler(features -> {
            var page = new JavaFxPage(owner); popup.accept(page); return page.web.getEngine();
        });
        engine.setOnVisibilityChanged(e -> { if (!e.getData()) closeRequested.run(); });
        engine.setOnAlert(e -> Dialogs.alert(owner, "Page message", e.getData()));
        engine.setConfirmHandler(s -> Dialogs.confirm(owner, "Page confirmation", s));
        engine.setPromptHandler(p -> Dialogs.prompt(owner, "Page prompt", p.getMessage(), p.getDefaultValue()));
    }
    private void historyChanged() {
        var h = web.getEngine().getHistory();
        back.set(h.getCurrentIndex() > 0); forward.set(h.getCurrentIndex() < h.getEntries().size()-1);
    }
    public Node view() { return web; }
    public void load(String url) { web.getEngine().load(url); }
    public void back() { web.getEngine().getHistory().go(-1); }
    public void forward() { web.getEngine().getHistory().go(1); }
    public void reload() { web.getEngine().reload(); }
    public void stop() { web.getEngine().getLoadWorker().cancel(); }
    public void zoom(double value) { zoomLevel.set(value); web.setZoom(value); }
    public void focus() { web.requestFocus(); }
    public void visible(boolean value) { web.setVisible(value); web.setManaged(value); }
    public CompletableFuture<String> evaluate(String script) {
        try { return CompletableFuture.completedFuture(String.valueOf(web.getEngine().executeScript(script))); }
        catch (RuntimeException e) { return CompletableFuture.failedFuture(e); }
    }
    public CompletableFuture<String> developerTools(String operation) {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Tab closed"));
        if (!java.util.Set.of("show","console","toggle","close","status").contains(operation))
            return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown Developer Tools action"));
        try {
            boolean installed = Boolean.TRUE.equals(web.getEngine().executeScript("!!window.__fluxDevTools"));
            if (operation.equals("status")) return CompletableFuture.completedFuture(installed ? "visible" : "closed");
            if (operation.equals("close") || operation.equals("toggle") && (installed || toolsWanted)) {
                toolsWanted = false; toolsRequest++;
                if (installed) web.getEngine().executeScript("window.__fluxDevTools.destroy();delete window.__fluxDevTools;");
                return CompletableFuture.completedFuture("Developer Tools closed");
            }
            if (state.get() != Worker.State.SUCCEEDED)
                return CompletableFuture.failedFuture(new IllegalStateException("Wait for the page to finish loading"));
            toolsWanted = true;
            if (installed) {
                web.getEngine().executeScript(operation.equals("console") ? "window.__fluxDevTools.show('console')" : "window.__fluxDevTools.show()");
                return CompletableFuture.completedFuture("Developer Tools opened for this tab");
            }
            long version = documentVersion, request = ++toolsRequest;
            var result = new CompletableFuture<String>();
            ErudaScript.load().whenComplete((script,error) -> Platform.runLater(() -> {
                if (closed || !toolsWanted || version != documentVersion || request != toolsRequest) {
                    result.completeExceptionally(new IllegalStateException("Page changed before Developer Tools opened")); return;
                }
                if (error != null) { toolsWanted = false; result.completeExceptionally(error); return; }
                try {
                    web.getEngine().executeScript(script);
                    if (operation.equals("console")) web.getEngine().executeScript("window.__fluxDevTools.show('console')");
                    result.complete("Developer Tools opened for this tab");
                } catch (RuntimeException failure) { toolsWanted = false; result.completeExceptionally(failure); }
            }));
            return result;
        } catch (RuntimeException error) { return CompletableFuture.failedFuture(error); }
    }
    public void close() {
        if (closed) return;
        developerTools("close"); closed = true; documentVersion++;
        var e = web.getEngine();
        e.setCreatePopupHandler(null); e.setOnVisibilityChanged(null); e.setOnAlert(null);
        e.setConfirmHandler(null); e.setPromptHandler(null); e.getLoadWorker().cancel(); e.load(null);
        location.unbind(); title.unbind(); progress.unbind(); state.unbind();
    }
}
