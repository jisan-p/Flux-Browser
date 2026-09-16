package com.flux.browser.controller;

import com.flux.browser.db.*;
import com.flux.browser.model.*;
import com.flux.browser.util.Dialogs;
import com.flux.browser.util.UrlResolver;
import com.flux.browser.util.Views;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javafx.animation.Animation;
import javafx.animation.AnimationTimer;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import com.flux.browser.web.BrowserPage;
import javafx.stage.Stage;
import javafx.util.Duration;

/** Coordinates the shell; each tab owns its own browser page and session navigation history. */
public final class BrowserController {
    private record Tab(WebTabController page, Parent content, TabHeaderController header, Parent chip) {}
    @FXML private BorderPane root;
    @FXML private HBox tabHeaders;
    @FXML private StackPane tabHost;
    @FXML private ScrollPane tabScroll;
    @FXML private Parent library, settings;
    @FXML private SidebarController sidebarController;
    @FXML private LibraryController libraryController;
    @FXML private SettingsController settingsController;
    @FXML private TextField addressBar;
    @FXML private Button backButton, forwardButton, reloadButton, stopButton, bookmarkButton, maximizeButton;
    @FXML private Label schemeLabel, databaseStatus, statusText, tabCount;
    @FXML private ProgressBar loadProgress;
    private final List<Tab> tabs = new ArrayList<>();
    private final PauseTransition toast = new PauseTransition(Duration.seconds(5));
    // Merge WebKit progress/title/status bursts into one shell update per display pulse.
    private final AnimationTimer chromeRefresh = new AnimationTimer() {
        @Override public void handle(long now) { stop(); refreshChrome(); }
    };
    private Stage stage;
    private DatabaseManager database;
    private HistoryDAO historyDAO;
    private BookmarkDAO bookmarkDAO;
    private SpeedDialDAO speedDialDAO;
    private Tab active;
    private boolean closed, connecting, bookmarked;
    private int bookmarkRequest;
    private String checkedBookmarkUrl;
    private double dragX, dragY, resizeX, resizeY, resizeWidth, resizeHeight;

    public void configure(Stage stage, DatabaseManager database) {
        this.stage = stage; this.database = database;
        historyDAO = new HistoryDAO(database); bookmarkDAO = new BookmarkDAO(database); speedDialDAO = new SpeedDialDAO(database);
        sidebarController.configure(this);
        libraryController.configure(this, bookmarkDAO, historyDAO);
        settingsController.configure(this);
        toast.setOnFinished(event -> updateStatus());
        addressBar.focusedProperty().addListener((observable, before, focused) -> {
            addressBar.getParent().pseudoClassStateChanged(PseudoClass.getPseudoClass("focused"), focused);
            if (focused) Platform.runLater(addressBar::selectAll);
            else refreshChrome();
        });
        stage.getScene().addEventFilter(KeyEvent.KEY_PRESSED, this::keyPressed);
        stage.maximizedProperty().addListener((observable, before, after) -> maximizeButton.setText(after ? "❐" : "□"));
        stage.iconifiedProperty().addListener((observable, before, after) -> updateActiveContent());
        createTab();
        reconnect();
    }

    private Tab createTab() {
        var page = Views.<WebTabController>load("WebTab");
        var header = Views.<TabHeaderController>load("TabHeader");
        Tab tab = new Tab(page.controller(), page.root(), header.controller(), header.root());
        page.controller().configure(this, historyDAO, speedDialDAO);
        header.controller().configure(page.controller(), () -> selectTab(tab), () -> closeTab(tab.page()));
        tabs.add(tab); tabHeaders.getChildren().add(tab.chip());
        visible(tab.content(), false);
        tabHost.getChildren().add(tab.content());
        selectTab(tab);
        return tab;
    }

    @FXML public void newTab() { if (!closed) { createTab(); focusAddress(); } }
    public void newPopupTab(BrowserPage page) {
        if (closed) { page.close(); return; }
        createTab().page().adopt(page);
    }

