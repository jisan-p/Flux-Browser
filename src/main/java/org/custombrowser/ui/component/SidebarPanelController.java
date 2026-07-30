package org.custombrowser.ui.component;

import java.util.Map;

import org.custombrowser.ui.state.BrowserUiState;
import org.custombrowser.ui.state.BrowserUiState.SidebarPanel;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.VBox;

public final class SidebarPanelController {

    private final BrowserUiState uiState;

    @FXML
    private Label panelTitle;

    @FXML
    private ToggleButton dockButton;

    @FXML
    private VBox gxContent;

    @FXML
    private VBox bookmarksContent;

    @FXML
    private VBox historyContent;

    @FXML
    private VBox downloadsContent;

    @FXML
    private VBox settingsContent;

    private Map<SidebarPanel, Node> contentByPanel;

    public SidebarPanelController(BrowserUiState uiState) {
        this.uiState = uiState;
    }

    @FXML
    private void initialize() {
        contentByPanel = Map.of(
                SidebarPanel.GX_CONTROL, gxContent,
                SidebarPanel.BOOKMARKS, bookmarksContent,
                SidebarPanel.HISTORY, historyContent,
                SidebarPanel.DOWNLOADS, downloadsContent,
                SidebarPanel.SETTINGS, settingsContent);
        dockButton.selectedProperty().bindBidirectional(uiState.panelDockedProperty());
        uiState.activeSidebarPanelProperty().addListener(
                (observable, oldPanel, newPanel) -> showPanel(newPanel));
        showPanel(uiState.activeSidebarPanelProperty().get());
    }

    @FXML
    private void closePanel() {
        uiState.activeSidebarPanelProperty().set(SidebarPanel.NONE);
    }

    private void showPanel(SidebarPanel selected) {
        contentByPanel.forEach((panel, node) -> {
            boolean active = panel == selected;
            node.setVisible(active);
            node.setManaged(active);
        });
        panelTitle.setText(switch (selected) {
            case GX_CONTROL -> "GX CONTROL";
            case BOOKMARKS -> "BOOKMARKS";
            case HISTORY -> "HISTORY";
            case DOWNLOADS -> "DOWNLOADS";
            case SETTINGS -> "SETTINGS";
            case NONE -> "";
        });
    }
}
