package com.flux.browser.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public final class MessageDialogController {
    @FXML private Label heading, messageLabel;
    @FXML private TextField inputField;
    @FXML private Button cancelButton, acceptButton;
    private Stage stage;
    private boolean accepted;

    public void configure(String title, String message, String initial, boolean cancellable, Stage stage) {
        this.stage = stage;
        heading.setText(title);
        messageLabel.setText(message == null ? "" : message.substring(0, Math.min(message.length(), 1600)));
        inputField.setVisible(initial != null); inputField.setManaged(initial != null);
        inputField.setText(initial == null ? "" : initial);
        cancelButton.setVisible(cancellable); cancelButton.setManaged(cancellable);
        acceptButton.setText(cancellable ? "Continue" : "OK");
    }
    @FXML private void accept() { accepted = true; if (stage != null) stage.close(); }
    @FXML private void cancel() { if (stage != null) stage.close(); }
    public boolean accepted() { return accepted; }
    public String value() { return inputField.getText(); }
}
