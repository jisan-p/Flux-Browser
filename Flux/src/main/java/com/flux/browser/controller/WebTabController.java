package com.flux.browser.controller;

import com.flux.browser.db.HistoryDAO;
import com.flux.browser.db.SpeedDialDAO;
import com.flux.browser.util.UrlResolver;
import javafx.beans.property.*;
import com.flux.browser.web.BrowserPage;
import com.flux.browser.web.JavaFxPage;
import com.flux.browser.web.NativeWebPage;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;


/** Owns one lazily allocated browser page and its FX-thread lifecycle. */
public final class WebTabController {
    @FXML private StackPane root;
    private BrowserPage page;
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
    private boolean active;
    private double zoom = 1;
    private String status = "Ready to explore";
    private String attemptedUrl = "";
    private String deferredUrl;

    private void initializePage(BrowserPage content) {
        page = content;
        root.getProperties().put("browserPage", page);
        root.getChildren().add(0, page.view());
        page.zoom(zoom);
        page.location.addListener((o, before, after) -> {
            if (disposed || atHome) return;
            if (after != null && !after.isBlank()) {
                location.set(after);
                if (UrlResolver.isWeb(after)) attemptedUrl = after;
            }
            changed();
        });
        page.title.addListener(o -> { if (!disposed && !atHome) { resolveTitle(); changed(); } });
        page.progress.addListener(o -> changed());
        page.state.addListener((o, before, after) -> loadState(after));
        page.back.addListener(o -> changed()); page.forward.addListener(o -> changed());
        page.popup = browser::newPopupTab;
        page.closeRequested = () -> browser.closeTab(this);
        page.shortcut = browser::nativeShortcut;
        if (page instanceof NativeWebPage n) { n.pdf.addListener(o -> changed()); n.openUrl = browser::openNewUrl; n.pageAction = payload -> browser.pageAction(n, payload); }
        updatePageVisibility();
    }

    public void adopt(BrowserPage content) {
        atHome = false; visible(speedDial, false); initializePage(content);
        loadState(content.state.get());
    }

    public void configure(BrowserController browser, HistoryDAO history, SpeedDialDAO dials) {
        this.browser = browser;
        this.history = history;
        speedDialController.configure(browser, dials);
    }

    private void loadState(Worker.State state) {
        if (disposed) return;
        loading.set(state == Worker.State.SCHEDULED || state == Worker.State.RUNNING);
        if (state == Worker.State.SCHEDULED) {
            atHome = false;
            updateHomeActivity();
            visible(speedDial, false); visible(errorPane, false); updatePageVisibility();
            title.set("Loading…");
            status = "Loading page…";
        } else if (state == Worker.State.SUCCEEDED && !atHome) {
            location.set(page.location.get());
            resolveTitle();
            lastSuccessfulUrl = location.get();
            status = "Page loaded";
            browser.pageLoaded(this);
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
            page.visible(false); visible(errorPane, true);
            status = "Unable to load this page";
        } else if (state == Worker.State.CANCELLED && !atHome) {
            resolveTitle();
            status = "Loading stopped";
        }
        changed();
    }

    private void resolveTitle() {
        String url = page.location.get();
        title.set(url == null || url.isBlank() || url.equals("about:blank") ? "New tab" : UrlResolver.pageTitle(page.title.get(), url));
    }

    public void load(String address) {
        if (disposed) return;
        if (UrlResolver.HOME.equals(address)) { home(); return; }
        deferredUrl = null;
        attemptedUrl = address;
        atHome = false;
        updateHomeActivity();
        location.set(address);
        BrowserPage engine = page();
        visible(speedDial, false); visible(errorPane, false); updatePageVisibility();
        engine.load(address);
        page.focus();
        changed();
    }

    @FXML public void home() {
        if (disposed) return;
        if (!atHome) homeReturnError = errorPane.isVisible();
        atHome = true; deferredUrl = null;
        if (page != null) page.stop();
        // Retain history but pause media when explicitly returning Home.
        if (page != null) page.evaluate("document.querySelectorAll('video,audio').forEach(m=>m.pause()); true");
        loading.set(false); title.set("Speed Dial"); location.set(UrlResolver.HOME); status = "Ready to explore";
        if (page != null) page.visible(false);
        visible(errorPane, false); visible(speedDial, true);
        updateHomeActivity();
        changed();
    }