    /** Cocoa receives keys while WKWebView has focus; route shell shortcuts back to FX. */
    public void nativeShortcut(String key) {
        if (closed) return;
        switch (key) {
            case "l" -> focusAddress(); case "t" -> newTab();
            case "w" -> { if (active != null) closeTab(active.page()); }
            case "r" -> reload(); case "d" -> toggleBookmark();
            case "y" -> showLibrary(false); case "bookmarks" -> showLibrary(true);
            case "home" -> home(); case "back" -> back(); case "forward" -> forward(); case "stop" -> stopLoading();
            case "nextTab", "previousTab" -> selectTab(tabs.get(Math.floorMod(tabs.indexOf(active) + (key.equals("nextTab") ? 1 : -1), tabs.size())));
            default -> {
                if (key.matches("[1-9]")) {
                    int index = key.equals("9") ? tabs.size()-1 : Integer.parseInt(key)-1;
                    if (index < tabs.size()) selectTab(tabs.get(index));
                }
            }
        }
    }

    private void selectTab(Tab tab) {
        if (closed || !tabs.contains(tab)) return;
        if (active != null && active != tab) {
            active.page().setActive(false);
            visible(active.content(), false);
        }
        active = tab;
        // Keep the scene association: detaching WebView on every switch recreates rendering state.
        // Hidden tabs are unmanaged, so only the selected page participates in layout and painting.
        visible(tab.content(), true);
        for (Tab other : tabs) other.header().selected(other == tab);
        dismissPanels();
        addressBar.setText(tab.page().locationProperty().get());
        updateBookmark(true);
        refreshChrome();
        Platform.runLater(() -> {
            if (closed || active != tab) return;
            tabHeaders.applyCss(); tabHeaders.layout();
            double viewport = tabScroll.getViewportBounds().getWidth();
            double span = tabHeaders.getWidth() - viewport;
            if (span <= 0) return;
            double offset = tabScroll.getHvalue() * span;
            var bounds = tab.chip().getBoundsInParent();
            if (bounds.getMinX() < offset) tabScroll.setHvalue(Math.max(0, bounds.getMinX() / span));
            else if (bounds.getMaxX() > offset + viewport) tabScroll.setHvalue(Math.min(1, (bounds.getMaxX() - viewport) / span));
        });
    }

    public void closeTab(WebTabController page) {
        if (closed) return;
        Tab tab = tabs.stream().filter(item -> item.page() == page).findFirst().orElse(null);
        if (tab == null) return;
        int index = tabs.indexOf(tab);
        boolean wasActive = active == tab;
        tab.header().dispose(); tab.page().dispose();
        tabs.remove(tab); tabHeaders.getChildren().remove(tab.chip()); tabHost.getChildren().remove(tab.content());
        if (tabs.isEmpty()) { active = null; createTab(); focusAddress(); }
        else if (wasActive) selectTab(tabs.get(Math.min(index, tabs.size() - 1)));
        else refreshChrome();
    }

    @FXML private void navigate() { navigateTo(addressBar.getText()); }
    public void navigateTo(String input) {
        if (active == null || closed) return;
        try {
            String address = UrlResolver.resolve(input);
            dismissPanels();
            addressBar.setText(address);
            active.page().load(address);
        } catch (IllegalArgumentException error) { message(error.getMessage()); focusAddress(); }
    }

    @FXML public void home() { if (active != null) { dismissPanels(); active.page().home(); refreshChrome(); } }
    @FXML private void back() { if (panelsVisible()) dismissPanels(); else if (active != null) active.page().back(); }
    @FXML private void forward() { dismissPanels(); if (active != null) active.page().forward(); }
    @FXML private void reload() { if (library.isVisible()) libraryController.refresh(); else if (active != null) active.page().reload(); }
    @FXML private void stopLoading() { if (active != null) active.page().stop(); }

