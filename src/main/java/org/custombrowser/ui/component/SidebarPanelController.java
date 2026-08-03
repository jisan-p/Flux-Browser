package org.custombrowser.ui.component;

import java.io.IOException;
import java.net.URL;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import org.custombrowser.browser.FaviconService;
import org.custombrowser.browser.TabManager;
import org.custombrowser.download.DownloadManager;
import org.custombrowser.gx.ResourceMonitor;
import org.custombrowser.persistence.PersistenceService;
import org.custombrowser.settings.BrowsingDataService;
import org.custombrowser.ui.BrowserActions;
import org.custombrowser.ui.state.BrowserUiState;
import org.custombrowser.ui.state.BrowserUiState.SidebarPanel;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.StackPane;

/** Hosts and lazily loads the selected sidebar panel FXML document. */
public final class SidebarPanelController {

    private final BrowserUiState uiState;
    private final PersistenceService persistenceService;
    private final DownloadManager downloadManager;
    private final BrowsingDataService browsingDataService;
    private final ResourceMonitor resourceMonitor;
    private final FaviconService faviconService;
    private final Map<SidebarPanel, LoadedPanel> loadedPanels =
            new EnumMap<>(SidebarPanel.class);

    @FXML
    private Label panelTitle;

    @FXML
    private ToggleButton dockButton;

    @FXML
    private StackPane panelContentHost;

    private BrowserActions actions;
    private TabManager tabManager;
    private LoadedPanel visiblePanel;

    public SidebarPanelController(
            BrowserUiState uiState,
            PersistenceService persistenceService,
            DownloadManager downloadManager,
            BrowsingDataService browsingDataService,
            ResourceMonitor resourceMonitor,
            FaviconService faviconService) {
        this.uiState = Objects.requireNonNull(uiState, "uiState");
        this.persistenceService = Objects.requireNonNull(
                persistenceService, "persistenceService");
        this.downloadManager = Objects.requireNonNull(
                downloadManager, "downloadManager");
        this.browsingDataService = Objects.requireNonNull(
                browsingDataService, "browsingDataService");
        this.resourceMonitor = Objects.requireNonNull(
                resourceMonitor, "resourceMonitor");
        this.faviconService = Objects.requireNonNull(
                faviconService, "faviconService");
    }

    @FXML
    private void initialize() {
        dockButton.selectedProperty().bindBidirectional(
                uiState.panelDockedProperty());
        uiState.activeSidebarPanelProperty().addListener(
                (observable, oldPanel, newPanel) -> showPanel(newPanel));
        showPanel(uiState.activeSidebarPanelProperty().get());
    }

    public void setActions(BrowserActions actions) {
        this.actions = actions;
        loadedPanels.values().forEach(panel ->
                panel.controller().setActions(actions));
    }

    public void setTabManager(TabManager tabManager) {
        this.tabManager = tabManager;
        loadedPanels.values().forEach(panel ->
                panel.controller().setTabManager(tabManager));
    }

    public void refreshBookmarks() {
        LoadedPanel bookmarks = loadedPanels.get(SidebarPanel.BOOKMARKS);
        if (bookmarks != null) {
            bookmarks.controller().refreshBookmarks();
        }
    }

    public int loadedPanelCount() {
        return loadedPanels.size();
    }

    public void close() {
        if (visiblePanel != null) {
            visiblePanel.controller().onHidden();
        }
        loadedPanels.values().forEach(panel -> panel.controller().close());
        loadedPanels.clear();
        panelContentHost.getChildren().clear();
        visiblePanel = null;
    }

    @FXML
    private void closePanel() {
        uiState.activeSidebarPanelProperty().set(SidebarPanel.NONE);
    }

    private void showPanel(SidebarPanel selected) {
        if (visiblePanel != null) {
            visiblePanel.controller().onHidden();
            visiblePanel = null;
        }
        panelTitle.setText(title(selected));
        if (selected == null || selected == SidebarPanel.NONE) {
            panelContentHost.getChildren().clear();
            return;
        }
        LoadedPanel panel = loadedPanels.computeIfAbsent(
                selected, this::loadPanel);
        visiblePanel = panel;
        panelContentHost.getChildren().setAll(panel.node());
        panel.controller().onShown();
    }

    private LoadedPanel loadPanel(SidebarPanel panel) {
        URL resource = SidebarPanelController.class.getResource(
                resourceName(panel));
        if (resource == null) {
            throw new IllegalStateException(
                    "Missing sidebar panel FXML for " + panel);
        }
        SidebarContentController controller = new SidebarContentController(
                panel,
                uiState,
                persistenceService,
                downloadManager,
                browsingDataService,
                resourceMonitor,
                faviconService);
        controller.setActions(actions);
        if (tabManager != null) {
            controller.setTabManager(tabManager);
        }
        FXMLLoader loader = new FXMLLoader(resource);
        loader.setController(controller);
        try {
            return new LoadedPanel(loader.load(), controller);
        } catch (IOException error) {
            throw new IllegalStateException(
                    "Unable to load sidebar panel " + panel,
                    error);
        }
    }

    private static String resourceName(SidebarPanel panel) {
        return switch (panel) {
            case GX_CONTROL -> "gx-control-panel.fxml";
            case BOOKMARKS -> "bookmarks-panel.fxml";
            case HISTORY -> "history-panel.fxml";
            case DOWNLOADS -> "downloads-panel.fxml";
            case SETTINGS -> "settings-panel.fxml";
            case NONE -> throw new IllegalArgumentException(
                    "NONE has no sidebar document");
        };
    }

    private static String title(SidebarPanel panel) {
        if (panel == null) {
            return "";
        }
        return switch (panel) {
            case GX_CONTROL -> "GX CONTROL";
            case BOOKMARKS -> "BOOKMARKS";
            case HISTORY -> "HISTORY";
            case DOWNLOADS -> "DOWNLOADS";
            case SETTINGS -> "SETTINGS";
            case NONE -> "";
        };
    }

    private record LoadedPanel(
            Node node,
            SidebarContentController controller) {
    }
}
