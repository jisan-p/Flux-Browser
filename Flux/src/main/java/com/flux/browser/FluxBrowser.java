package com.flux.browser;

import com.flux.browser.controller.BrowserController;
import com.flux.browser.db.DatabaseManager;
import com.flux.browser.util.Views;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public final class FluxBrowser extends Application {
    private BrowserController browser;

    @Override public void start(Stage stage) {
        var view = Views.<BrowserController>load("BrowserWindow");
        browser = view.controller();
        var bounds = Screen.getPrimary().getVisualBounds();
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setTitle("Flux · Speed Dial");
        stage.setMinWidth(920);
        stage.setMinHeight(620);
        stage.setScene(new Scene(view.root(), Math.min(1320, bounds.getWidth() - 60), Math.min(860, bounds.getHeight() - 60)));
        browser.configure(stage, new DatabaseManager());
        stage.setOnCloseRequest(event -> browser.close());
        stage.show();
        browser.focusAddress();
    }

    @Override public void stop() { if (browser != null) browser.close(); }
    public static void main(String[] args) { launch(args); }
}
