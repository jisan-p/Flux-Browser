package com.flux.browser.controller;

import com.flux.browser.model.Bookmark;
import com.flux.browser.model.HistoryEntry;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

public final class LibraryRowController {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm").withZone(ZoneId.systemDefault());
    @FXML private Label iconLabel, titleLabel, urlLabel, categoryLabel, dateLabel;
    @FXML private Button openButton, editButton;
    private BrowserController browser;
    private LibraryController library;
    private Object item;
    private String url;

    @FXML private void initialize() {
        titleLabel.maxWidthProperty().bind(openButton.widthProperty().subtract(4));
        urlLabel.maxWidthProperty().bind(openButton.widthProperty().subtract(4));
    }

    public void configure(BrowserController browser, LibraryController library, Object item) {
        this.browser = browser; this.library = library; this.item = item;
        boolean bookmark = item instanceof Bookmark;
        editButton.setVisible(bookmark); editButton.setManaged(bookmark);
        if (item instanceof Bookmark entry) {
            titleLabel.setText(entry.title()); url = entry.url(); categoryLabel.setText(entry.category());
            dateLabel.setText(DATE.format(entry.createdAt())); iconLabel.setText("☆");
        } else if (item instanceof HistoryEntry entry) {
            titleLabel.setText(entry.title()); url = entry.url(); categoryLabel.setText("VISITED");
            dateLabel.setText(DATE.format(entry.visitedAt())); iconLabel.setText("↗");
        }
        urlLabel.setText(url);
        openButton.setAccessibleText("Open " + titleLabel.getText());
    }

    @FXML private void open() { if (browser != null) browser.navigateTo(url); }
    @FXML private void edit() { if (browser != null && item instanceof Bookmark bookmark) browser.editBookmark(bookmark, library::refresh); }
    @FXML private void delete() {
        if (browser == null) return;
        if (item instanceof Bookmark bookmark) browser.deleteBookmark(bookmark, library::refresh);
        else if (item instanceof HistoryEntry entry) browser.deleteVisit(entry, library::refresh);
    }
}
