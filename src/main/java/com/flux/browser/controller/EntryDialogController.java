package com.flux.browser.controller;

import com.flux.browser.util.UrlResolver;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public final class EntryDialogController {
    public record Draft(String title, String url, String category) {}
    @FXML private Label heading, errorLabel;
    @FXML private TextField titleField, urlField, categoryField;
    @FXML private VBox categoryBox;
    @FXML private Button saveButton, cancelButton;
    private Stage stage;
    private Function<Draft, CompletableFuture<?>> saveAction;
    private Runnable saved;
    private boolean busy;

    public void configure(Stage stage, String heading, String title, String url, String category,
                          Function<Draft, CompletableFuture<?>> saveAction, Runnable saved) {
        this.stage = stage; this.saveAction = saveAction; this.saved = saved;
        this.heading.setText(heading);
        titleField.setText(title); urlField.setText(url);
        categoryBox.setVisible(category != null); categoryBox.setManaged(category != null);
        categoryField.setText(category == null ? "Unsorted" : category);
        stage.setOnCloseRequest(event -> { if (busy) event.consume(); });
        stage.setOnShown(event -> { titleField.requestFocus(); titleField.selectAll(); });
    }

    @FXML private void save() {
        if (saveAction == null || busy) return;
        try {
            Draft draft = new Draft(UrlResolver.requiredText(titleField.getText(), "Title", 512),
                    UrlResolver.webAddress(urlField.getText()),
                    UrlResolver.requiredText(categoryField.getText().isBlank() ? "Unsorted" : categoryField.getText(), "Folder", 80));
            setBusy(true);
            saveAction.apply(draft).whenComplete((result, error) -> Platform.runLater(() -> {
                setBusy(false);
                if (error != null) showError(BrowserController.friendlyError(error));
                else { stage.close(); saved.run(); }
            }));
        } catch (IllegalArgumentException error) {
            setBusy(false); showError(error.getMessage());
        }
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        saveButton.setDisable(busy); cancelButton.setDisable(busy);
        titleField.setDisable(busy); urlField.setDisable(busy); categoryField.setDisable(busy);
        saveButton.setText(busy ? "Saving…" : "Save destination");
    }
    private void showError(String message) {
        errorLabel.setText(message); errorLabel.setVisible(true); errorLabel.setManaged(true); stage.sizeToScene();
    }
    @FXML private void cancel() { if (stage != null && !busy) stage.close(); }
}
