package com.flux.browser.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;

public final class ManagerHeaderController {
    private BrowserController browser;
    public void configure(BrowserController browser) { this.browser = browser; }
    public void select(javafx.scene.Parent root, String name) {
        for (var node : root.lookupAll(".manager-tab")) {
            node.getStyleClass().remove("manager-current");
            if (name.equals(node.getUserData())) node.getStyleClass().add("manager-current");
        }
    }
    @FXML private void navigate(ActionEvent event) {
        switch (((Button) event.getSource()).getUserData().toString()) {
            case "History" -> browser.showLibrary(false);
            case "Downloads" -> browser.showDownloads();
            case "Bookmarks" -> browser.showLibrary(true);
            case "Settings" -> browser.settings();
        }
    }
    @FXML private void dismiss() { browser.dismissPanels(); }
}
