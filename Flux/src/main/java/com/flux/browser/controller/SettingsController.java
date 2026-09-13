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
    private BrowserController browser;
    private boolean updating;

    public void configure(BrowserController browser) {
        this.browser = browser;
        zoomSlider.valueProperty().addListener((observable, old, value) -> {
            zoomLabel.setText(Math.round(value.doubleValue()) + "%");
            if (!updating) browser.setZoom(value.doubleValue() / 100);
        });
        updateAccent(false);
    }

    public void show(double zoom) {
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

    private void updateAccent(boolean cyan) {
        magentaButton.pseudoClassStateChanged(PseudoClass.getPseudoClass("selected"), !cyan);
        cyanButton.pseudoClassStateChanged(PseudoClass.getPseudoClass("selected"), cyan);
    }
    @FXML private void magenta() { if (browser != null) { browser.setAccent(false); updateAccent(false); } }
    @FXML private void cyan() { if (browser != null) { browser.setAccent(true); updateAccent(true); } }
    @FXML private void resetZoom() { zoomSlider.setValue(100); }
    @FXML private void reconnect() { if (browser != null) browser.reconnect(); }
    @FXML private void dismiss() { if (browser != null) browser.dismissPanels(); }
}
