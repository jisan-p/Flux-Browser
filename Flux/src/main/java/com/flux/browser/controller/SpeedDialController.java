package com.flux.browser.controller;

import com.flux.browser.db.SpeedDialDAO;
import com.flux.browser.model.SpeedDial;
import com.flux.browser.util.Views;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.TilePane;
import javafx.util.Duration;

public final class SpeedDialController {
    @FXML private Label clockLabel, dateLabel, dialCount, dialNote;
    @FXML private TextField searchField;
    @FXML private Button addDialButton;
    @FXML private TilePane tiles;
    private BrowserController browser;
    private SpeedDialDAO dao;
    private Timeline clock;
    private int request;
    private boolean disposed;

    public void configure(BrowserController browser, SpeedDialDAO dao) {
        this.browser = browser;
        this.dao = dao;
        updateClock();
        clock = new Timeline(new KeyFrame(Duration.minutes(1), event -> updateClock()));
        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();
        refresh();
    }

    private void updateClock() {
        var now = LocalDateTime.now();
        clockLabel.setText(now.format(DateTimeFormatter.ofPattern("HH:mm")));
        dateLabel.setText(now.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH)).toUpperCase(Locale.ROOT));
    }

    public void refresh() {
        if (disposed || browser == null) return;
        int version = ++request;
        dialNote.setVisible(true); dialNote.setManaged(true);
        addDialButton.setDisable(!browser.storageAvailable());
        if (!browser.storageAvailable()) {
            render(SpeedDial.starters());
            dialNote.setText("Starter shortcuts · Connect storage in Settings to customize your Speed Dial.");
            return;
        }
        dao.getSpeedDials().whenComplete((items, error) -> Platform.runLater(() -> {
            if (disposed || request != version) return;
            if (error != null) {
                browser.storageChanged();
                render(SpeedDial.starters());
                dialNote.setText("Saved shortcuts could not be loaded. Showing starter shortcuts.");
                addDialButton.setDisable(true);
            } else {
                render(items);
                dialNote.setText(items.isEmpty() ? "An open canvas. Add your first favorite site." : "Your favorite places, one click away.");
                dialNote.setVisible(items.isEmpty()); dialNote.setManaged(items.isEmpty());
            }
        }));
    }

    private void render(List<SpeedDial> items) {
        tiles.getChildren().clear();
        for (SpeedDial item : items) {
            var view = Views.<DialTileController>load("DialTile");
            view.controller().configure(browser, item);
            tiles.getChildren().add(view.root());
        }
        dialCount.setText(String.format("%02d", items.size()));
    }

    @FXML private void search() { if (browser != null) browser.navigateTo(searchField.getText()); }
    @FXML private void add() { if (browser != null) browser.editDial(null); }
    public void dispose() { disposed = true; request++; if (clock != null) clock.stop(); }
}