    public void tabChanged(WebTabController page) { if (!closed && active != null && active.page() == page) chromeRefresh.start(); }
    private void refreshChrome() {
        if (closed || active == null) return;
        WebTabController page = active.page();
        String location = page.locationProperty().get();
        if (!addressBar.isFocused()) addressBar.setText(location);
        schemeLabel.setText(page.isHome() ? "FLUX" : location.startsWith("https://") ? "HTTPS" : location.startsWith("http://") ? "HTTP" : "WEB");
        backButton.setDisable(!panelsVisible() && !page.canGoBack());
        forwardButton.setDisable(panelsVisible() || !page.canGoForward());
        reloadButton.setDisable(page.loadingProperty().get());
        stopButton.setDisable(!page.loadingProperty().get());
        loadProgress.setProgress(page.progress());
        tabCount.setText(tabs.size() + (tabs.size() == 1 ? " TAB" : " TABS"));
        stage.setTitle(page.titleProperty().get() + " · Flux");
        if (!panelsVisible()) sidebarController.select(page.isHome() ? "home" : "");
        updateBookmark(false);
        if (toast.getStatus() != Animation.Status.RUNNING) updateStatus();
    }

    private String bookmarkUrl() {
        if (active == null || active.page().isHome()) return "";
        try { return UrlResolver.webAddress(active.page().locationProperty().get()); }
        catch (IllegalArgumentException error) { return ""; }
    }

    private void updateBookmark(boolean force) {
        String url = bookmarkUrl();
        if (!force && url.equals(checkedBookmarkUrl)) return;
        checkedBookmarkUrl = url;
        int version = ++bookmarkRequest;
        setBookmarked(false);
        bookmarkButton.setDisable(true);
        if (url.isBlank() || !storageAvailable()) return;
        bookmarkDAO.contains(url).whenComplete((saved, error) -> Platform.runLater(() -> {
            if (closed || version != bookmarkRequest || !url.equals(bookmarkUrl())) return;
            setBookmarked(error == null && saved);
            bookmarkButton.setDisable(error != null || !storageAvailable());
            storageChanged();
        }));
    }

    private void setBookmarked(boolean saved) {
        bookmarked = saved;
        bookmarkButton.setText(saved ? "★" : "☆");
        bookmarkButton.setAccessibleText(saved ? "Remove bookmark" : "Bookmark this page");
        bookmarkButton.pseudoClassStateChanged(PseudoClass.getPseudoClass("saved"), saved);
    }

    @FXML private void toggleBookmark() {
        String url = bookmarkUrl();
        if (url.isBlank() || bookmarkButton.isDisabled()) return;
        boolean removing = bookmarked;
        bookmarkButton.setDisable(true);
        CompletableFuture<?> future = removing ? bookmarkDAO.deleteByUrl(url)
                : bookmarkDAO.save(0, UrlResolver.pageTitle(active.page().titleProperty().get(), url), url, "Unsorted");
        perform(removing ? "Bookmark removed" : "Bookmark saved", future, ignored -> bookmarksChanged());
        future.whenComplete((result, error) -> { if (error != null) Platform.runLater(() -> updateBookmark(true)); });
    }

    private void bookmarksChanged() {
        updateBookmark(true);
        if (library.isVisible()) libraryController.refresh();
    }

    public void editBookmark(Bookmark item, Runnable refreshed) {
        if (!storageAvailable()) { message("Connect storage in Settings to save bookmarks."); return; }
        Dialogs.edit(stage, item == null ? "Add a bookmark" : "Edit bookmark",
                item == null ? "" : item.title(), item == null ? "https://" : item.url(), item == null ? "Unsorted" : item.category(),
                draft -> observeStorage(bookmarkDAO.save(item == null ? 0 : item.id(), draft.title(), draft.url(), draft.category())),
                () -> { bookmarksChanged(); refreshed.run(); message("Bookmark saved"); });
    }

