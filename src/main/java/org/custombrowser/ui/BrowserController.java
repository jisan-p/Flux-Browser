package org.custombrowser.ui;

import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Stream;

import org.custombrowser.browser.BrowserTab;
import org.custombrowser.browser.FaviconService;
import org.custombrowser.browser.PopupPolicyService;
import org.custombrowser.browser.TabManager;
import org.custombrowser.browser.TabManager.TabState;
import org.custombrowser.navigation.NavigationResolver;
import org.custombrowser.download.DownloadDetector;
import org.custombrowser.download.DownloadManager;
import org.custombrowser.diagnostics.PerformanceTracker;
import org.custombrowser.gx.TabSuspensionService;
import org.custombrowser.navigation.NavigationResolver.NavigationTarget;
import org.custombrowser.navigation.NavigationResolver.NavigationType;
import org.custombrowser.persistence.PersistenceModels.BrowserSession;
import org.custombrowser.persistence.PersistenceModels.StoredTab;
import org.custombrowser.persistence.PersistenceService;
import org.custombrowser.ui.component.EasySetupController;
import org.custombrowser.ui.component.ErrorPageController;
import org.custombrowser.ui.component.FindBarController;
import org.custombrowser.ui.component.NavigationBarController;
import org.custombrowser.ui.component.SidebarController;
import org.custombrowser.ui.component.SidebarPanelController;
import org.custombrowser.ui.component.StartPageController;
import org.custombrowser.ui.component.TabStripController;
import org.custombrowser.ui.state.BrowserUiState;
import org.custombrowser.ui.state.BrowserUiState.Accent;
import org.custombrowser.ui.state.BrowserUiState.SidebarPanel;

