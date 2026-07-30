package org.custombrowser.ui.component;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.custombrowser.ui.BrowserActions;

import javafx.fxml.FXML;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;

public final class NavigationBarController {

    @FXML
    private Button backButton;

    @FXML
    private Button forwardButton;

    @FXML
    private Button reloadButton;

    @FXML
    private TextField addressField;

    @FXML
    private Label securityIndicator;

    @FXML
    private ProgressBar loadProgress;

    private BrowserActions actions;
    private final ContextMenu suggestionMenu = new ContextMenu();
    private List<String> suggestions = List.of();

    @FXML
    private void initialize() {
        suggestionMenu.setAutoHide(true);
        addressField.textProperty().addListener(
                (observable, oldValue, newValue) -> refreshSuggestions());
        addressField.focusedProperty().addListener(
                (observable, wasFocused, focused) -> {
                    if (focused) {
                        refreshSuggestions();
                    } else {
                        suggestionMenu.hide();
                    }
                });
        addressField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                suggestionMenu.hide();
            }
        });
    }

    public void setActions(BrowserActions actions) {
        this.actions = actions;
    }

    public void setAddress(String address, boolean force) {
        if (force || !addressField.isFocused()) {
            addressField.setText(address);
        }
        securityIndicator.setText(
                address != null && address.startsWith("https://") ? "▣" : "◇");
    }

    public void setSuggestions(Collection<String> candidates) {
        List<String> next = candidates.stream()
                .filter(candidate -> candidate != null && !candidate.isBlank())
                .distinct()
                .sorted()
                .toList();
        if (suggestions.equals(next)) {
            return;
        }
        suggestions = next;
        refreshSuggestions();
    }

    public void setPageState(
            boolean canGoBack,
            boolean canGoForward,
            boolean loading,
            double progress) {
        backButton.setDisable(!canGoBack);
        forwardButton.setDisable(!canGoForward);
        reloadButton.setText(loading ? "×" : "↻");
        reloadButton.setAccessibleText(loading ? "Stop loading" : "Reload");
        loadProgress.setVisible(loading);
        loadProgress.setManaged(loading);
        loadProgress.setProgress(progress);
    }

    public void focusAddress() {
        addressField.requestFocus();
        addressField.selectAll();
    }

    @FXML
    private void submitAddress() {
        suggestionMenu.hide();
        if (actions != null) {
            actions.navigate(addressField.getText());
        }
    }

    @FXML
    private void back() {
        if (actions != null) {
            actions.goBack();
        }
    }

    @FXML
    private void forward() {
        if (actions != null) {
            actions.goForward();
        }
    }

    @FXML
    private void reloadOrStop() {
        if (actions != null) {
            actions.reloadOrStop();
        }
    }

    @FXML
    private void home() {
        if (actions != null) {
            actions.showStartPage();
        }
    }

    @FXML
    private void easySetup() {
        if (actions != null) {
            actions.toggleEasySetup();
        }
    }

    @FXML
    private void findInPage() {
        if (actions != null) {
            actions.showFindBar();
        }
    }

    @FXML
    private void copyAddress() {
        if (actions != null) {
            actions.copyAddress();
        }
    }

    @FXML
    private void openExternally() {
        if (actions != null) {
            actions.openCurrentPageExternally();
        }
    }

    @FXML
    private void printPage() {
        if (actions != null) {
            actions.printCurrentPage();
        }
    }

    @FXML
    private void bookmarkPage() {
        if (actions != null) {
            actions.bookmarkCurrentPage();
        }
    }

    private void refreshSuggestions() {
        if (!addressField.isFocused()
                || addressField.getScene() == null
                || addressField.getScene().getWindow() == null
                || !addressField.getScene().getWindow().isShowing()) {
            suggestionMenu.hide();
            return;
        }

        String query = addressField.getText() == null
                ? ""
                : addressField.getText().trim().toLowerCase(Locale.ROOT);
        if (query.isBlank()) {
            suggestionMenu.hide();
            return;
        }

        List<MenuItem> matches = suggestions.stream()
                .filter(candidate ->
                        candidate.toLowerCase(Locale.ROOT).contains(query))
                .limit(8)
                .map(this::suggestionItem)
                .toList();
        if (matches.isEmpty()) {
            suggestionMenu.hide();
            return;
        }

        suggestionMenu.getItems().setAll(matches);
        if (!suggestionMenu.isShowing()) {
            suggestionMenu.show(addressField, Side.BOTTOM, 0, 0);
        }
    }

    private MenuItem suggestionItem(String candidate) {
        MenuItem item = new MenuItem(candidate);
        item.setOnAction(event -> {
            addressField.setText(candidate);
            suggestionMenu.hide();
            if (actions != null) {
                actions.navigate(candidate);
            }
        });
        return item;
    }
}
