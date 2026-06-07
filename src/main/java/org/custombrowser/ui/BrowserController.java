package org.custombrowser.ui;

import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ResourceBundle;

import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TextField;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebHistory;
import javafx.scene.web.WebView;

public class BrowserController implements Initializable {

    @FXML
    private WebView webView;

    @FXML
    private TextField textField;

    private WebEngine engine;
    private WebHistory history;
    private double zoomLevel = 1.0;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        engine = webView.getEngine();
        history = engine.getHistory();

        // Keep the address bar in sync with the current page URL
        engine.locationProperty().addListener((obs, oldUrl, newUrl) -> {
            textField.setText(newUrl);
        });

        // Load a default homepage
        engine.load("https://www.google.com");
    }

    // ---- Navigation ----

    @FXML
    private void loadPage() {
        String input = textField.getText().trim();
        if (input.isEmpty()) {
            return;
        }

        String url;
        if (input.contains(" ") || !input.contains(".")) {
            // Treat as a search query if there's a space or no dot
            url = "https://www.google.com/search?q="
                    + URLEncoder.encode(input, StandardCharsets.UTF_8);
        } else if (!input.startsWith("http://")
                && !input.startsWith("https://")
                && !input.startsWith("file://")) {
            url = "https://" + input;
        } else {
            url = input;
        }

        engine.load(url);
    }

    @FXML
    private void refreshPage() {
        engine.reload();
    }

    @FXML
    private void back() {
        ObservableList<WebHistory.Entry> entries = history.getEntries();
        int currentIndex = history.getCurrentIndex();

        if (currentIndex > 0) {
            history.go(-1);
            // Update the text field to reflect the new page URL
            textField.setText(entries.get(currentIndex - 1).getUrl());
        }
    }

    @FXML
    private void forward() {
        ObservableList<WebHistory.Entry> entries = history.getEntries();
        int currentIndex = history.getCurrentIndex();

        if (currentIndex < entries.size() - 1) {
            history.go(1);
            // Update the text field to reflect the new page URL
            textField.setText(entries.get(currentIndex + 1).getUrl());
        }
    }

    // ---- Zoom ----

    @FXML
    private void zoomIn() {
        zoomLevel += 0.25;
        webView.setZoom(zoomLevel);
    }

    @FXML
    private void zoomOut() {
        zoomLevel -= 0.25;
        webView.setZoom(zoomLevel);
    }

    // ---- History ----

    @FXML
    private void displayHistory() {
        ObservableList<WebHistory.Entry> entries = history.getEntries();
        System.out.println("===== Browsing History =====");
        for (int i = 0; i < entries.size(); i++) {
            WebHistory.Entry entry = entries.get(i);
            String marker = (i == history.getCurrentIndex()) ? " <-- current" : "";
            System.out.printf("[%d] %s  |  %s%s%n",
                    i, entry.getUrl(), entry.getLastVisitedDate(), marker);
        }
        System.out.println("============================");
    }

    // ---- JavaScript Execution ----

    @FXML
    private void executeJs() {
        engine.executeScript("window.location = 'https://www.google.com'");
    }
}
