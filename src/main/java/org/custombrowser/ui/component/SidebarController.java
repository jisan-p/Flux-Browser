package org.custombrowser.ui.component;

import org.custombrowser.ui.BrowserActions;
import org.custombrowser.ui.state.BrowserUiState;
import org.custombrowser.ui.state.BrowserUiState.SidebarPanel;

import javafx.fxml.FXML;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.VBox;

public final class SidebarController {

    private final BrowserUiState uiState;

    @FXML
    private VBox sidebarRoot;

    @FXML
    private ToggleButton gxButton;

    @FXML
    private ToggleButton bookmarksButton;

    @FXML
    private ToggleButton historyButton;

    @FXML
    private ToggleButton downloadsButton;

    @FXML
    private ToggleButton settingsButton;

    private BrowserActions actions;

    public SidebarController(BrowserUiState uiState) {
        this.uiState = uiState;
    }

    @FXML
    private void initialize() {
        sidebarRoot.visibleProperty().bind(uiState.sidebarVisibleProperty());
        sidebarRoot.managedProperty().bind(uiState.sidebarVisibleProperty());
        uiState.activeSidebarPanelProperty().addListener(
                (observable, oldPanel, newPanel) -> selectButton(newPanel));
        selectButton(uiState.activeSidebarPanelProperty().get());
    }

    public void setActions(BrowserActions actions) {
        this.actions = actions;
    }

    @FXML
    private void home() {
        uiState.activeSidebarPanelProperty().set(SidebarPanel.NONE);
        if (actions != null) {
            actions.showStartPage();
        }
    }

    @FXML
    private void gxControl() {
        togglePanel(SidebarPanel.GX_CONTROL);
    }

    @FXML
    private void bookmarks() {
        togglePanel(SidebarPanel.BOOKMARKS);
    }

    @FXML
    private void history() {
        togglePanel(SidebarPanel.HISTORY);
    }

    @FXML
    private void downloads() {
        togglePanel(SidebarPanel.DOWNLOADS);
    }

    @FXML
    private void settings() {
        togglePanel(SidebarPanel.SETTINGS);
    }

    @FXML
    private void toggleSidebar() {
        uiState.sidebarVisibleProperty().set(false);
        uiState.activeSidebarPanelProperty().set(SidebarPanel.NONE);
    }

    private void togglePanel(SidebarPanel requested) {
        SidebarPanel next = uiState.activeSidebarPanelProperty().get() == requested
                ? SidebarPanel.NONE
                : requested;
        uiState.activeSidebarPanelProperty().set(next);
    }

    private void selectButton(SidebarPanel panel) {
        gxButton.setSelected(panel == SidebarPanel.GX_CONTROL);
        bookmarksButton.setSelected(panel == SidebarPanel.BOOKMARKS);
        historyButton.setSelected(panel == SidebarPanel.HISTORY);
        downloadsButton.setSelected(panel == SidebarPanel.DOWNLOADS);
        settingsButton.setSelected(panel == SidebarPanel.SETTINGS);
    }
}
