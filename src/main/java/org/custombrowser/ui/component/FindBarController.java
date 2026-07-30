package org.custombrowser.ui.component;

import org.custombrowser.ui.BrowserActions;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

public final class FindBarController {

    @FXML
    private TextField queryField;

    @FXML
    private CheckBox matchCase;

    @FXML
    private Label resultLabel;

    private BrowserActions actions;

    public void setActions(BrowserActions actions) {
        this.actions = actions;
    }

    public void focusQuery() {
        queryField.requestFocus();
        queryField.selectAll();
    }

    @FXML
    private void findNext() {
        find(false);
    }

    @FXML
    private void findPrevious() {
        find(true);
    }

    @FXML
    private void close() {
        if (actions != null) {
            actions.hideFindBar();
        }
    }

    private void find(boolean backwards) {
        boolean found = actions != null
                && actions.findInPage(
                        queryField.getText(),
                        backwards,
                        matchCase.isSelected());
        resultLabel.setText(found ? "MATCH" : "NO MATCH");
    }
}
