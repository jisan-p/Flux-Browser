package org.custombrowser.ui;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

public class BrowserWindow extends Application { // Inheriting from Application class

    @Override
    public void stop() throws Exception { // Must override this virtual function
        super.stop();
        System.exit(0); // Forcibly kills all background threads (like HttpClient's pool) when the window is closed
    }

    @Override
    public void start(Stage primaryStage) { // Must override this virtual function

        try {

            TextField addressBar = new TextField();

            WebView webView = new WebView();
            WebEngine webEngine = webView.getEngine();

            VBox vBox = new VBox(addressBar, webView); // stacks the addressbar and webview together
            VBox.setVgrow(webView, Priority.ALWAYS); // Ensures the webview fills the remaining window space

            addressBar.setOnAction(event -> {
                String input = addressBar.getText().trim();
                if (input.isEmpty()) {
                    return;
                }

                String url;
                if (input.contains(" ") || !input.contains(".")) {
                    // Treat as search query if there's a space or no dot (like "hello world" or "weather")
                    url = "https://www.google.com/search?q=" + URLEncoder.encode(input, StandardCharsets.UTF_8);
                } else if (!input.startsWith("http://") && !input.startsWith("https://") && !input.startsWith("file://")) {
                    // Auto-append https:// if it looks like a domain but lacks protocol
                    url = "https://" + input;
                } else {
                    url = input;
                }
                
                webEngine.load(url);
            });

            Scene scene = new Scene(vBox, 800, 600); // setting window resolutions

            primaryStage.setTitle("Weird Browser");
            primaryStage.setScene(scene);

            // Ensure the window triggers the stop() method when the 'X' button is clicked
            primaryStage.setOnCloseRequest(event -> {
                Platform.exit();
            });

            primaryStage.show();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
