package com.flux.browser.controller;

import com.flux.browser.model.SpeedDial;
import com.flux.browser.util.UrlResolver;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public final class DialTileController {
    @FXML private VBox root;
    @FXML private Label monogram, titleLabel, domainLabel;
    @FXML private Button openButton, editButton, deleteButton;
    private BrowserController browser;
    private SpeedDial item;

    public void configure(BrowserController browser, SpeedDial item) {
        this.browser = browser;
        this.item = item;
        titleLabel.setText(item.title());
        monogram.setText(item.title().substring(0, item.title().offsetByCodePoints(0, 1)).toUpperCase());
        domainLabel.setText(UrlResolver.host(item.url()));
        openButton.setAccessibleText("Open " + item.title());
        editButton.setDisable(item.id() < 0);
        deleteButton.setDisable(item.id() < 0);
        if (item.position() % 2 != 0) root.getStyleClass().add("alternate");
    }
    @FXML private void open() { if (browser != null) browser.navigateTo(item.url()); }
    @FXML private void edit() { if (browser != null) browser.editDial(item); }
    @FXML private void delete() { if (browser != null) browser.deleteDial(item); }
}
