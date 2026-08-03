package org.custombrowser.ui.component;

import org.custombrowser.ui.BrowserActions;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public final class ErrorPageController {

    @FXML
    private Label failedAddress;

    @FXML
    private Label failureMessage;

    private BrowserActions actions;
    private Runnable retryAction;

    public void setActions(BrowserActions actions) {
        this.actions = actions;
    }

    public void showFailure(String address, String message, Runnable retryAction) {
        failedAddress.setText(address == null ? "" : address);
        failureMessage.setText(message == null
                ? "The page could not be loaded."
                : message);
        this.retryAction = retryAction;
    }

    @FXML
    private void retry() {
        if (retryAction != null) {
            retryAction.run();
        }
    }

    @FXML
    private void home() {
        if (actions != null) {
            actions.showStartPage();
        }
    }
}
