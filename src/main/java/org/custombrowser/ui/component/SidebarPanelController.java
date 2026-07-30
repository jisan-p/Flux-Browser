package org.custombrowser.ui.component;

import java.util.Map;
import java.util.Locale;

import org.custombrowser.persistence.PersistenceModels.Bookmark;
import org.custombrowser.persistence.PersistenceModels.Download;
import org.custombrowser.persistence.PersistenceModels.Visit;
import org.custombrowser.persistence.PersistenceService;
import org.custombrowser.ui.BrowserActions;
import org.custombrowser.ui.state.BrowserUiState;
import org.custombrowser.ui.state.BrowserUiState.SidebarPanel;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;

public final class SidebarPanelController {

    private final BrowserUiState uiState;
    private final PersistenceService persistenceService;

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

    @FXML
    private TextField bookmarkSearch;

    @FXML
    private ListView<Bookmark> bookmarkList;

    @FXML
    private Label bookmarkStatus;

    @FXML
    private TextField historySearch;

    @FXML
    private ListView<Visit> historyList;

    @FXML
    private Label historyStatus;

    @FXML
    private TextField downloadSearch;

    @FXML
    private ListView<Download> downloadList;

    @FXML
    private Label downloadStatus;

    @FXML
    private TextField settingsSearch;

    @FXML
    private VBox appearanceSettings;

    @FXML
    private VBox privacySettings;

    @FXML
    private Label databaseStatus;

    private BrowserActions actions;
    private Map<SidebarPanel, Node> contentByPanel;

    public SidebarPanelController(
            BrowserUiState uiState,
            PersistenceService persistenceService) {
        this.uiState = uiState;
        this.persistenceService = persistenceService;
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
        bookmarkSearch.textProperty().addListener(
                (observable, oldText, newText) -> refreshBookmarks());
        historySearch.textProperty().addListener(
                (observable, oldText, newText) -> refreshHistory());
        downloadSearch.textProperty().addListener(
                (observable, oldText, newText) -> refreshDownloads());
        settingsSearch.textProperty().addListener(
                (observable, oldText, newText) -> filterSettings(newText));
        bookmarkList.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY
                    && event.getClickCount() == 2) {
                openSelectedBookmark();
            }
        });
        historyList.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY
                    && event.getClickCount() == 2) {
                openSelectedVisit();
            }
        });
        databaseStatus.setText(persistenceService.enabled()
                ? "CONNECTED · POSTGRESQL"
                : "TEST MODE · NO DATABASE");
        showPanel(uiState.activeSidebarPanelProperty().get());
    }

    public void setActions(BrowserActions actions) {
        this.actions = actions;
    }

    public void refreshBookmarks() {
        persistenceService.bookmarks(bookmarkSearch.getText())
                .whenComplete((items, error) -> Platform.runLater(() -> {
                    if (error != null) {
                        bookmarkStatus.setText("DATABASE ERROR");
                    } else {
                        bookmarkList.getItems().setAll(items);
                        bookmarkStatus.setText(items.size() + " BOOKMARKS");
                    }
                }));
    }

    @FXML
    private void closePanel() {
        uiState.activeSidebarPanelProperty().set(SidebarPanel.NONE);
    }

    @FXML
    private void bookmarkCurrentPage() {
        if (actions != null) {
            actions.bookmarkCurrentPage();
        }
    }

    @FXML
    private void openSelectedBookmark() {
        Bookmark selected = bookmarkList.getSelectionModel().getSelectedItem();
        if (selected != null && actions != null) {
            actions.navigate(selected.url());
        }
    }

    @FXML
    private void deleteSelectedBookmark() {
        Bookmark selected = bookmarkList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            persistenceService.deleteBookmark(selected.id())
                    .thenRun(() -> Platform.runLater(this::refreshBookmarks));
        }
    }

    @FXML
    private void clearBookmarks() {
        if (confirm("Delete all bookmarks?")) {
            persistenceService.clearBookmarks()
                    .thenRun(() -> Platform.runLater(this::refreshBookmarks));
        }
    }

    @FXML
    private void openSelectedVisit() {
        Visit selected = historyList.getSelectionModel().getSelectedItem();
        if (selected != null && actions != null) {
            actions.navigate(selected.url());
        }
    }

    @FXML
    private void deleteSelectedVisit() {
        Visit selected = historyList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            persistenceService.deleteVisit(selected.id())
                    .thenRun(() -> Platform.runLater(this::refreshHistory));
        }
    }

    @FXML
    private void clearHistory() {
        if (confirm("Delete all browsing history?")) {
            persistenceService.clearVisits()
                    .thenRun(() -> Platform.runLater(this::refreshHistory));
        }
    }

    @FXML
    private void deleteSelectedDownload() {
        Download selected = downloadList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            persistenceService.deleteDownload(selected.id())
                    .thenRun(() -> Platform.runLater(this::refreshDownloads));
        }
    }

    @FXML
    private void clearDownloads() {
        if (confirm("Delete all download metadata?")) {
            persistenceService.clearDownloads()
                    .thenRun(() -> Platform.runLater(this::refreshDownloads));
        }
    }

    private void refreshHistory() {
        persistenceService.visits(historySearch.getText())
                .whenComplete((items, error) -> Platform.runLater(() -> {
                    if (error != null) {
                        historyStatus.setText("DATABASE ERROR");
                    } else {
                        historyList.getItems().setAll(items);
                        historyStatus.setText(items.size() + " VISITS");
                    }
                }));
    }

    private void refreshDownloads() {
        persistenceService.downloads(downloadSearch.getText())
                .whenComplete((items, error) -> Platform.runLater(() -> {
                    if (error != null) {
                        downloadStatus.setText("DATABASE ERROR");
                    } else {
                        downloadList.getItems().setAll(items);
                        downloadStatus.setText(items.size() + " DOWNLOADS");
                    }
                }));
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
        switch (selected) {
            case BOOKMARKS -> refreshBookmarks();
            case HISTORY -> refreshHistory();
            case DOWNLOADS -> refreshDownloads();
            default -> {
            }
        }
    }

    private void filterSettings(String query) {
        String normalized = query == null
                ? ""
                : query.trim().toLowerCase(Locale.ROOT);
        setVisibleAndManaged(
                appearanceSettings,
                normalized.isBlank()
                        || "appearance theme accent wallpaper sidebar scale motion"
                                .contains(normalized));
        setVisibleAndManaged(
                privacySettings,
                normalized.isBlank()
                        || "privacy data history downloads database"
                                .contains(normalized));
    }

    private boolean confirm(String message) {
        Alert alert = new Alert(
                Alert.AlertType.CONFIRMATION,
                message,
                ButtonType.OK,
                ButtonType.CANCEL);
        alert.setTitle("Confirm data removal");
        alert.setHeaderText("This action cannot be undone");
        if (panelTitle.getScene() != null) {
            alert.initOwner(panelTitle.getScene().getWindow());
        }
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private static void setVisibleAndManaged(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
