package org.custombrowser.ui;

/**
 * Commands exposed by the browser surface to its FXML child components.
 */
public interface BrowserActions {

    void navigate(String input);

    void showStartPage();

    void goBack();

    void goForward();

    void reloadOrStop();

    void toggleEasySetup();
}
