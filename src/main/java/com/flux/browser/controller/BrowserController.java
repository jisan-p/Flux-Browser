package com.flux.browser.controller;

import com.flux.browser.db.*;
import com.flux.browser.feature.*;
import com.flux.browser.web.NativeWebPage;
import com.flux.browser.ui.WindowGeometry;
import com.flux.browser.ui.WindowResizeSupport;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
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
    @FXML private StackPane root;
    @FXML private ApplicationMenuController applicationMenuController;
    @FXML private Parent sidebar, easySetup, historyPanel;
    @FXML private HistoryPanelController historyPanelController;
    @FXML private EasySetupController easySetupController;
    @FXML private HBox statusBar;
    @FXML private com.flux.browser.ui.WallpaperCanvas wallpaperCanvas;
    @FXML private com.flux.browser.ui.ChromeContour chromeContour;
    private com.flux.browser.ui.SystemTheme systemTheme;
    private final com.flux.browser.ui.Soundscape soundscape = new com.flux.browser.ui.Soundscape(this::message);
    private final AnimationTimer appearanceRefresh = new AnimationTimer() {
        @Override public void handle(long now) { stop(); applyAppearance(); }
    };
    @FXML private HBox tabHeaders;
    @FXML private StackPane tabHost, contentHost;
    @FXML private BorderPane contentLayout;
    @FXML private ScrollPane tabScroll;
    @FXML private Parent library, settings, features, downloadsPage;
    @FXML private DownloadsController downloadsPageController;
    @FXML private FeaturesController featuresController;
    @FXML private HBox documentBar;
    @FXML private Button newTabButton, workspacesButton;
    @FXML private SidebarController sidebarController;
    @FXML private LibraryController libraryController;
    @FXML private SettingsController settingsController;
    @FXML private TextField addressBar;
    @FXML private Button backButton, forwardButton, reloadButton, stopButton, bookmarkButton, maximizeButton;
    @FXML private Label schemeLabel, databaseStatus, statusText, tabCount, toastText;
    @FXML private ProgressBar loadProgress;
    private final List<Tab> tabs = new ArrayList<>();
    private final IdentityHashMap<Tab, String> workspaces = new IdentityHashMap<>();
    private final ArrayDeque<FeatureStore.SavedTab> recentlyClosed = new ArrayDeque<>();
    private final FeatureStore store = new FeatureStore();
    private final Downloads downloads = new Downloads();
    private FeatureStore.State preferences = new FeatureStore.State();
    private final PauseTransition sessionDelay = new PauseTransition(Duration.seconds(1));
    private boolean sessionReady, restoring, focusMode;
    private int interactionVersion, privacyVersion;
    private final boolean sessionEnabled = Boolean.parseBoolean(System.getProperty("flux.session", "true"));
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
    private double dragX, dragY;
    private WindowResizeSupport windowResize;

    public void configure(Stage stage, DatabaseManager database) {
        this.stage = stage; this.database = database;
        windowResize = new WindowResizeSupport(stage, root);
        applicationMenuController.configure(this);
        historyDAO = new HistoryDAO(database); bookmarkDAO = new BookmarkDAO(database); speedDialDAO = new SpeedDialDAO(database);
        sidebarController.configure(this);
        libraryController.configure(this, bookmarkDAO, historyDAO);
        settingsController.configure(this);
        featuresController.configure(this);
        downloadsPageController.configure(this);
        historyPanelController.configure(this);
        easySetupController.configure(this);
        root.getStylesheets().add(Views.class.getResource("/com/flux/browser/gx.css").toExternalForm());
        wallpaperCanvas.widthProperty().bind(root.widthProperty()); wallpaperCanvas.heightProperty().bind(root.heightProperty());
        chromeContour.widthProperty().bind(root.widthProperty()); chromeContour.heightProperty().bind(root.heightProperty());
        systemTheme = new com.flux.browser.ui.SystemTheme(() -> appearanceRefresh.start());
        root.addEventFilter(javafx.event.ActionEvent.ACTION, e -> { if(e.getTarget() instanceof ButtonBase)soundscape.click(false); });
        addressBar.addEventFilter(KeyEvent.KEY_TYPED, e -> soundscape.click(true));
        applyAppearance();
        sessionDelay.setOnFinished(e -> persistSession());
        if (NativeWebPage.enabled()) NativeWebPage.downloadListener(downloads::update);
        toast.setOnFinished(event -> updateStatus());
        toastText.textProperty().bind(statusText.textProperty());
        toastText.visibleProperty().bind(toast.statusProperty().isEqualTo(Animation.Status.RUNNING).and(statusBar.visibleProperty().not()));
        toastText.managedProperty().bind(toastText.visibleProperty());
        addressBar.focusedProperty().addListener((observable, before, focused) -> {
            addressBar.getParent().pseudoClassStateChanged(PseudoClass.getPseudoClass("focused"), focused);
            if (focused) Platform.runLater(addressBar::selectAll);
            else refreshChrome();
        });
        stage.getScene().addEventFilter(KeyEvent.KEY_PRESSED, this::keyPressed);
        stage.maximizedProperty().addListener((observable, before, after) -> maximizeButton.setAccessibleText(after ? "Restore window" : "Maximize window"));
        stage.iconifiedProperty().addListener((observable, before, after) -> { updateActiveContent(); soundscape.paused(after); });
        root.widthProperty().addListener(o -> chromeRefresh.start());
        createTab();
        reconnect();
        int version = interactionVersion;
        if (!sessionEnabled) { sessionReady = true; applyPrivacy(); }
        else store.load().whenComplete((saved, error) -> Platform.runLater(() -> {
            if (closed) return;
            sessionReady = true;
            if (error != null) { message("Session could not be read; starting with default preferences."); applyPrivacy(); return; }
            preferences = saved;
            applyAppearance(); easySetupController.show();
            applyPrivacy();
            if (saved.restore && !saved.tabs.isEmpty() && version == interactionVersion) restoreSession();
            else { preferences.workspace = "Default"; refreshWorkspaceHeaders(); }
        }));
    }

    private Tab createTab() {
        var page = Views.<WebTabController>load("WebTab");
        var header = Views.<TabHeaderController>load("TabHeader");
        Tab tab = new Tab(page.controller(), page.root(), header.controller(), header.root());
        workspaces.put(tab, preferences.workspace);
        page.controller().configure(this, historyDAO, speedDialDAO);
        header.controller().configure(this, page.controller(), () -> selectTab(tab), () -> closeTab(tab.page()));
        tabs.add(tab); tabHeaders.getChildren().add(tab.chip());
        visible(tab.content(), false);
        tabHost.getChildren().add(tab.content());
        selectTab(tab);
        return tab;
    }

    @FXML public void workspaces() {
        javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();
        menu.getStyleClass().add("browser-menu");
        for (String ws : preferences.workspaces) {
            javafx.scene.control.Menu wsMenu = new javafx.scene.control.Menu(ws);
            for (Tab t : tabs) {
                if (ws.equals(this.workspaces.get(t))) {
                    String title = t.page().titleProperty().get();
                    if (title == null || title.isBlank()) title = "Untitled Tab";
                    if (title.length() > 40) title = title.substring(0, 40) + "...";
                    javafx.scene.control.MenuItem tabItem = new javafx.scene.control.MenuItem(title);
                    tabItem.setOnAction(e -> {
                        switchWorkspace(ws);
                        selectTab(t);
                    });
                    wsMenu.getItems().add(tabItem);
                }
            }
            if (wsMenu.getItems().isEmpty()) {
                javafx.scene.control.MenuItem empty = new javafx.scene.control.MenuItem("No tabs");
                empty.setDisable(true);
                wsMenu.getItems().add(empty);
            }
            menu.getItems().add(wsMenu);
        }
        menu.getItems().add(new javafx.scene.control.SeparatorMenuItem());
        javafx.scene.control.MenuItem manageItem = new javafx.scene.control.MenuItem("Manage Workspaces...");
        manageItem.setOnAction(e -> featureSection("Workspaces"));
        menu.getItems().add(manageItem);
        menu.show(workspacesButton, javafx.geometry.Side.RIGHT, 10, 0);
    }
    public void newWorkspace() {
        String name = com.flux.browser.util.Dialogs.prompt(window(), "New Workspace", "Enter a name for the new workspace:", "");
        if (name != null && !name.isBlank()) {
            try {
                createWorkspace(name.trim());
            } catch (IllegalArgumentException e) {
                message(e.getMessage());
            }
        }
    }
    public void pageAction(BrowserPage source, String payload) {
        if (closed || source != currentPage()) return;
        try {
            var action = com.google.gson.JsonParser.parseString(payload).getAsJsonObject();
            if (!currentUrl().equals(action.get("url").getAsString())) return;
            switch (action.get("action").getAsString()) {
                case "search" -> openNewUrl(PageActions.search(action.get("text").getAsString()));
                case "translate" -> {
                    String language = action.get("language").getAsString();
                    if (language.isEmpty()) language = preferences.translation;
                    String url = PageActions.translation(currentUrl(), language);
                    if (canCreateTab()) { preferences.translation = language; saveSession(); openNewUrl(url); }
                }
                case "reader" -> featuresController.reader();
                case "save" -> { if (source instanceof NativeWebPage nativePage) nativePage.action("download", currentUrl()); }
                default -> { }
            }
        } catch (IllegalArgumentException e) { message(e.getMessage()); }
    }

    @FXML public void newTab() { if (!closed && canCreateTab()) { interactionVersion++; createTab(); focusAddress(); saveSession(); } }
    private boolean canCreateTab() {
        if (focusMode) { message("Turn off Focus mode in Browser tools to open or switch tabs."); return false; }
        if (tabs.size() >= 200) { message("Close a tab before opening another (200 tab limit)."); return false; }
        return true;
    }
    public void newPopupTab(BrowserPage page) {
        if (closed || !canCreateTab()) { page.close(); return; }
        createTab().page().adopt(page);
    }

    /** Cocoa receives keys while WKWebView has focus; route shell shortcuts back to FX. */
    public void nativeShortcut(String key) {
        if (closed) return;
        switch (key) {
            case "developerTools" -> developerTools();
            case "developerConsole" -> developerConsole();
            case "reopen" -> reopenClosedTab();
            case "l" -> focusAddress(); case "t" -> newTab();
            case "w" -> { if (active != null) closeTab(active.page()); }
            case "r" -> reload(); case "d" -> toggleBookmark();
            case "y" -> showLibrary(false); case "bookmarks" -> showLibrary(true);
            case "home" -> home(); case "back" -> back(); case "forward" -> forward(); case "stop" -> stopLoading();
            case "nextTab", "previousTab" -> selectTab(visibleTabs().get(Math.floorMod(visibleTabs().indexOf(active) + (key.equals("nextTab") ? 1 : -1), visibleTabs().size())));
            default -> {
                if (key.matches("[1-9]")) {
                    int index = key.equals("9") ? visibleTabs().size()-1 : Integer.parseInt(key)-1;
                    if (index < visibleTabs().size()) selectTab(visibleTabs().get(index));
                }
            }
        }
    }

    private void selectTab(Tab tab) {
        if (closed || !tabs.contains(tab) || (focusMode && active != null && active != tab)) return;
        preferences.workspace = workspaces.get(tab);
        refreshWorkspaceHeaders();
        if (active != null && active != tab) {
            active.page().setActive(false);
            visible(active.content(), false);
        }
        active = tab;
        chromeContour.select(tab.chip());
        saveSession();
        // Keep the scene association: detaching WebView on every switch recreates rendering state.
        // Hidden tabs are unmanaged, so only the selected page participates in layout and painting.
        visible(tab.content(), true);
        for (Tab other : tabs) other.header().selected(other == tab);
        dismissPanels();
        addressBar.setText(tab.page().isHome() ? "" : tab.page().locationProperty().get());
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
        if (focusMode) { message("Turn off Focus mode before closing its tab."); return; }
        recentlyClosed.addFirst(savedTab(tab));
        while (recentlyClosed.size() > 20) recentlyClosed.removeLast();
        int index = visibleTabs().indexOf(tab);
        boolean wasActive = active == tab;
        tab.header().dispose(); tab.page().dispose();
        tabs.remove(tab); tabHeaders.getChildren().remove(tab.chip()); tabHost.getChildren().remove(tab.content());
        workspaces.remove(tab);
        List<Tab> remaining = visibleTabs();
        if (remaining.isEmpty()) { active = null; createTab(); focusAddress(); }
        else if (wasActive) selectTab(remaining.get(Math.min(Math.max(0,index), remaining.size() - 1)));
        else refreshChrome();
        saveSession();
    }

    public void copyAddress(String url) {
        var content = new ClipboardContent(); content.putString(url); Clipboard.getSystemClipboard().setContent(content);
    }
    public boolean canReopenTab() { return !focusMode && !recentlyClosed.isEmpty(); }
    public boolean hasOtherTabs(WebTabController page, boolean rightOnly) {
        var current = visibleTabs();
        int index = -1;
        for (int i = 0; i < current.size(); i++) if (current.get(i).page() == page) index = i;
        return !focusMode && index >= 0 && (rightOnly ? index < current.size()-1 : current.size() > 1);
    }
    public void closeOtherTabs(WebTabController page, boolean rightOnly) {
        if (focusMode) return;
        var current = List.copyOf(visibleTabs());
        if (current.stream().noneMatch(tab -> tab.page() == page)) return;
        boolean found = false;
        for (var tab : current) {
            if (tab.page() == page) { found = true; continue; }
            if (!rightOnly || found) closeTab(tab.page());
        }
    }

    @FXML private void navigate() { navigateTo(addressBar.getText()); }
    public void navigateTo(String input) {
        if (active == null || closed) return;
        try {
            interactionVersion++;
            String answer = SearchTools.answer(input);
            if (!answer.isEmpty()) { message(answer); return; }
            String address = UrlResolver.resolve(SearchTools.resolve(input, preferences.providers));
            dismissPanels();
            addressBar.setText(address);
            active.page().load(address);
        } catch (IllegalArgumentException error) { message(error.getMessage()); focusAddress(); }
    }

    @FXML public void home() { if (active != null) { dismissPanels(); active.page().home(); refreshChrome(); } }
    @FXML private void back() { if (panelsVisible()) dismissPanels(); else if (active != null) active.page().back(); }
    @FXML private void forward() { dismissPanels(); if (active != null) active.page().forward(); }
    @FXML private void reload() { if (library.isVisible()) libraryController.refresh(); else if (downloadsPage.isVisible()) downloadsPageController.refresh(); else if (active != null) active.page().reload(); }
    @FXML private void stopLoading() { if (active != null) active.page().stop(); }

    public void tabChanged(WebTabController page) { if (closed) return; saveSession(); if (active != null && active.page() == page) chromeRefresh.start(); }
    private void refreshChrome() {
        if (closed || active == null) return;
        WebTabController page = active.page();
        String location = page.locationProperty().get();
        if (!addressBar.isFocused()) addressBar.setText(page.isHome() ? "" : location);
        schemeLabel.setText(page.isHome() ? "⌕" : location.startsWith("https://") ? "HTTPS" : location.startsWith("http://") ? "HTTP" : "WEB");
        backButton.setDisable(!panelsVisible() && !page.canGoBack());
        forwardButton.setDisable(panelsVisible() || !page.canGoForward());
        reloadButton.setDisable(page.loadingProperty().get());
        stopButton.setDisable(!page.loadingProperty().get());
        visible(reloadButton,!page.loadingProperty().get()); visible(stopButton,page.loadingProperty().get());
        loadProgress.setProgress(page.progress());
        int count = visibleTabs().size();
        tabScroll.setPrefViewportWidth(Math.min(Math.max(160,root.getWidth()-230),count*196));
        tabCount.setText(count + (count == 1 ? " TAB" : " TABS") + (preferences.workspaces.size() > 1 ? " · " + preferences.workspace : ""));
        visible(documentBar, !panelsVisible() && currentPage() instanceof NativeWebPage n && n.pdf.get());
        stage.setTitle(page.titleProperty().get() + " · Flux");
        if (!panelsVisible()) sidebarController.select(historyPanel.isVisible() ? "history" : page.isHome() ? "home" : "");
        updateBookmark(false);
        if (toast.getStatus() != Animation.Status.RUNNING) updateStatus();
        applicationMenuController.refresh();
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
        closeHistoryPanel(); closeEasySetup();
        visible(downloadsPage, false); visible(features, false); visible(settings, false); visible(library, true); tabHost.setVisible(false);
        updateActiveContent();
        libraryController.show(bookmarks);
        sidebarController.select(bookmarks ? "bookmarks" : "history");
        refreshChrome();
    }
    @FXML public void showBookmarks() {
        showLibrary(true);
    }
    @FXML public void settings() {
        closeHistoryPanel(); closeEasySetup();
        visible(downloadsPage, false); visible(features, false); visible(library, false); visible(settings, true); tabHost.setVisible(false);
        updateActiveContent();
        settingsController.show(active == null ? 1 : active.page().zoom());
        sidebarController.select("settings");
        refreshChrome();
    }
    public void dismissPanels() {
        closeHistoryPanel(); closeEasySetup();
        visible(downloadsPage, false); visible(features, false); visible(library, false); visible(settings, false); tabHost.setVisible(true);
        updateActiveContent();
        if (active != null) active.page().focus();
        refreshChrome();
    }
    private boolean panelsVisible() { return library.isVisible() || settings.isVisible() || features.isVisible() || downloadsPage.isVisible(); }
    private void updateActiveContent() { if (active != null) active.page().setActive(!panelsVisible() && !stage.isIconified()); }
    private static void visible(Node node, boolean visible) { node.setVisible(visible); node.setManaged(visible); }
    public void setAccent(boolean cyan) {
        preferences.appearance.accent=cyan ? "#00ffff" : "#fa1e4e";
        preferences.appearance.theme=cyan ? "Custom" : "GX Classic";
        applyAppearance(); saveSession();
    }
    @FXML public void easySetup() {
        closeHistoryPanel();
        if (!sessionReady) { message("Loading browser preferences…"); return; }
        if(easySetup.isVisible()) { closeEasySetup(); return; }
        // Home can show a matching overlay. WKWebView needs a separate layout region so Cocoa never covers the controls.
        if(active!=null && active.page().isHome() && !panelsVisible()) {
            contentLayout.setRight(null);contentHost.getChildren().add(easySetup);
            StackPane.setAlignment(easySetup,javafx.geometry.Pos.TOP_RIGHT);
        }
        visible(easySetup,true);easySetupController.show();
    }
    public void openHistoryPanel() {
        dismissPanels();
        contentHost.getChildren().remove(historyPanel); contentLayout.setRight(historyPanel);
        visible(historyPanel,true); historyPanelController.refresh(); sidebarController.select("history");
    }
    public void closeHistoryPanel() {
        boolean showing=historyPanel.isVisible();
        visible(historyPanel,false);
        if(historyPanel.getParent()!=contentHost) { contentLayout.setRight(easySetup); contentHost.getChildren().add(historyPanel); }
        if(showing && active!=null) { refreshChrome(); active.page().focus(); }
    }
    public void closeEasySetup() {
        visible(easySetup,false);
        if(easySetup.getParent()==contentHost) {contentHost.getChildren().remove(easySetup);contentLayout.setRight(easySetup);}
    }
    public void appearanceChanged() { preferences.appearance.normalize(); appearanceRefresh.start(); saveSession(); }
    private void applyAppearance() {
        if(closed)return;
        var a=preferences.appearance;
        boolean light=a.mode.equals("Light") || a.mode.equals("Auto") && systemTheme!=null && systemTheme.light();
        root.getStyleClass().removeAll("cyan-theme","light-theme","compact-layout","spacious-layout","no-element-backgrounds","hide-tile-titles","big-tiles");
        if(a.accent.equals("#00ffff"))root.getStyleClass().add("cyan-theme");
        if(light)root.getStyleClass().add("light-theme");
        if(a.layout.equals("Compact"))root.getStyleClass().add("compact-layout");
        if(a.layout.equals("Spacious"))root.getStyleClass().add("spacious-layout");
        if(!a.elementBackgrounds)root.getStyleClass().add("no-element-backgrounds");
        if(!a.tileTitles)root.getStyleClass().add("hide-tile-titles");
        if(a.bigTiles)root.getStyleClass().add("big-tiles");
        var base=javafx.scene.paint.Color.web(a.base);
        String surface=light ? "rgba(244,240,250,"+a.opacity+")" : String.format(java.util.Locale.ROOT,"rgba(%d,%d,%d,%.3f)",(int)(base.getRed()*255),(int)(base.getGreen()*255),(int)(base.getBlue()*255),a.opacity);
        root.setStyle("-flux-accent:"+a.accent+";-flux-soft:derive("+a.accent+",-72%);-gx-surface:"+surface+";-fx-font-family:'"+a.font+"';-fx-font-size:"+a.fontSize+"px;");
        visible(sidebar,a.sidebar); visible(workspacesButton,a.sidebar); visible(statusBar,a.statusBar);
        chromeContour.accent(a.accent);
        wallpaperCanvas.configure(a,light); soundscape.configure(a);
        for(Tab tab:tabs)tab.page().applyAppearance();
    }
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

    public record MenuState(boolean web, boolean back, boolean forward, boolean loading, boolean bookmarkEnabled,
                            boolean bookmarked, boolean otherTabs, boolean rightTabs, int visibleTabs, int totalTabs, double zoom) { }
    public MenuState menuState() {
        var page = currentPage();
        return new MenuState(!panelsVisible() && page != null && !(page instanceof NativeWebPage n && n.pdf.get()),
            !backButton.isDisable(), !forwardButton.isDisable(), active != null && active.page().loadingProperty().get(),
            !bookmarkButton.isDisable(), bookmarked, active != null && hasOtherTabs(active.page(), false),
            active != null && hasOtherTabs(active.page(), true), visibleTabs().size(), tabs.size(), active == null ? 1 : active.page().zoom());
    }
    public void closeCurrentOtherTabs(boolean rightOnly) { if (active != null) closeOtherTabs(active.page(), rightOnly); }
    public void saveCurrentPage() {
        if (menuState().web() && currentPage() instanceof NativeWebPage page) page.action("download", currentUrl());
    }
    public void about() { settings(); settingsController.selectCategory("About"); }

    private void keyPressed(KeyEvent event) {
        boolean handled = true;
        if (event.getCode() == KeyCode.F12 && !event.isAltDown() && !event.isShortcutDown()) {
            developerTools();
        } else if (event.isShortcutDown() && (event.isAltDown() || event.isShiftDown()) && event.getCode() == KeyCode.I) {
            developerTools();
        } else if (event.isShortcutDown() && event.isAltDown() && event.getCode() == KeyCode.C) {
            developerConsole();
        } else if (event.isControlDown() && event.getCode() == KeyCode.TAB) {
            int next = Math.floorMod(visibleTabs().indexOf(active) + (event.isShiftDown() ? -1 : 1), visibleTabs().size());
            selectTab(visibleTabs().get(next));
        } else if (event.isShortcutDown()) {
            switch (event.getCode()) {
                case L -> focusAddress();
                case T -> { if (event.isShiftDown()) reopenClosedTab(); else newTab(); }
                case W -> { if (active != null) closeTab(active.page()); }
                case R -> reload();
                case D -> toggleBookmark();
                case Y -> showLibrary(false);
                case B -> { if (event.isShiftDown()) showLibrary(true); else handled = false; }
                case DIGIT1, DIGIT2, DIGIT3, DIGIT4, DIGIT5, DIGIT6, DIGIT7, DIGIT8, DIGIT9 -> {
                    int index = event.getCode() == KeyCode.DIGIT9 ? visibleTabs().size() - 1 : event.getCode().getCode() - KeyCode.DIGIT1.getCode();
                    if (index < visibleTabs().size()) selectTab(visibleTabs().get(index));
                }
                default -> handled = false;
            }
        } else if (event.isAltDown()) {
            switch (event.getCode()) {
                case LEFT -> back(); case RIGHT -> forward(); case HOME -> home(); default -> handled = false;
            }
        } else if (event.getCode() == KeyCode.ESCAPE) {
            if (historyPanel.isVisible()) closeHistoryPanel(); else if (easySetup.isVisible()) closeEasySetup(); else if (panelsVisible()) dismissPanels(); else stopLoading();
        } else if (event.getCode() == KeyCode.F5) reload();
        else handled = false;
        if (handled) event.consume();
    }

    @FXML private void minimize() { stage.setIconified(true); }
    @FXML private void maximize() {
        WindowGeometry.minimum(stage, WindowGeometry.screen(stage));
        stage.setMaximized(!stage.isMaximized());
    }
    @FXML private void quit() { close(); stage.close(); Platform.exit(); }
    @FXML private void beginDrag(MouseEvent event) { dragX = event.getScreenX() - stage.getX(); dragY = event.getScreenY() - stage.getY(); }
    @FXML private void dragWindow(MouseEvent event) {
        if (!stage.isMaximized() && event.isPrimaryButtonDown()) { stage.setX(event.getScreenX() - dragX); stage.setY(event.getScreenY() - dragY); }
    }
    @FXML private void titleClicked(MouseEvent event) { if (event.getClickCount() == 2 && event.getButton() == MouseButton.PRIMARY) maximize(); }
    @FXML private void beginResize(MouseEvent event) { windowResize.beginCorner(event); }
    @FXML private void resizeWindow(MouseEvent event) { windowResize.drag(event); }

    public FeatureStore.State preferences() { return preferences; }
    public Downloads downloads() { return downloads; }
    public Path profileDirectory() { return store.directory(); }
    public HistoryDAO history() { return historyDAO; }
    public String currentUrl() { return active == null ? "" : active.page().locationProperty().get(); }
    public BrowserPage currentPage() { return active == null || active.page().isHome() ? null : active.page().existingPage(); }
    public boolean focusMode() { return focusMode; }
    public void setFocusMode(boolean value) { focusMode = value; newTabButton.setDisable(value); refreshWorkspaceHeaders(); applicationMenuController.refresh(); }
    private List<Tab> visibleTabs() { return tabs.stream().filter(t -> preferences.workspace.equals(workspaces.get(t))).toList(); }
    private void refreshWorkspaceHeaders() { for (Tab t : tabs) visible(t.chip(), preferences.workspace.equals(workspaces.get(t)) && (!focusMode || t == active)); }
    public void createWorkspace(String name) {
        name = name.trim();
        if (name.isBlank() || name.length() > 40 || preferences.workspaces.contains(name) || preferences.workspaces.size() >= 50)
            throw new IllegalArgumentException("Use a unique workspace name of 1–40 characters (up to 50 workspaces).");
        if (!canCreateTab()) return;
        preferences.workspaces.add(name); switchWorkspace(name);
    }
    public void switchWorkspace(String name) {
        if (focusMode || !preferences.workspaces.contains(name)) return;
        interactionVersion++; preferences.workspace = name;
        List<Tab> candidates = visibleTabs();
        if (candidates.isEmpty()) createTab(); else selectTab(candidates.getFirst());
        refreshWorkspaceHeaders(); saveSession();
    }
    public void moveCurrentTab(String target) {
        if (active == null || focusMode || !preferences.workspaces.contains(target)) return;
        workspaces.put(active, target); preferences.workspace = target; refreshWorkspaceHeaders(); saveSession(); refreshChrome();
    }
    public void deleteWorkspace(String name) {
        if (focusMode || "Default".equals(name) || !preferences.workspaces.contains(name)) return;
        workspaces.replaceAll((t,w) -> name.equals(w) ? "Default" : w);
        preferences.workspaces.remove(name); switchWorkspace("Default");
    }
    public void reopenClosedTab() {
        if (recentlyClosed.isEmpty() || !canCreateTab()) return;
        var saved = recentlyClosed.removeFirst();
        createTab().page().restore(saved.url(), saved.title(), saved.zoom()); saveSession();
    }
    public void openNewUrl(String url) { if (closed || !canCreateTab()) return; createTab(); navigateTo(url); }
    public void openPdf(Path path) {
        if (!NativeWebPage.enabled()) { message("PDF viewer requires native macOS WebKit."); return; }
        if (!canCreateTab()) return;
        createTab().page().openPdf(path); saveSession();
    }
    @FXML public void developerTools() { inspectPage("toggle"); }
    public void showDeveloperTools() { inspectPage("show"); }
    public void developerConsole() { inspectPage("console"); }
    private void inspectPage(String action) {
        if (closed) return;
        BrowserPage page = currentPage();
        if (page == null || page instanceof NativeWebPage n && n.pdf.get()) { message("Open a web page before using Developer Tools."); return; }
        dismissPanels();
        page.developerTools(action).whenComplete((result,error) -> Platform.runLater(() -> {
            if (closed) return;
            message(error == null ? result : "Developer Tools could not open for this tab. Try again after the page finishes loading.");
        }));
    }
    public void showDownloads() {
        closeHistoryPanel(); closeEasySetup(); visible(library,false); visible(settings,false); visible(features,false); visible(downloadsPage,true); tabHost.setVisible(false);
        updateActiveContent(); downloadsPageController.show(); sidebarController.select("downloads"); refreshChrome();
    }
    public void featureSection(String name) { if ("Downloads".equals(name)) showDownloads(); else { features(); featuresController.selectSection(name); } }
    @FXML public void features() {
        closeHistoryPanel(); closeEasySetup();
        if (!sessionReady) { message("Loading browser preferences…"); return; }
        visible(downloadsPage, false); visible(library, false); visible(settings, false); visible(features, true); tabHost.setVisible(false);
        updateActiveContent(); featuresController.show(); refreshChrome();
    }
    @FXML private void pdfPrevious() { pdfAction("pdfPrevious"); }
    @FXML private void pdfNext() { pdfAction("pdfNext"); }
    @FXML private void pdfZoomIn() { pdfAction("pdfZoomIn"); }
    @FXML private void pdfZoomOut() { pdfAction("pdfZoomOut"); }
    @FXML private void pdfDownload() { if (currentPage() instanceof NativeWebPage n) n.action("download", currentUrl()); }
    private void pdfAction(String action) { if (currentPage() instanceof NativeWebPage n) n.action(action, ""); }
    public void saveSession() { if (!closed && sessionReady && !restoring && sessionEnabled) sessionDelay.playFromStart(); }
    private FeatureStore.SavedTab savedTab(Tab t) { return new FeatureStore.SavedTab(workspaces.get(t), t.page().locationProperty().get(), t.page().titleProperty().get(), t.page().zoom()); }
    private void persistSession() {
        if (closed || !sessionReady || !sessionEnabled || restoring) return;
        preferences.tabs = new ArrayList<>(tabs.stream().map(this::savedTab).toList());
        preferences.selectedTab = Math.max(0, tabs.indexOf(active));
        store.save(preferences).whenComplete((v,e) -> { if (e != null) Platform.runLater(() -> message("Could not save the browsing session.")); });
    }
    private void restoreSession() {
        restoring = true;
        var saved = List.copyOf(preferences.tabs); int selected = preferences.selectedTab;
        for (Tab t : tabs) { t.header().dispose(); t.page().dispose(); }
        tabs.clear(); workspaces.clear(); tabHost.getChildren().clear(); tabHeaders.getChildren().clear(); active = null;
        for (var entry : saved) {
            preferences.workspace = entry.workspace();
            Tab t = createTab(); t.page().restoreDeferred(entry.url(), entry.title(), entry.zoom());
        }
        active.page().setActive(false); visible(active.content(), false); active = null;
        selectTab(tabs.get(Math.clamp(selected, 0, tabs.size()-1)));
        restoring = false; saveSession();
    }
    public void applyPrivacy() {
        if (!NativeWebPage.enabled()) return;
        int version = ++privacyVersion;
        boolean block = preferences.blocker, secure = preferences.https, phishing = preferences.phishing;
        var exceptions = List.copyOf(preferences.allowedSites);
        store.rules(block, exceptions).whenComplete((rules,e) -> Platform.runLater(() -> {
            if (closed || version != privacyVersion) return;
            if (e != null) { message("Could not read blocking rules; existing rules remain active."); return; }
            NativeWebPage.configurePrivacy(rules, secure, phishing).whenComplete((v,error) -> Platform.runLater(() -> {
                if (error != null && !closed && version == privacyVersion) message("Blocking rules could not be applied. Check Browser tools and retry.");
            }));
        }));
    }
    public void pageLoaded(WebTabController tab) {
        if (!preferences.fullText || !storageAvailable() || !UrlResolver.isWeb(tab.locationProperty().get())) return;
        String url = tab.locationProperty().get(), title = tab.titleProperty().get(); BrowserPage page = tab.existingPage();
        long generation = historyDAO.generation();
        page.evaluate(PageText.INDEX).whenComplete((text,e) -> Platform.runLater(() -> {
            if (closed || e != null || generation != historyDAO.generation() || !preferences.fullText || tab.existingPage() != page || !url.equals(tab.locationProperty().get()) || text == null || text.isBlank()) return;
            try { perform("", historyDAO.index(title,url,text), v -> {}); } catch (IllegalArgumentException ignored) { }
        }));
    }

    public void close() {
        if (closed) return;
        applicationMenuController.dispose();
        persistSession(); sessionDelay.stop(); appearanceRefresh.stop(); easySetupController.close(); wallpaperCanvas.dispose(); chromeContour.dispose(); soundscape.close(); if(systemTheme!=null)systemTheme.close(); featuresController.close(); store.close();
        if (NativeWebPage.enabled()) NativeWebPage.shutdownServices();
        closed = true; bookmarkRequest++; toast.stop(); chromeRefresh.stop(); libraryController.dispose(); downloadsPageController.dispose(); historyPanelController.dispose();
        for (Tab tab : tabs) { tab.header().dispose(); tab.page().dispose(); }
        tabs.clear();
        tabHost.getChildren().clear();
        database.close();
    }
}
