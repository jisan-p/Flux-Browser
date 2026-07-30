package org.custombrowser.ui.component;

import javafx.fxml.FXML;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

public final class TitleBarController {

    @FXML
    private HBox titleBarRoot;

    private double dragOffsetX;
    private double dragOffsetY;

    @FXML
    private void rememberDragOffset(MouseEvent event) {
        Stage stage = stage();
        if (stage != null && !stage.isMaximized()) {
            dragOffsetX = event.getScreenX() - stage.getX();
            dragOffsetY = event.getScreenY() - stage.getY();
        }
    }

    @FXML
    private void dragWindow(MouseEvent event) {
        Stage stage = stage();
        if (stage != null && !stage.isMaximized()) {
            stage.setX(event.getScreenX() - dragOffsetX);
            stage.setY(event.getScreenY() - dragOffsetY);
        }
    }

    @FXML
    private void titleBarClicked(MouseEvent event) {
        if (event.getClickCount() == 2) {
            toggleMaximized();
        }
    }

    @FXML
    private void minimize() {
        Stage stage = stage();
        if (stage != null) {
            stage.setIconified(true);
        }
    }

    @FXML
    private void toggleMaximized() {
        Stage stage = stage();
        if (stage != null) {
            stage.setMaximized(!stage.isMaximized());
        }
    }

    @FXML
    private void close() {
        Stage stage = stage();
        if (stage != null) {
            stage.close();
        }
    }

    private Stage stage() {
        if (titleBarRoot.getScene() == null
                || titleBarRoot.getScene().getWindow() == null) {
            return null;
        }
        return (Stage) titleBarRoot.getScene().getWindow();
    }
}