    public void deleteBookmark(Bookmark item, Runnable refreshed) {
        perform("Bookmark removed", bookmarkDAO.delete(item.id()), ignored -> { bookmarksChanged(); refreshed.run(); });
    }
    public void deleteVisit(HistoryEntry item, Runnable refreshed) {
        perform("Visit removed", historyDAO.deleteHistory(item.id()), ignored -> refreshed.run());
    }
    public void editDial(SpeedDial item) {
        if (!storageAvailable()) { message("Connect storage in Settings to customize Speed Dial."); return; }
        Dialogs.edit(stage, item == null ? "Add a Speed Dial site" : "Edit Speed Dial site",
                item == null ? "" : item.title(), item == null ? "https://" : item.url(), null,
                draft -> observeStorage(speedDialDAO.save(item == null ? 0 : item.id(), draft.title(), draft.url())),
                () -> { refreshDials(); message("Speed Dial saved"); });
    }
    public void deleteDial(SpeedDial item) {
        if (item.id() < 0) return;
        perform("Shortcut removed", speedDialDAO.delete(item.id()), ignored -> refreshDials());
    }
    private void refreshDials() { for (Tab tab : tabs) tab.page().refreshDials(); }

    public void showLibrary(boolean bookmarks) {
        visible(settings, false); visible(library, true); tabHost.setVisible(false);
        updateActiveContent();
        libraryController.show(bookmarks);
        sidebarController.select(bookmarks ? "bookmarks" : "history");
        refreshChrome();
    }
    @FXML public void settings() {
        visible(library, false); visible(settings, true); tabHost.setVisible(false);
        updateActiveContent();
        settingsController.show(active == null ? 1 : active.page().zoom());
        sidebarController.select("settings");
        refreshChrome();
    }
    public void dismissPanels() {
        visible(library, false); visible(settings, false); tabHost.setVisible(true);
        updateActiveContent();
        if (active != null) active.page().focus();
        refreshChrome();
    }
    private boolean panelsVisible() { return library.isVisible() || settings.isVisible(); }
    private void updateActiveContent() { if (active != null) active.page().setActive(!panelsVisible() && !stage.isIconified()); }
    private static void visible(Node node, boolean visible) { node.setVisible(visible); node.setManaged(visible); }
    public void setAccent(boolean cyan) { root.getStyleClass().remove("cyan-theme"); if (cyan) root.getStyleClass().add("cyan-theme"); }
    public void setZoom(double zoom) { if (active != null) active.page().zoom(zoom); }
    public Stage window() { return stage; }
    public boolean isClosed() { return closed; }
    public boolean storageAvailable() { return database != null && database.isAvailable(); }

    public void reconnect() {
        if (closed || connecting) return;
        connecting = true; storageChanged();
        database.initialize().whenComplete((result, error) -> Platform.runLater(() -> {
            if (closed) return;
            connecting = false; storageChanged(); refreshDials(); updateBookmark(true);
            if (library.isVisible()) libraryController.refresh();
            message(error == null ? "Storage connected · your library is ready" : "Storage offline · browsing is available. Reconnect in Settings.");
        }));
    }

    public void storageChanged() {
        if (closed) return;
        boolean online = storageAvailable();
        databaseStatus.setText(connecting ? "●  CONNECTING STORAGE" : online ? "●  STORAGE CONNECTED" : "●  STORAGE OFFLINE");
        databaseStatus.pseudoClassStateChanged(PseudoClass.getPseudoClass("online"), online);
        settingsController.connection(online, connecting);
        if (!online) bookmarkButton.setDisable(true);
    }

    private <T> CompletableFuture<T> observeStorage(CompletableFuture<T> future) {
        future.whenComplete((value, error) -> Platform.runLater(this::storageChanged));
        return future;
    }

    public <T> void perform(String successMessage, CompletableFuture<T> future, Consumer<T> success) {
        future.whenComplete((value, error) -> Platform.runLater(() -> {
            if (closed) return;
            storageChanged();
            if (error != null) message(friendlyError(error));
            else { success.accept(value); if (!successMessage.isBlank()) message(successMessage); }
        }));
    }

