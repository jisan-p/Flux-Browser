package com.flux.browser.controller;

import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;

public final class SettingsController {
    @FXML private Button magentaButton, cyanButton, reconnectButton;
    @FXML private Label zoomLabel, connectionLabel, connectionDetail;
    @FXML private Slider zoomSlider;
    @FXML private javafx.scene.control.TextField settingsSearch;
    @FXML private javafx.scene.control.ToggleGroup categories, displayModes;
    @FXML private javafx.scene.layout.FlowPane themeChoices;
    @FXML private javafx.scene.layout.VBox settingsCards;
    @FXML private Label noResults;
    private String selectedCategory = "Appearance";
    private BrowserController browser;
    private boolean updating;

    public void configure(BrowserController browser) {
        this.browser = browser;
        zoomSlider.valueProperty().addListener((observable, old, value) -> {
            zoomLabel.setText(Math.round(value.doubleValue()) + "%");
            if (!updating) browser.setZoom(value.doubleValue() / 100);
        });
        settingsSearch.textProperty().addListener(o -> filter());
        updateAccent(); filter();
    }

    private void filter() {
        String query = settingsSearch.getText().strip().toLowerCase(java.util.Locale.ROOT);
        int count = 0;
        for (var card : settingsCards.getChildren()) {
            String terms = String.valueOf(card.getUserData()).toLowerCase(java.util.Locale.ROOT);
            boolean visible = query.isEmpty() ? terms.startsWith(selectedCategory.toLowerCase(java.util.Locale.ROOT)) : terms.contains(query);
            card.setVisible(visible); card.setManaged(visible); if (visible) count++;
        }
        noResults.setVisible(count == 0); noResults.setManaged(count == 0);
    }
    @FXML private void category(javafx.event.ActionEvent event) {
        var button = (javafx.scene.control.ToggleButton) event.getSource();
        button.setSelected(true); selectedCategory = String.valueOf(button.getUserData());
        settingsSearch.clear(); filter();
    }
    @FXML private void openFeature(javafx.event.ActionEvent event) { browser.featureSection(String.valueOf(((Button)event.getSource()).getUserData())); }
    @FXML private void history() { browser.showLibrary(false); }
    @FXML private void bookmarks() { browser.showLibrary(true); }
    @FXML private void theme(javafx.event.ActionEvent event) {
        browser.preferences().appearance.theme(String.valueOf(((Button) event.getSource()).getUserData()));
        browser.appearanceChanged(); syncThemes();
    }
    @FXML private void mode(javafx.event.ActionEvent event) {
        var button = (javafx.scene.control.ToggleButton) event.getSource(); button.setSelected(true);
        browser.preferences().appearance.mode = String.valueOf(button.getUserData()); browser.appearanceChanged();
    }
    private void syncThemes() {
        var a = browser.preferences().appearance;
        for (var choice : themeChoices.getChildren()) choice.pseudoClassStateChanged(PseudoClass.getPseudoClass("selected"), a.theme.equals(choice.getUserData()));
        for (var choice : displayModes.getToggles()) choice.setSelected(a.mode.equals(choice.getUserData()));
        updateAccent();
    }
    public void show(double zoom) {
        syncThemes();
        updating = true;
        zoomSlider.setValue(zoom * 100);
        updating = false;
    }

    public void connection(boolean online, boolean connecting) {
        reconnectButton.setDisable(connecting);
        connectionLabel.setText(connecting ? "Connecting…" : online ? "Connected" : "Offline");
        connectionLabel.pseudoClassStateChanged(PseudoClass.getPseudoClass("online"), online);
        connectionDetail.setText(connecting ? "Checking your database connection…" : online
                ? "Storage is ready. Your changes are saved automatically."
                : "Browsing is available. Saving is paused until storage reconnects. Check that PostgreSQL is running and your connection settings are correct.");
    }

    private void updateAccent() {
        String accent = browser.preferences().appearance.accent;
        magentaButton.pseudoClassStateChanged(PseudoClass.getPseudoClass("selected"), accent.equals("#fa1e4e"));
        cyanButton.pseudoClassStateChanged(PseudoClass.getPseudoClass("selected"), accent.equals("#00ffff"));
    }
    @FXML private void magenta() { if (browser != null) { browser.setAccent(false); syncThemes(); } }
    @FXML private void cyan() { if (browser != null) { browser.setAccent(true); syncThemes(); } }
    @FXML private void customize() { if(browser!=null) { browser.dismissPanels(); browser.easySetup(); } }
    @FXML private void resetZoom() { zoomSlider.setValue(100); }
    @FXML private void reconnect() { if (browser != null) browser.reconnect(); }
    @FXML private void dismiss() { if (browser != null) browser.dismissPanels(); }
}
