package org.custombrowser.ui.component;

import org.custombrowser.ui.BrowserActions;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;

public final class NavigationBarController {

    @FXML
    private Button backButton;

    @FXML
    private Button forwardButton;

    @FXML
    private Button reloadButton;

    @FXML
    private TextField addressField;

    private BrowserActions actions;

    public void setActions(BrowserActions actions) {
        this.actions = actions;
    }

    public void setAddress(String address) {
        if (!addressField.isFocused()) {
            addressField.setText(address);
        }
    }

    public void setPageState(boolean canGoBack, boolean canGoForward, boolean loading) {
        backButton.setDisable(!canGoBack);
        forwardButton.setDisable(!canGoForward);
        reloadButton.setText(loading ? "×" : "↻");
        reloadButton.setAccessibleText(loading ? "Stop loading" : "Reload");
    }

    public void focusAddress() {
        addressField.requestFocus();
        addressField.selectAll();
    }

    @FXML
    private void submitAddress() {
        if (actions != null) {
            actions.navigate(addressField.getText());
        }
    }

    @FXML
    private void back() {
        if (actions != null) {
            actions.goBack();
        }
    }

    @FXML
    private void forward() {
        if (actions != null) {
            actions.goForward();
        }
    }

    @FXML
    private void reloadOrStop() {
        if (actions != null) {
            actions.reloadOrStop();
        }
    }

    @FXML
    private void home() {
        if (actions != null) {
            actions.showStartPage();
        }
    }

    @FXML
    private void easySetup() {
        if (actions != null) {
            actions.toggleEasySetup();
        }
    }
}
