package org.custombrowser.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
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
            FXMLLoader loader = new FXMLLoader(getClass().getResource("browser.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root, 1024, 720); // setting window resolutions

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
