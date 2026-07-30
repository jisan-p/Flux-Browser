package org.custombrowser.ui;

import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;

import org.custombrowser.navigation.NavigationResolver;
import org.custombrowser.ui.component.EasySetupController;
import org.custombrowser.ui.component.NavigationBarController;
import org.custombrowser.ui.component.SidebarController;
import org.custombrowser.ui.component.SidebarPanelController;
import org.custombrowser.ui.component.StartPageController;
import org.custombrowser.ui.component.TabStripController;
import org.custombrowser.ui.state.BrowserUiState;
import org.custombrowser.ui.state.BrowserUiState.Accent;
import org.custombrowser.ui.state.BrowserUiState.SidebarPanel;

import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebHistory;
import javafx.scene.web.WebView;

public final class BrowserController implements Initializable, BrowserActions {

    private static final double MIN_ZOOM = 0.5;
    private static final double MAX_ZOOM = 2.0;
    private static final double ZOOM_STEP = 0.1;
    private static final double DOCKED_PANEL_WIDTH = 320.0;
    private static final List<String> ACCENT_CLASSES =
            List.of("accent-red", "accent-cyan", "accent-purple", "accent-green");

    private final NavigationResolver navigationResolver;
    private final BrowserUiState uiState;

    @FXML
    private StackPane browserRoot;

    @FXML
    private StackPane contentSurface;

    @FXML
    private WebView webView;

    @FXML
    private StackPane startPage;

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

    private WebEngine engine;
    private WebHistory history;
    private boolean showingStartPage = true;

    public BrowserController(
            NavigationResolver navigationResolver,
            BrowserUiState uiState) {
        this.navigationResolver = Objects.requireNonNull(
                navigationResolver, "navigationResolver");
        this.uiState = Objects.requireNonNull(uiState, "uiState");
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        engine = webView.getEngine();
        history = engine.getHistory();

        navigationBarController.setActions(this);
        tabStripController.setActions(this);
        sidebarController.setActions(this);
        startPageController.setActions(this);

        engine.locationProperty().addListener(
                (observable, oldUrl, newUrl) ->
                        navigationBarController.setAddress(newUrl));
        engine.titleProperty().addListener(
                (observable, oldTitle, newTitle) ->
                        tabStripController.setTitle(newTitle));
        engine.getLoadWorker().runningProperty().addListener(
                (observable, oldLoading, loading) -> updateNavigationState());
        history.currentIndexProperty().addListener(
                (observable, oldIndex, newIndex) -> updateNavigationState());
        history.getEntries().addListener(
                (ListChangeListener<WebHistory.Entry>) change ->
                        updateNavigationState());

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
        showStartPage();
    }

    @Override
    public void navigate(String input) {
        try {
            String address = navigationResolver.resolve(input).uri().toString();
            showWebView();
            engine.load(address);
        } catch (IllegalArgumentException ignored) {
            navigationBarController.focusAddress();
        }
    }

    @Override
    public void showStartPage() {
        showingStartPage = true;
        webView.setVisible(false);
        webView.setManaged(false);
        startPage.setVisible(true);
        startPage.setManaged(true);
        navigationBarController.setAddress("");
        tabStripController.setTitle("Start Page");
        updateNavigationState();
    }

    @Override
    public void goBack() {
        int index = history.getCurrentIndex();
        if (!showingStartPage && index > 0) {
            history.go(-1);
        }
    }

    @Override
    public void goForward() {
        int index = history.getCurrentIndex();
        if (!showingStartPage && index < history.getEntries().size() - 1) {
            history.go(1);
        }
    }

    @Override
    public void reloadOrStop() {
        if (showingStartPage) {
            return;
        }
        if (engine.getLoadWorker().isRunning()) {
            engine.getLoadWorker().cancel();
        } else {
            engine.reload();
        }
    }

    @Override
    public void toggleEasySetup() {
        uiState.easySetupOpenProperty().set(
                !uiState.easySetupOpenProperty().get());
    }

    public void focusAddress() {
        navigationBarController.focusAddress();
    }

    public void zoomIn() {
        webView.setZoom(Math.min(MAX_ZOOM, webView.getZoom() + ZOOM_STEP));
    }

    public void zoomOut() {
        webView.setZoom(Math.max(MIN_ZOOM, webView.getZoom() - ZOOM_STEP));
    }

    public void resetZoom() {
        webView.setZoom(1.0);
    }

    private void showWebView() {
        showingStartPage = false;
        startPage.setVisible(false);
        startPage.setManaged(false);
        webView.setManaged(true);
        webView.setVisible(true);
        updateNavigationState();
    }

    private void updateNavigationState() {
        int index = history.getCurrentIndex();
        boolean canGoBack = !showingStartPage && index > 0;
        boolean canGoForward = !showingStartPage
                && index >= 0
                && index < history.getEntries().size() - 1;
        navigationBarController.setPageState(
                canGoBack,
                canGoForward,
                !showingStartPage && engine.getLoadWorker().isRunning());
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
}
