package com.flux.browser.controller;

import com.flux.browser.db.HistoryDAO;
import com.flux.browser.db.SpeedDialDAO;
import com.flux.browser.util.Dialogs;
import com.flux.browser.util.UrlResolver;
import javafx.beans.property.*;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebHistory;
import javafx.scene.web.WebView;

/** Owns one WebView and its page lifecycle. WebEngine is only touched on the FX thread. */
public final class WebTabController {
    @FXML private WebView webView;
    @FXML private Parent speedDial;
    @FXML private SpeedDialController speedDialController;
    @FXML private VBox errorPane;
    @FXML private Label errorUrl, errorMessage;
    private final ReadOnlyStringWrapper title = new ReadOnlyStringWrapper("Speed Dial");
    private final ReadOnlyStringWrapper location = new ReadOnlyStringWrapper(UrlResolver.HOME);
    private final ReadOnlyBooleanWrapper loading = new ReadOnlyBooleanWrapper(false);
    private BrowserController browser;
    private HistoryDAO history;
    private boolean atHome = true;
    private boolean homeReturnError;
    private boolean disposed;
    private String status = "Ready to explore";
    private String attemptedUrl = "";

    @FXML private void initialize() {
        WebEngine engine = engine();
        engine.getHistory().setMaxSize(100);
        engine.locationProperty().addListener((observable, before, after) -> {
            if (disposed) return;
            if (!atHome) {
                location.set(after == null ? "" : after);
                if (UrlResolver.isWeb(after)) attemptedUrl = after;
            }
            changed();
        });
        engine.titleProperty().addListener((observable, before, after) -> {
            if (!disposed && !atHome) { resolveTitle(); changed(); }
        });
        engine.getLoadWorker().progressProperty().addListener((observable, before, after) -> changed());
        engine.getLoadWorker().stateProperty().addListener((observable, before, state) -> loadState(state));
        engine.getHistory().currentIndexProperty().addListener((observable, before, after) -> changed());
        engine.getHistory().getEntries().addListener((ListChangeListener<WebHistory.Entry>) change -> changed());
        engine.setOnStatusChanged(event -> {
            if (!disposed) { status = event.getData() == null || event.getData().isBlank() ? "Ready" : event.getData(); changed(); }
        });
    }

    public void configure(BrowserController browser, HistoryDAO history, SpeedDialDAO dials) {
        this.browser = browser;
        this.history = history;
        speedDialController.configure(browser, dials);
        engine().setCreatePopupHandler(features -> browser.newPopupTab());
        engine().setOnAlert(event -> Dialogs.alert(browser.window(), "Message from " + UrlResolver.host(engine().getLocation()), event.getData()));
        engine().setConfirmHandler(message -> Dialogs.confirm(browser.window(), "Confirm · " + UrlResolver.host(engine().getLocation()), message));
        engine().setPromptHandler(prompt -> Dialogs.prompt(browser.window(), "Prompt · " + UrlResolver.host(engine().getLocation()), prompt.getMessage(), prompt.getDefaultValue()));
        engine().setOnVisibilityChanged(event -> { if (!event.getData() && !disposed) browser.closeTab(this); });
    }

    private void loadState(Worker.State state) {
        if (disposed) return;
        loading.set(state == Worker.State.SCHEDULED || state == Worker.State.RUNNING);
        if (state == Worker.State.SCHEDULED) {
            atHome = false;
            visible(speedDial, false); visible(webView, true); visible(errorPane, false);
            title.set("Loading…");
            status = "Loading page…";
        } else if (state == Worker.State.SUCCEEDED && !atHome) {
            location.set(engine().getLocation());
            resolveTitle();
            status = "Page loaded";
            if (history != null && UrlResolver.isWeb(location.get())) {
                try {
                    browser.perform("", history.saveVisit(title.get(), location.get()), ignored -> {});
                } catch (IllegalArgumentException error) {
                    browser.message("Page loaded; this address is too long to save in history.");
                }
            }
        } else if (state == Worker.State.FAILED && !atHome) {
            title.set("Page unavailable");
            errorUrl.setText(attemptedUrl);
            errorMessage.setText("Check the address and your internet connection, then try again. Some websites require features this browser does not support.");
            visible(webView, false); visible(errorPane, true);
            status = "Unable to load this page";
        } else if (state == Worker.State.CANCELLED && !atHome) {
            resolveTitle();
            status = "Loading stopped";
        }
        changed();
    }