    public static String friendlyError(Throwable error) {
        while ((error instanceof CompletionException || error instanceof ExecutionException) && error.getCause() != null) error = error.getCause();
        if (error instanceof SQLException sql && "23505".equals(sql.getSQLState())) return "Another saved destination already uses this address.";
        if (error instanceof IllegalArgumentException) return error.getMessage();
        return "Storage could not complete that action. Check the connection in Settings and try again.";
    }

    public void message(String message) {
        if (closed) return;
        statusText.setText(message); toast.playFromStart();
    }
    private void updateStatus() {
        if (active == null) return;
        String text = panelsVisible() ? "Your browsing page is still open in its tab" : active.page().status();
        statusText.setText(text.substring(0, Math.min(text.length(), 250)));
    }
    public void focusAddress() { addressBar.requestFocus(); addressBar.selectAll(); }

    private void keyPressed(KeyEvent event) {
        boolean handled = true;
        if (event.isControlDown() && event.getCode() == KeyCode.TAB) {
            int next = Math.floorMod(tabs.indexOf(active) + (event.isShiftDown() ? -1 : 1), tabs.size());
            selectTab(tabs.get(next));
        } else if (event.isShortcutDown()) {
            switch (event.getCode()) {
                case L -> focusAddress();
                case T -> newTab();
                case W -> { if (active != null) closeTab(active.page()); }
                case R -> reload();
                case D -> toggleBookmark();
                case Y -> showLibrary(false);
                case B -> { if (event.isShiftDown()) showLibrary(true); else handled = false; }
                case DIGIT1, DIGIT2, DIGIT3, DIGIT4, DIGIT5, DIGIT6, DIGIT7, DIGIT8, DIGIT9 -> {
                    int index = event.getCode() == KeyCode.DIGIT9 ? tabs.size() - 1 : event.getCode().getCode() - KeyCode.DIGIT1.getCode();
                    if (index < tabs.size()) selectTab(tabs.get(index));
                }
                default -> handled = false;
            }
        } else if (event.isAltDown()) {
            switch (event.getCode()) {
                case LEFT -> back(); case RIGHT -> forward(); case HOME -> home(); default -> handled = false;
            }
        } else if (event.getCode() == KeyCode.ESCAPE) {
            if (panelsVisible()) dismissPanels(); else stopLoading();
        } else if (event.getCode() == KeyCode.F5) reload();
        else handled = false;
        if (handled) event.consume();
    }

    @FXML private void minimize() { stage.setIconified(true); }
    @FXML private void maximize() { stage.setMaximized(!stage.isMaximized()); }
    @FXML private void quit() { close(); stage.close(); Platform.exit(); }
    @FXML private void beginDrag(MouseEvent event) { dragX = event.getScreenX() - stage.getX(); dragY = event.getScreenY() - stage.getY(); }
    @FXML private void dragWindow(MouseEvent event) {
        if (!stage.isMaximized() && event.isPrimaryButtonDown()) { stage.setX(event.getScreenX() - dragX); stage.setY(event.getScreenY() - dragY); }
    }
    @FXML private void titleClicked(MouseEvent event) { if (event.getClickCount() == 2 && event.getButton() == MouseButton.PRIMARY) maximize(); }
    @FXML private void beginResize(MouseEvent event) { resizeX = event.getScreenX(); resizeY = event.getScreenY(); resizeWidth = stage.getWidth(); resizeHeight = stage.getHeight(); }
    @FXML private void resizeWindow(MouseEvent event) {
        if (!stage.isMaximized() && event.isPrimaryButtonDown()) {
            stage.setWidth(Math.max(stage.getMinWidth(), resizeWidth + event.getScreenX() - resizeX));
            stage.setHeight(Math.max(stage.getMinHeight(), resizeHeight + event.getScreenY() - resizeY));
        }
    }

    public void close() {
        if (closed) return;
        closed = true; bookmarkRequest++; toast.stop(); chromeRefresh.stop(); libraryController.dispose();
        for (Tab tab : tabs) { tab.header().dispose(); tab.page().dispose(); }
        tabs.clear();
        tabHost.getChildren().clear();
        database.close();
    }
}
