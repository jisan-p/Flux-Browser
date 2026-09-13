package com.flux.browser.controller;

import com.flux.browser.db.BookmarkDAO;
import com.flux.browser.db.HistoryDAO;
import com.flux.browser.util.Dialogs;
import com.flux.browser.util.Views;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public final class LibraryController {
    @FXML private VBox root;
    @FXML private Label heading, subtitle, resultLabel, emptyLabel;
    @FXML private TextField filterField;
    @FXML private Button addButton, clearButton;
    @FXML private ListView<Object> entries;
    private final PauseTransition debounce = new PauseTransition(Duration.millis(200));
    private BrowserController browser;
    private BookmarkDAO bookmarks;
    private HistoryDAO history;
    private boolean bookmarkMode;
    private int request;

    public void configure(BrowserController browser, BookmarkDAO bookmarks, HistoryDAO history) {
        this.browser = browser;
        this.bookmarks = bookmarks;
        this.history = history;
        debounce.setOnFinished(event -> refresh());
        filterField.textProperty().addListener((observable, before, after) -> debounce.playFromStart());
        entries.setCellFactory(list -> new ListCell<>() {
            private Views.View<LibraryRowController> row;
            @Override protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (empty || item == null) { setGraphic(null); return; }
                if (row == null) {
                    row = Views.load("LibraryRow");
                    ((javafx.scene.layout.Region) row.root()).prefWidthProperty().bind(widthProperty().subtract(20));
                    setPrefWidth(0);
                }
                row.controller().configure(browser, LibraryController.this, item);
                setGraphic(row.root());
            }
        });
    }

    public void show(boolean bookmarkMode) {
        this.bookmarkMode = bookmarkMode;
        heading.setText(bookmarkMode ? "Bookmarks" : "History");
        subtitle.setText(bookmarkMode ? "Keep the good stuff close." : "Retrace your steps. Rediscover something good.");
        filterField.setPromptText(bookmarkMode ? "Search titles, addresses, or folders" : "Search visited titles or addresses");
        addButton.setVisible(bookmarkMode); addButton.setManaged(bookmarkMode);
        clearButton.setVisible(!bookmarkMode); clearButton.setManaged(!bookmarkMode);
        filterField.clear();
        debounce.stop();
        refresh();
    }

    @FXML public void refresh() {
        if (browser == null) return;
        int version = ++request;
        addButton.setDisable(!browser.storageAvailable());
        clearButton.setDisable(!browser.storageAvailable());
        entries.getItems().clear();
        if (!browser.storageAvailable()) {
            resultLabel.setText("Storage is offline");
            emptyLabel.setText("Reconnect storage in Settings to view your saved data.");
            return;
        }
        resultLabel.setText("Loading your library…");
        emptyLabel.setText("Loading…");
        CompletableFuture<? extends List<?>> future = bookmarkMode ? bookmarks.getBookmarks(filterField.getText()) : history.getHistory(filterField.getText());
        future.whenComplete((items, error) -> Platform.runLater(() -> {
            if (version != request || browser.isClosed()) return;
            browser.storageChanged();
            if (error != null) {
                resultLabel.setText("Could not load saved data");
                emptyLabel.setText(BrowserController.friendlyError(error));
            } else {
                entries.getItems().setAll(items);
                resultLabel.setText(items.size() + (bookmarkMode ? " saved destinations" : " visits · newest first")
                        + (items.size() == 500 ? " · Showing up to 500 matches; narrow your search for older items." : ""));
                emptyLabel.setText(filterField.getText().isBlank() ? "Nothing here yet. Your next discovery is waiting." : "No matches. Try another title, address, or folder.");
            }
        }));
    }

    @FXML private void add() { if (browser != null) browser.editBookmark(null, this::refresh); }
    @FXML private void clear() {
        if (browser != null && Dialogs.confirm(browser.window(), "Clear browsing history?", "This removes all saved visits, including entries outside the current search. Your bookmarks and Speed Dial stay saved.")) {
            browser.perform("History cleared", history.deleteHistory(), ignored -> refresh());
        }
    }
    @FXML private void dismiss() { if (browser != null) browser.dismissPanels(); }
    public void dispose() { request++; debounce.stop(); }
}
