package com.flux.browser;

import com.flux.browser.controller.BrowserController;
import com.flux.browser.db.DatabaseManager;
import com.flux.browser.util.Views;
import com.flux.browser.web.NativeWebPage;
import com.flux.browser.ui.WindowGeometry;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.scene.paint.Color;

public final class FluxBrowser extends Application {
    private BrowserController browser;

    @Override public void start(Stage stage) {
        // Keep the compatibility implementation available for future releases and checks.
        // Version 1's application entry point supports the native macOS engine only.
        if (!NativeWebPage.enabled()) {
            throw new IllegalStateException("Flux 1 requires macOS with native WebKit. Windows, Linux and the JavaFX web engine are deferred to a future version. Run without -Dflux.engine=javafx.");
        }
        var view = Views.<BrowserController>load("BrowserWindow");
        browser = view.controller();
        var bounds = Screen.getPrimary().getVisualBounds();
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setTitle("Flux · Speed Dial");
        WindowGeometry.minimum(stage, bounds);
        Scene scene = new Scene(view.root());
        scene.setFill(Color.TRANSPARENT);
        stage.setScene(scene);
        WindowGeometry.apply(stage, WindowGeometry.initial(bounds));
        browser.configure(stage, new DatabaseManager());
        stage.setOnCloseRequest(event -> browser.close());
        stage.show();
        browser.focusAddress();
        String startUrl = getParameters() == null ? null : getParameters().getNamed().get("url");
        if (startUrl != null && !startUrl.isBlank()) browser.navigateTo(startUrl);
    }

    @Override public void stop() { if (browser != null) browser.close(); }
    public static void main(String[] args) { launch(args); }
}
