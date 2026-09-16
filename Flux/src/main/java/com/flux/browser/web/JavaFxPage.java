package com.flux.browser.web;

import com.flux.browser.controller.WebContentController;
import com.flux.browser.util.Dialogs;
import com.flux.browser.util.Views;
import java.util.concurrent.CompletableFuture;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.web.WebHistory;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

/** Compatibility engine for non-macOS hosts and explicit comparison runs. */
public final class JavaFxPage extends BrowserPage {
    private final WebView web;
    private final Stage owner;
    public JavaFxPage(Stage owner) {
        this.owner = owner;
        web = Views.<WebContentController>load("WebContent").controller().view();
        var engine = web.getEngine();
        engine.getHistory().setMaxSize(100);
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
    public void close() {
        var e = web.getEngine();
        e.setCreatePopupHandler(null); e.setOnVisibilityChanged(null); e.setOnAlert(null);
        e.setConfirmHandler(null); e.setPromptHandler(null); e.getLoadWorker().cancel(); e.load(null);
        location.unbind(); title.unbind(); progress.unbind(); state.unbind();
    }
}