    public void back() {
        if (!canGoBack()) return;
        if (atHome) {
            atHome = false;
            updateHomeActivity();
            visible(speedDial, false); visible(errorPane, homeReturnError); updatePageVisibility();
            location.set(page.location.get());
            if (homeReturnError) { title.set("Page unavailable"); status = "Unable to load this page"; }
            else resolveTitle();
            changed();
        } else { if (failedOutsideHistory()) page.load(lastSuccessfulUrl); else page.back(); }
    }

    public void forward() { if (canGoForward()) page.forward(); }
    public void reload() { if (atHome) speedDialController.refresh(); else if (errorPane.isVisible()) retry(); else if (page != null) page.reload(); }
    public void stop() { if (page != null) page.stop(); }
    @FXML private void retry() { if (!attemptedUrl.isBlank()) load(attemptedUrl); }

    private String lastSuccessfulUrl = "";
    private boolean failedOutsideHistory() {
        return errorPane.isVisible() && !lastSuccessfulUrl.isBlank() && !lastSuccessfulUrl.equals(attemptedUrl);
    }
    public boolean canGoBack() { return page != null && (atHome ? !attemptedUrl.isBlank() : failedOutsideHistory() || page.back.get()); }
    public boolean canGoForward() { return page != null && !atHome && page.forward.get(); }
    public boolean isHome() { return atHome; }
    public BrowserPage page() {
        if (disposed) throw new IllegalStateException("Tab is closed");
        if (page == null) initializePage(NativeWebPage.enabled() ? new NativeWebPage(browser.window()) : new JavaFxPage(browser.window()));
        return page;
    }
    public BrowserPage existingPage() { return page; }
    public void restoreDeferred(String url, String name, double value) {
        zoom(value); deferredUrl = UrlResolver.HOME.equals(url) ? null : url;
        atHome = deferredUrl == null; location.set(url); title.set(name == null ? "Restored tab" : name); changed();
    }
    public void restore(String url, String name, double value) { restoreDeferred(url,name,value); if (active && deferredUrl != null) activateDeferred(); }
    private void activateDeferred() {
        String url = deferredUrl; deferredUrl = null;
        if (url.startsWith("file:")) openPdf(java.nio.file.Path.of(java.net.URI.create(url))); else load(url);
    }
    public void openPdf(java.nio.file.Path path) {
        attemptedUrl = path.toAbsolutePath().toUri().toString(); location.set(attemptedUrl);
        atHome = false; deferredUrl = null; updateHomeActivity(); visible(speedDial,false); visible(errorPane,false);
        BrowserPage p = page(); updatePageVisibility();
        if (p instanceof NativeWebPage n) n.action("pdfOpen",path.toAbsolutePath().toString());
        changed();
    }
    public ReadOnlyStringProperty titleProperty() { return title.getReadOnlyProperty(); }
    public ReadOnlyStringProperty locationProperty() { return location.getReadOnlyProperty(); }
    public ReadOnlyBooleanProperty loadingProperty() { return loading.getReadOnlyProperty(); }
    public double progress() { return loading.get() ? page.progress.get() : 0; }
    public String status() { return status; }
    public double zoom() { return zoom; }
    public void zoom(double value) { zoom = Math.clamp(value, 0.75, 1.5); if (page != null) page.zoom(zoom); }
    public void focus() { if (!atHome && page != null) page.focus(); }
    public void setActive(boolean active) { this.active = active; if (active && deferredUrl != null) activateDeferred(); updateHomeActivity(); updatePageVisibility(); }
    private void updateHomeActivity() { speedDialController.setActive(active && atHome && !disposed); }
    private void updatePageVisibility() { if (page != null) page.visible(active && !atHome && !errorPane.isVisible() && !disposed); }
    public void applyAppearance() { speedDialController.applyAppearance(); }
    public void refreshDials() { speedDialController.refresh(); }
    private void changed() { if (!disposed && browser != null) browser.tabChanged(this); }
    private static void visible(Node node, boolean visible) { node.setVisible(visible); node.setManaged(visible); }

    public void dispose() {
        disposed = true;
        speedDialController.dispose();
        if (page != null) { page.close(); root.getChildren().remove(page.view()); page = null; }
    }
}