    private void resolveTitle() {
        String url = engine().getLocation();
        title.set(url == null || url.isBlank() || url.equals("about:blank") ? "New tab" : UrlResolver.pageTitle(engine().getTitle(), url));
    }

    public void load(String address) {
        if (disposed) return;
        if (UrlResolver.HOME.equals(address)) { home(); return; }
        attemptedUrl = address;
        atHome = false;
        location.set(address);
        visible(speedDial, false); visible(errorPane, false); visible(webView, true);
        engine().load(address);
        webView.requestFocus();
        changed();
    }

    @FXML public void home() {
        if (disposed) return;
        if (!atHome) homeReturnError = errorPane.isVisible();
        atHome = true;
        engine().getLoadWorker().cancel();
        // Stop hidden media while retaining the document and WebHistory for Back from Home.
        if (engine().getDocument() != null) {
            try { engine().executeScript("document.querySelectorAll('video,audio').forEach(function(m){m.pause();})"); }
            catch (RuntimeException ignored) { /* A navigating document may already have been released. */ }
        }
        loading.set(false); title.set("Speed Dial"); location.set(UrlResolver.HOME); status = "Ready to explore";
        visible(webView, false); visible(errorPane, false); visible(speedDial, true);
        speedDialController.refresh();
        changed();
    }

    public void back() {
        if (!canGoBack()) return;
        if (atHome) {
            atHome = false;
            visible(speedDial, false); visible(webView, !homeReturnError); visible(errorPane, homeReturnError);
            location.set(engine().getLocation());
            if (homeReturnError) { title.set("Page unavailable"); status = "Unable to load this page"; }
            else resolveTitle();
            changed();
        } else engine().getHistory().go(failedOutsideHistory() ? 0 : -1);
    }

    public void forward() { if (canGoForward()) engine().getHistory().go(1); }
    public void reload() { if (atHome) speedDialController.refresh(); else if (errorPane.isVisible()) retry(); else engine().reload(); }
    public void stop() { engine().getLoadWorker().cancel(); }
    @FXML private void retry() { if (!attemptedUrl.isBlank()) load(attemptedUrl); }

    private boolean failedOutsideHistory() {
        WebHistory webHistory = engine().getHistory();
        return errorPane.isVisible() && !webHistory.getEntries().isEmpty()
                && !webHistory.getEntries().get(webHistory.getCurrentIndex()).getUrl().equals(engine().getLocation());
    }

    public boolean canGoBack() { return atHome ? UrlResolver.isWeb(engine().getLocation()) : failedOutsideHistory() || engine().getHistory().getCurrentIndex() > 0; }
    public boolean canGoForward() { return !atHome && engine().getHistory().getCurrentIndex() < engine().getHistory().getEntries().size() - 1; }
    public boolean isHome() { return atHome; }
    public WebEngine engine() { return webView.getEngine(); }
    public ReadOnlyStringProperty titleProperty() { return title.getReadOnlyProperty(); }
    public ReadOnlyStringProperty locationProperty() { return location.getReadOnlyProperty(); }
    public ReadOnlyBooleanProperty loadingProperty() { return loading.getReadOnlyProperty(); }
    public double progress() { return loading.get() ? engine().getLoadWorker().getProgress() : 0; }
    public String status() { return status; }
    public double zoom() { return webView.getZoom(); }
    public void zoom(double value) { webView.setZoom(Math.clamp(value, 0.75, 1.5)); }
    public void focus() { if (!atHome) webView.requestFocus(); }
    public void refreshDials() { speedDialController.refresh(); }
    private void changed() { if (!disposed && browser != null) browser.tabChanged(this); }
    private static void visible(Node node, boolean visible) { node.setVisible(visible); node.setManaged(visible); }

    public void dispose() {
        disposed = true;
        speedDialController.dispose();
        engine().setCreatePopupHandler(null); engine().setOnAlert(null); engine().setConfirmHandler(null);
        engine().setPromptHandler(null); engine().setOnVisibilityChanged(null); engine().setOnStatusChanged(null);
        engine().getLoadWorker().cancel();
        engine().load(null);
    }
}
