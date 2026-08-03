package org.custombrowser.ui.component;

import org.custombrowser.ui.BrowserActions;
import org.custombrowser.ui.model.SpeedDialEntry;
import org.custombrowser.ui.state.BrowserUiState;
import org.custombrowser.ui.state.BrowserUiState.Wallpaper;

import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

public final class StartPageController {

    private final BrowserUiState uiState;

    @FXML
    private StackPane startPageRoot;

    @FXML
    private TextField searchField;

    @FXML
    private TilePane speedDialGrid;

    @FXML
    private StackPane editorOverlay;

    @FXML
    private TextField dialTitleField;

    @FXML
    private TextField dialAddressField;

    private BrowserActions actions;
    private SpeedDialEntry editingEntry;

    public StartPageController(BrowserUiState uiState) {
        this.uiState = uiState;
    }

    @FXML
    private void initialize() {
        uiState.wallpaperProperty().addListener(
                (observable, oldWallpaper, newWallpaper) ->
                        applyWallpaper(newWallpaper));
        uiState.speedDials().addListener(
                (ListChangeListener<SpeedDialEntry>) change -> rebuildSpeedDials());
        applyWallpaper(uiState.wallpaperProperty().get());
        rebuildSpeedDials();
    }

    public void setActions(BrowserActions actions) {
        this.actions = actions;
    }

    public void focusSearch() {
        searchField.requestFocus();
        searchField.selectAll();
    }

    @FXML
    private void submitSearch() {
        if (actions != null && !searchField.getText().isBlank()) {
            actions.navigate(searchField.getText());
        }
    }

    @FXML
    private void openNewDialEditor() {
        editingEntry = null;
        dialTitleField.clear();
        dialAddressField.clear();
        showEditor(true);
        dialTitleField.requestFocus();
    }

    @FXML
    private void saveDial() {
        String title = dialTitleField.getText();
        String address = dialAddressField.getText();
        try {
            SpeedDialEntry replacement = new SpeedDialEntry(title, address);
            if (editingEntry == null) {
                uiState.speedDials().add(replacement);
            } else {
                int index = uiState.speedDials().indexOf(editingEntry);
                if (index >= 0) {
                    uiState.speedDials().set(index, replacement);
                }
            }
            showEditor(false);
        } catch (IllegalArgumentException ignored) {
            if (title == null || title.isBlank()) {
                dialTitleField.requestFocus();
            } else {
                dialAddressField.requestFocus();
            }
        }
    }

    @FXML
    private void cancelDialEditor() {
        showEditor(false);
    }

    private void edit(SpeedDialEntry entry) {
        editingEntry = entry;
        dialTitleField.setText(entry.title());
        dialAddressField.setText(entry.address());
        showEditor(true);
        dialTitleField.requestFocus();
        dialTitleField.selectAll();
    }

    private void move(SpeedDialEntry entry, int offset) {
        int oldIndex = uiState.speedDials().indexOf(entry);
        int newIndex = oldIndex + offset;
        if (oldIndex >= 0 && newIndex >= 0 && newIndex < uiState.speedDials().size()) {
            uiState.speedDials().remove(oldIndex);
            uiState.speedDials().add(newIndex, entry);
        }
    }

    private void rebuildSpeedDials() {
        speedDialGrid.getChildren().clear();
        for (SpeedDialEntry entry : uiState.speedDials()) {
            speedDialGrid.getChildren().add(createDial(entry));
        }
    }

    private VBox createDial(SpeedDialEntry entry) {
        String initial = entry.title().substring(0, 1).toUpperCase();
        Button tile = new Button(initial);
        tile.getStyleClass().add("speed-dial-icon");
        tile.setOnAction(event -> {
            if (actions != null) {
                actions.navigate(entry.address());
            }
        });

        MenuItem edit = new MenuItem("Edit");
        edit.setOnAction(event -> edit(entry));
        MenuItem moveLeft = new MenuItem("Move left");
        moveLeft.setOnAction(event -> move(entry, -1));
        MenuItem moveRight = new MenuItem("Move right");
        moveRight.setOnAction(event -> move(entry, 1));
        MenuItem remove = new MenuItem("Remove");
        remove.setOnAction(event -> uiState.speedDials().remove(entry));
        tile.setContextMenu(new ContextMenu(edit, moveLeft, moveRight, remove));
        tile.setTooltip(new Tooltip(entry.address()));

        Button label = new Button(entry.title());
        label.getStyleClass().add("speed-dial-label");
        label.setOnAction(tile.getOnAction());

        VBox dial = new VBox(8, tile, label);
        dial.setAlignment(Pos.CENTER);
        dial.getStyleClass().add("speed-dial");
        return dial;
    }

    private void showEditor(boolean show) {
        editorOverlay.setVisible(show);
        editorOverlay.setManaged(show);
        if (!show) {
            editingEntry = null;
        }
    }

    private void applyWallpaper(Wallpaper wallpaper) {
        startPageRoot.getStyleClass().removeIf(
                styleClass -> styleClass.startsWith("wallpaper-"));
        startPageRoot.getStyleClass().add(wallpaper.styleClass());
    }
}
