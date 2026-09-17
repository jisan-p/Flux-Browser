package com.flux.browser.controller;

import java.util.List;
import javafx.fxml.FXML;
import javafx.scene.control.Button;

public final class SidebarController {
    @FXML private Button homeButton, bookmarksButton, historyButton, settingsButton;
    private BrowserController browser;
    private String selectedPage;
    public void configure(BrowserController browser) { this.browser = browser; }
    @FXML private void home() { if (browser != null) browser.home(); }
    @FXML private void bookmarks() { if (browser != null) browser.showLibrary(true); }
    @FXML private void history() { if (browser != null) browser.showLibrary(false); }
    @FXML private void settings() { if (browser != null) browser.settings(); }

    @FXML private void downloads() { if(browser!=null)browser.featureSection("Downloads"); }
    @FXML private void workspaces() { if(browser!=null)browser.featureSection("Workspaces"); }
    @FXML private void tools() { if (browser != null) browser.features(); }
    @FXML private void customize() { if (browser != null) browser.easySetup(); }

    public void select(String page) {
        if (page.equals(selectedPage)) return;
        selectedPage = page;
        for (Button button : List.of(homeButton, bookmarksButton, historyButton, settingsButton)) button.getStyleClass().remove("selected");
        Button selected = switch (page) {
            case "home" -> homeButton;
            case "bookmarks" -> bookmarksButton;
            case "history" -> historyButton;
            case "settings" -> settingsButton;
            default -> null;
        };
        if (selected != null) selected.getStyleClass().add("selected");
    }
}