import javafx.application.HostServices;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.InvalidationListener;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public final class BrowserController
        implements Initializable, BrowserActions, AutoCloseable {

    private static final double MIN_ZOOM = 0.5;
    private static final double MAX_ZOOM = 2.0;
    private static final double ZOOM_STEP = 0.1;
    private static final double DOCKED_PANEL_WIDTH = 320.0;
    private static final List<String> ACCENT_CLASSES =
            List.of(
                    "accent-red",
                    "accent-cyan",
                    "accent-purple",
                    "accent-green",
                    "accent-orange",
                    "accent-blue");

    private final NavigationResolver navigationResolver;
    private final BrowserUiState uiState;
    private final TabManager tabManager;
    private final PersistenceService persistenceService;
    private final DownloadManager downloadManager;
    private final PopupPolicyService popupPolicyService;
    private final TabSuspensionService suspensionService;
    private final Timeline autoSuspendTimer;
    private final Set<String> persistentNavigationSuggestions =
            new LinkedHashSet<>();
    private final Map<BrowserTab, TabListeners> installedTabListeners =
            new HashMap<>();

    @FXML
    private StackPane browserRoot;

    @FXML
    private StackPane contentSurface;

    @FXML
    private StackPane webViewHost;

    @FXML
    private StackPane startPage;

    @FXML
    private VBox errorPage;

    @FXML
    private HBox findBar;

    @FXML
    private VBox sidebarPanel;

    @FXML
    private VBox easySetup;

    @FXML
    private NavigationBarController navigationBarController;

    @FXML
    private TabStripController tabStripController;

    @FXML
    private SidebarController sidebarController;

    @FXML
    private SidebarPanelController sidebarPanelController;

    @FXML
    private StartPageController startPageController;

    @FXML
    private EasySetupController easySetupController;

    @FXML
    private FindBarController findBarController;

    @FXML
    private ErrorPageController errorPageController;

    private HostServices hostServices;

    public BrowserController(
            NavigationResolver navigationResolver,
            BrowserUiState uiState,
            FaviconService faviconService,
            PersistenceService persistenceService,
            DownloadManager downloadManager,
            PopupPolicyService popupPolicyService,
            PerformanceTracker performanceTracker) {
        this.navigationResolver = Objects.requireNonNull(
                navigationResolver, "navigationResolver");
        this.uiState = Objects.requireNonNull(uiState, "uiState");
        this.persistenceService = Objects.requireNonNull(
                persistenceService, "persistenceService");
        this.downloadManager = Objects.requireNonNull(
                downloadManager, "downloadManager");
        this.popupPolicyService = Objects.requireNonNull(
                popupPolicyService, "popupPolicyService");
        tabManager = new TabManager(
                Objects.requireNonNull(faviconService, "faviconService"),
                this::requestExternalNavigation,
                this::allowPopup,
                Objects.requireNonNull(
                        performanceTracker, "performanceTracker"));
        suspensionService = new TabSuspensionService(tabManager);
        autoSuspendTimer = new Timeline(new KeyFrame(
                Duration.seconds(15),
                event -> runAutoSuspension()));
        autoSuspendTimer.setCycleCount(Timeline.INDEFINITE);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        navigationBarController.setActions(this);
        tabStripController.setActions(this);
        tabStripController.setTabManager(tabManager);
        sidebarController.setActions(this);
        sidebarPanelController.setActions(this);
        sidebarPanelController.setTabManager(tabManager);
        startPageController.setActions(this);
        findBarController.setActions(this);
        errorPageController.setActions(this);

        tabManager.tabs().addListener(
                (ListChangeListener<BrowserTab>) this::tabsChanged);
        tabManager.activeTabProperty().addListener(
                (observable, oldTab, newTab) -> {
                    displayActiveTab();
                    persistSession();
                });

        uiState.activeSidebarPanelProperty().addListener(
                (observable, oldPanel, newPanel) -> applySidebarPanel());
        uiState.panelDockedProperty().addListener(
                (observable, wasDocked, docked) -> applySidebarPanel());
        uiState.sidebarVisibleProperty().addListener(
                (observable, wasVisible, visible) -> {
                    if (!visible) {
                        uiState.activeSidebarPanelProperty().set(SidebarPanel.NONE);
                    }
                });
        uiState.easySetupOpenProperty().addListener(
                (observable, wasOpen, open) -> applyEasySetupVisibility());
        uiState.accentProperty().addListener(
                (observable, oldAccent, accent) -> applyAccent(accent));
        uiState.reducedMotionProperty().addListener(
                (observable, wasReduced, reduced) -> applyReducedMotion(reduced));
        uiState.uiScaleProperty().addListener(
                (observable, oldScale, scale) -> applyUiScale(scale.doubleValue()));

        applyAccent(uiState.accentProperty().get());
        applyReducedMotion(uiState.reducedMotionProperty().get());
        applyUiScale(uiState.uiScaleProperty().get());
        applySidebarPanel();
        applyEasySetupVisibility();
        hideFindBar();
        restoreSession();
        loadPersistentNavigationSuggestions();
        autoSuspendTimer.play();
    }

    public void setHostServices(HostServices hostServices) {
        this.hostServices = hostServices;
    }

    @Override
    public void navigate(String input) {
        try {
            NavigationTarget target = navigationResolver.resolve(input);
            if (target.type() == NavigationType.EXTERNAL) {
                requestExternalNavigation(target.uri());
                return;
            }
            if (DownloadDetector.isLikelyDownload(target.uri())) {
                requestDownload(target.uri());
                return;
            }
            activeTab().navigate(target.uri());
        } catch (IllegalArgumentException ignored) {
            navigationBarController.focusAddress();
        }
    }

    @Override
    public void showStartPage() {
        activeTab().showStartPage();
        displayActiveTab();
    }

    @Override
    public void goBack() {
        activeTab().goBack();
    }

    @Override
    public void goForward() {
        activeTab().goForward();
    }

    @Override
    public void reloadOrStop() {
        if (!activeTab().startPageProperty().get()) {
            activeTab().reloadOrStop();
        }
    }

    @Override
    public void toggleEasySetup() {
        uiState.easySetupOpenProperty().set(
                !uiState.easySetupOpenProperty().get());
    }

    @Override
    public void newTab() {
        BrowserTab tab = tabManager.createTab();
        installActiveStateListener(tab);
        displayActiveTab();
    }

    @Override
    public void closeActiveTab() {
        tabManager.close(activeTab());
    }

    @Override
    public void reopenClosedTab() {
        tabManager.reopenClosedTab().ifPresent(tab -> {
            installActiveStateListener(tab);
            displayActiveTab();
        });
    }

    @Override
    public void showFindBar() {
        findBar.setVisible(true);
        findBar.setManaged(true);
        findBarController.focusQuery();
    }

    @Override
    public void hideFindBar() {
        findBar.setVisible(false);
        findBar.setManaged(false);
    }

    @Override
    public boolean findInPage(
            String query,
            boolean backwards,
            boolean matchCase) {
        return activeTab().find(query, backwards, matchCase);
    }

    @Override
    public void copyAddress() {
        String address = activeTab().locationProperty().get();
        if (address == null || address.isBlank()) {
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(address);
        Clipboard.getSystemClipboard().setContent(content);
    }

    @Override
    public void openCurrentPageExternally() {
        URI address = activeAddress();
        if (address != null && hostServices != null) {
            hostServices.showDocument(address.toString());
        }
    }

    @Override
    public void printCurrentPage() {
        BrowserTab tab = activeTab();
        if (!tab.startPageProperty().get()) {
            tab.print();
        }
    }

    @Override
    public void bookmarkCurrentPage() {
        BrowserTab tab = activeTab();
        if (tab.startPageProperty().get()
                || tab.locationProperty().get() == null
                || tab.locationProperty().get().isBlank()) {
            return;
        }
        persistenceService.addBookmark(
                        tab.titleProperty().get(),
                        tab.locationProperty().get())
                .thenRun(() -> javafx.application.Platform.runLater(() -> {
                    persistentNavigationSuggestions.add(
                            tab.locationProperty().get());
                    navigationBarController.setSuggestions(
                            navigationSuggestions());
                    sidebarPanelController.refreshBookmarks();
                }));
    }

    public void focusAddress() {
        navigationBarController.focusAddress();
    }

    public void zoomIn() {
        BrowserTab tab = activeTab();
        tab.setZoom(Math.min(MAX_ZOOM, tab.zoom() + ZOOM_STEP));
    }

    public void zoomOut() {
        BrowserTab tab = activeTab();
        tab.setZoom(Math.max(MIN_ZOOM, tab.zoom() - ZOOM_STEP));
    }

    public void resetZoom() {
        activeTab().setZoom(1.0);
    }

    public void selectNextTab(boolean backwards) {
        tabManager.selectRelative(backwards ? -1 : 1);
    }

    public void selectTabNumber(int oneBasedNumber) {
        tabManager.selectIndex(oneBasedNumber == 9
                ? tabManager.tabs().size() - 1
                : oneBasedNumber - 1);
    }

    public void closeTransientPanels() {
        hideFindBar();
        uiState.easySetupOpenProperty().set(false);
    }

    public void showSidebarPanel(SidebarPanel panel) {
        uiState.sidebarVisibleProperty().set(true);
        uiState.activeSidebarPanelProperty().set(panel);
    }

    TabManager tabManagerForTesting() {
        return tabManager;
    }

    int loadedSidebarPanelCountForTesting() {
        return sidebarPanelController.loadedPanelCount();
    }

    @Override
    public void close() {
        autoSuspendTimer.stop();
        sidebarPanelController.close();
        persistenceService.saveSessionNow(sessionSnapshot());
        List.copyOf(installedTabListeners.keySet())
                .forEach(this::removeActiveStateListeners);
        tabManager.close();
    }

    private void runAutoSuspension() {
        if (!uiState.autoSuspendEnabledProperty().get()) {
            return;
        }
        suspensionService.suspendInactive(java.time.Duration.ofMinutes(
                uiState.autoSuspendMinutesProperty().get()));
    }

    private void tabsChanged(ListChangeListener.Change<? extends BrowserTab> change) {
        while (change.next()) {
            if (change.wasRemoved()) {
                change.getRemoved().forEach(this::removeActiveStateListeners);
            }
            if (change.wasAdded()) {
                change.getAddedSubList().forEach(this::installActiveStateListener);
            }
        }
        persistSession();
    }

    private void installActiveStateListener(BrowserTab tab) {
        tab.setVisitHandler((title, url) -> {
            persistenceService.recordVisit(title, url);
            persistentNavigationSuggestions.add(url);
            if (tab == tabManager.activeTab()) {
                navigationBarController.setSuggestions(
                        navigationSuggestions());
            }
        });
        tab.setDownloadHandler(this::requestDownload);
        if (installedTabListeners.containsKey(tab)) {
            return;
        }
        InvalidationListener displayListener = observable -> {
            if (tab == tabManager.activeTab()) {
                displayActiveTab();
            }
        };
        InvalidationListener persistenceListener = observable -> persistSession();
        installedTabListeners.put(
                tab, new TabListeners(displayListener, persistenceListener));
        tab.locationProperty().addListener(displayListener);
        tab.loadingProperty().addListener(displayListener);
        tab.progressProperty().addListener(displayListener);
        tab.startPageProperty().addListener(displayListener);
        tab.suspendedProperty().addListener(displayListener);
        tab.failureMessageProperty().addListener(displayListener);
        tab.canGoBackProperty().addListener(displayListener);
        tab.canGoForwardProperty().addListener(displayListener);
        tab.titleProperty().addListener(persistenceListener);
        tab.locationProperty().addListener(persistenceListener);
        tab.pinnedProperty().addListener(persistenceListener);
        tab.startPageProperty().addListener(persistenceListener);
        tab.zoomProperty().addListener(persistenceListener);
    }

    private void removeActiveStateListeners(BrowserTab tab) {
        TabListeners listeners = installedTabListeners.remove(tab);
        if (listeners == null) {
            return;
        }
        InvalidationListener displayListener = listeners.displayListener();
        InvalidationListener persistenceListener =
                listeners.persistenceListener();
        tab.locationProperty().removeListener(displayListener);
        tab.loadingProperty().removeListener(displayListener);
        tab.progressProperty().removeListener(displayListener);
        tab.startPageProperty().removeListener(displayListener);
        tab.suspendedProperty().removeListener(displayListener);
        tab.failureMessageProperty().removeListener(displayListener);
        tab.canGoBackProperty().removeListener(displayListener);
        tab.canGoForwardProperty().removeListener(displayListener);
        tab.titleProperty().removeListener(persistenceListener);
        tab.locationProperty().removeListener(persistenceListener);
        tab.pinnedProperty().removeListener(persistenceListener);
        tab.startPageProperty().removeListener(persistenceListener);
        tab.zoomProperty().removeListener(persistenceListener);
    }

    private void displayActiveTab() {
        BrowserTab tab = tabManager.activeTab();
        if (tab == null) {
            return;
        }
        tab.activate();

        boolean onStartPage = tab.startPageProperty().get();
        webViewHost.getChildren().setAll(onStartPage
                ? List.of()
                : List.of(tab.webView()));
        webViewHost.setVisible(!onStartPage);
        webViewHost.setManaged(!onStartPage);
        startPage.setVisible(onStartPage);
        startPage.setManaged(onStartPage);

        String failure = tab.failureMessageProperty().get();
        boolean failed = !onStartPage && failure != null;
        errorPage.setVisible(failed);
        errorPage.setManaged(failed);
        if (failed) {
            errorPageController.showFailure(
                    tab.locationProperty().get(),
                    failure,
                    tab::retry);
        }

        navigationBarController.setAddress(
                onStartPage ? "" : tab.locationProperty().get(),
                true);
        navigationBarController.setPageState(
                !onStartPage && tab.canGoBackProperty().get(),
                !onStartPage && tab.canGoForwardProperty().get(),
                !onStartPage && tab.loadingProperty().get(),
                tab.progressProperty().get());
        navigationBarController.setSuggestions(navigationSuggestions());
    }

    private void restoreSession() {
        BrowserSession session = persistenceService.startupState().session();
        tabManager.restoreSession(
                session.openTabs().stream()
                        .map(this::toTabState)
                        .toList(),
                session.recentlyClosed().stream()
                        .map(this::toTabState)
                        .toList());
        if (tabManager.tabs().isEmpty()) {
            newTab();
        }
    }

    private TabState toTabState(StoredTab tab) {
        return new TabState(
                tab.id(),
                tab.url(),
                tab.title(),
                tab.pinned(),
                tab.selected(),
                tab.zoom(),
                tab.startPage());
    }

    private BrowserSession sessionSnapshot() {
        return new BrowserSession(
                tabManager.snapshot().stream()
                        .map(this::toStoredTab)
                        .toList(),
                tabManager.recentlyClosedSnapshot().stream()
                        .map(this::toStoredTab)
                        .toList());
    }

    private StoredTab toStoredTab(TabState tab) {
        return new StoredTab(
                tab.id(),
                tab.address(),
                tab.title(),
                tab.pinned(),
                tab.selected(),
                tab.zoom(),
                tab.startPage());
    }

    private void persistSession() {
        if (!tabManager.tabs().isEmpty()) {
            persistenceService.saveSession(sessionSnapshot());
        }
    }

    private List<String> navigationSuggestions() {
        Stream<String> speedDials = uiState.speedDials().stream()
                .map(entry -> entry.address());
        Stream<String> history = tabManager.tabs().stream()
                .flatMap(tab -> tab.navigationAddresses().stream());
        return Stream.concat(
                        Stream.concat(speedDials, history),
                        persistentNavigationSuggestions.stream())
                .filter(address -> address != null && !address.isBlank())
                .distinct()
                .toList();
    }

    private void loadPersistentNavigationSuggestions() {
        persistenceService.bookmarks("")
                .thenCombine(
                        persistenceService.visits(""),
                        (bookmarks, visits) -> Stream.concat(
                                        bookmarks.stream()
                                                .map(bookmark -> bookmark.url()),
                                        visits.stream()
                                                .map(visit -> visit.url()))
                                .toList())
                .thenAccept(addresses -> javafx.application.Platform.runLater(() -> {
                    persistentNavigationSuggestions.addAll(addresses);
                    navigationBarController.setSuggestions(
                            navigationSuggestions());
                }));
    }

    private void requestExternalNavigation(URI uri) {
        if (hostServices == null) {
            return;
        }
        ButtonType open = new ButtonType("Open application", ButtonBar.ButtonData.OK_DONE);
        Alert confirmation = new Alert(
                Alert.AlertType.CONFIRMATION,
                "Flux wants to open an external application for:\n" + uri,
                open,
                ButtonType.CANCEL);
        confirmation.setTitle("Open external application?");
        confirmation.setHeaderText("Leave Flux Browser");
        if (browserRoot.getScene() != null) {
            confirmation.initOwner(browserRoot.getScene().getWindow());
        }
        confirmation.showAndWait()
                .filter(open::equals)
                .ifPresent(button -> hostServices.showDocument(uri.toString()));
    }

    private void requestDownload(URI uri) {
        javafx.stage.Window owner = browserRoot.getScene() == null
                ? null
                : browserRoot.getScene().getWindow();
        if (downloadManager.chooseAndStart(uri, owner)) {
            showSidebarPanel(SidebarPanel.DOWNLOADS);
        }
    }

    private boolean allowPopup(BrowserTab sourceTab) {
        URI origin = null;
        try {
            String location = sourceTab.locationProperty().get();
            if (location != null && !location.isBlank()) {
                origin = URI.create(location);
            }
        } catch (IllegalArgumentException ignored) {
        }
        javafx.stage.Window owner = browserRoot.getScene() == null
                ? null
                : browserRoot.getScene().getWindow();
        return popupPolicyService.allowPopup(origin, owner);
    }

    private URI activeAddress() {
        String rawAddress = activeTab().locationProperty().get();
        if (rawAddress == null || rawAddress.isBlank()) {
            return null;
        }
        try {
            return URI.create(rawAddress);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private BrowserTab activeTab() {
        BrowserTab tab = tabManager.activeTab();
        if (tab == null) {
            tab = tabManager.createTab();
        }
        return tab;
    }

    private void applySidebarPanel() {
        boolean open = uiState.activeSidebarPanelProperty().get()
                != SidebarPanel.NONE;
        sidebarPanel.setVisible(open);
        sidebarPanel.setManaged(open);
        boolean reserveSpace = open && uiState.panelDockedProperty().get();
        contentSurface.setPadding(reserveSpace
                ? new Insets(0, 0, 0, DOCKED_PANEL_WIDTH)
                : Insets.EMPTY);
    }

    private void applyEasySetupVisibility() {
        boolean open = uiState.easySetupOpenProperty().get();
        easySetup.setVisible(open);
        easySetup.setManaged(open);
    }

    private void applyAccent(Accent accent) {
        browserRoot.getStyleClass().removeAll(ACCENT_CLASSES);
        browserRoot.getStyleClass().add(accent.styleClass());
    }

    private void applyReducedMotion(boolean reduced) {
        browserRoot.getStyleClass().remove("reduced-motion");
        if (reduced) {
            browserRoot.getStyleClass().add("reduced-motion");
        }
    }

    private void applyUiScale(double scale) {
        double clamped = Math.max(11.0, Math.min(16.0, scale));
        browserRoot.setStyle("-fx-font-size: %.1fpx;".formatted(clamped));
    }

    private record TabListeners(
            InvalidationListener displayListener,
            InvalidationListener persistenceListener) {
    }
}
