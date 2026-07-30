package org.custombrowser.ui;

import org.custombrowser.application.ApplicationContext;
import org.custombrowser.ui.window.WindowResizeSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public final class BrowserWindow extends Application {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(BrowserWindow.class);

    private ApplicationContext applicationContext;

    @Override
    public void stop() {
        if (applicationContext != null) {
            applicationContext.close();
        }
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            applicationContext = ApplicationContext.createDefault();
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("browser.fxml"));
            loader.setControllerFactory(applicationContext::createController);
            Parent root = loader.load();
            BrowserController browserController = loader.getController();

            Scene scene = new Scene(root, 1280, 800);
            scene.setFill(Color.TRANSPARENT);
            scene.getStylesheets().add(
                    getClass().getResource("flux-gx.css").toExternalForm());
            installKeyboardCommands(scene, primaryStage, browserController);
            WindowResizeSupport.install(scene, primaryStage);

            primaryStage.initStyle(StageStyle.UNDECORATED);
            primaryStage.setTitle("Flux Browser");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(900);
            primaryStage.setMinHeight(640);
            primaryStage.setOnCloseRequest(event -> Platform.exit());
            primaryStage.show();
        } catch (Throwable error) {
            LOGGER.error("Unable to start Flux Browser", error);
            Platform.exit();
        }
    }

    private static void installKeyboardCommands(
            Scene scene,
            Stage stage,
            BrowserController controller) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.F11) {
                stage.setFullScreen(!stage.isFullScreen());
                event.consume();
                return;
            }
            if (!event.isShortcutDown()) {
                return;
            }
            switch (event.getCode()) {
                case L -> controller.focusAddress();
                case EQUALS, ADD -> controller.zoomIn();
                case MINUS, SUBTRACT -> controller.zoomOut();
                case DIGIT0, NUMPAD0 -> controller.resetZoom();
                default -> {
                    return;
                }
            }
            event.consume();
        });
    }
}
