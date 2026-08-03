package org.custombrowser.ui;

import org.custombrowser.application.ApplicationContext;
import org.custombrowser.diagnostics.PerformanceTracker;
import org.custombrowser.persistence.PersistenceException;
import org.custombrowser.persistence.PersistenceModels.WindowState;
import org.custombrowser.ui.state.BrowserUiState.SidebarPanel;
import org.custombrowser.ui.window.WindowResizeSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Screen;

public final class BrowserWindow extends Application {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(BrowserWindow.class);

    private ApplicationContext applicationContext;
    private Stage primaryStage;
    private Double windowedX;
    private Double windowedY;
    private double windowedWidth = 1280;
    private double windowedHeight = 800;

    @Override
    public void stop() {
        if (applicationContext != null) {
            try {
                applicationContext.saveWindowState(currentWindowState());
            } catch (PersistenceException error) {
                LOGGER.error("Unable to save window state: {}", error.getMessage());
            }
            try {
                applicationContext.close();
            } catch (PersistenceException error) {
                LOGGER.error("Unable to save browser state: {}", error.getMessage());
            }
        }
    }

    @Override
    public void start(Stage primaryStage) {
        long applicationStarted = System.nanoTime();
        this.primaryStage = primaryStage;
        try {
            applicationContext = ApplicationContext.createDefault();
            PerformanceTracker performance =
                    applicationContext.performanceTracker();
            performance.recordNanos(
                    "startup.context",
                    System.nanoTime() - applicationStarted);
            WindowState windowState = applicationContext.initialWindowState();
            long fxmlStarted = System.nanoTime();
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("browser.fxml"));
            loader.setControllerFactory(applicationContext::createController);
            Parent root = loader.load();
            performance.recordNanos(
                    "startup.fxml", System.nanoTime() - fxmlStarted);
            BrowserController browserController = loader.getController();
            browserController.setHostServices(getHostServices());

            Scene scene = new Scene(
                    root,
                    windowState.width(),
                    windowState.height());
            scene.setFill(Color.TRANSPARENT);
            scene.getStylesheets().add(
                    getClass().getResource("flux-gx.css").toExternalForm());
            performance.measure("startup.css-layout", () -> {
                root.applyCss();
                root.layout();
            });
            installKeyboardCommands(scene, primaryStage, browserController);
            WindowResizeSupport.install(scene, primaryStage);

            primaryStage.initStyle(StageStyle.UNDECORATED);
            primaryStage.setTitle("Flux Browser");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(900);
            primaryStage.setMinHeight(640);
            if (windowState.x() != null
                    && windowState.y() != null
                    && !Screen.getScreensForRectangle(
                            windowState.x(),
                            windowState.y(),
                            windowState.width(),
                            windowState.height()).isEmpty()) {
                primaryStage.setX(windowState.x());
                primaryStage.setY(windowState.y());
            }
            windowedX = windowState.x();
            windowedY = windowState.y();
            windowedWidth = windowState.width();
            windowedHeight = windowState.height();
            installWindowStateTracking(primaryStage);
            primaryStage.setOnCloseRequest(event -> {
                try {
                    applicationContext.saveWindowState(currentWindowState());
                } catch (PersistenceException error) {
                    LOGGER.error(
                            "Unable to save window state: {}",
                            error.getMessage());
                } finally {
                    Platform.exit();
                }
            });
            primaryStage.show();
            primaryStage.setMaximized(windowState.maximized());
            primaryStage.setFullScreen(windowState.fullscreen());
            performance.recordNanos(
                    "startup.total", System.nanoTime() - applicationStarted);
            performance.logSummary(LOGGER, "startup");
        } catch (PersistenceException error) {
            LOGGER.error("Flux persistence startup failed: {}", error.getMessage());
            showPersistenceFailure(error);
        } catch (Throwable error) {
            LOGGER.error("Unable to start Flux Browser", error);
            Platform.exit();
        }
    }

    private void installWindowStateTracking(Stage stage) {
        stage.xProperty().addListener(observable -> captureWindowedBounds(stage));
        stage.yProperty().addListener(observable -> captureWindowedBounds(stage));
        stage.widthProperty().addListener(observable -> captureWindowedBounds(stage));
        stage.heightProperty().addListener(observable -> captureWindowedBounds(stage));
    }

    private void captureWindowedBounds(Stage stage) {
        if (!stage.isMaximized() && !stage.isFullScreen()) {
            windowedX = stage.getX();
            windowedY = stage.getY();
            windowedWidth = stage.getWidth();
            windowedHeight = stage.getHeight();
        }
    }

    private WindowState currentWindowState() {
        if (primaryStage == null) {
            return WindowState.defaults();
        }
        captureWindowedBounds(primaryStage);
        return new WindowState(
                windowedX,
                windowedY,
                windowedWidth,
                windowedHeight,
                primaryStage.isMaximized(),
                primaryStage.isFullScreen());
    }

    private static void showPersistenceFailure(PersistenceException error) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Flux Browser startup failed");
        alert.setHeaderText("PostgreSQL is required");
        alert.setContentText(
                error.getMessage()
                        + "\n\nFrom the Flux-Browser directory run:\n"
                        + "docker compose up -d postgres");
        alert.showAndWait();
        Platform.exit();
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
            if (event.getCode() == KeyCode.ESCAPE) {
                controller.closeTransientPanels();
                return;
            }
            if (event.isAltDown() && event.getCode() == KeyCode.LEFT) {
                controller.goBack();
                event.consume();
                return;
            }
            if (event.isAltDown() && event.getCode() == KeyCode.RIGHT) {
                controller.goForward();
                event.consume();
                return;
            }
            if (!event.isShortcutDown()) {
                return;
            }
            if (event.getCode() == KeyCode.TAB) {
                controller.selectNextTab(event.isShiftDown());
                event.consume();
                return;
            }
            if (event.isShiftDown() && event.getCode() == KeyCode.T) {
                controller.reopenClosedTab();
                event.consume();
                return;
            }
            if (event.isShiftDown() && event.getCode() == KeyCode.B) {
                controller.showSidebarPanel(SidebarPanel.BOOKMARKS);
                event.consume();
                return;
            }
            if (event.getCode().isDigitKey()) {
                int number = switch (event.getCode()) {
                    case DIGIT1, NUMPAD1 -> 1;
                    case DIGIT2, NUMPAD2 -> 2;
                    case DIGIT3, NUMPAD3 -> 3;
                    case DIGIT4, NUMPAD4 -> 4;
                    case DIGIT5, NUMPAD5 -> 5;
                    case DIGIT6, NUMPAD6 -> 6;
                    case DIGIT7, NUMPAD7 -> 7;
                    case DIGIT8, NUMPAD8 -> 8;
                    case DIGIT9, NUMPAD9 -> 9;
                    default -> -1;
                };
                if (number > 0) {
                    controller.selectTabNumber(number);
                    event.consume();
                    return;
                }
            }
            switch (event.getCode()) {
                case L -> controller.focusAddress();
                case T -> controller.newTab();
                case W -> controller.closeActiveTab();
                case R -> controller.reloadOrStop();
                case F -> controller.showFindBar();
                case P -> controller.printCurrentPage();
                case H -> controller.showSidebarPanel(SidebarPanel.HISTORY);
                case J -> controller.showSidebarPanel(SidebarPanel.DOWNLOADS);
                case COMMA -> controller.showSidebarPanel(SidebarPanel.SETTINGS);
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
