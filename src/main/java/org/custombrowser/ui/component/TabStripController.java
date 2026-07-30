package org.custombrowser.ui.component;

import org.custombrowser.ui.BrowserActions;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public final class TabStripController {

    @FXML
    private Label tabTitle;

    private BrowserActions actions;

    public void setActions(BrowserActions actions) {
        this.actions = actions;
    }

    public void setTitle(String title) {
        tabTitle.setText(title == null || title.isBlank() ? "Start Page" : title);
    }

    @FXML
    private void showStartPage() {
        if (actions != null) {
            actions.showStartPage();
        }
    }
}
