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

    void newTab();

    void closeActiveTab();

    void reopenClosedTab();

    void showFindBar();

    void hideFindBar();

    boolean findInPage(String query, boolean backwards, boolean matchCase);

    void copyAddress();

    void openCurrentPageExternally();

    void printCurrentPage();

    void bookmarkCurrentPage();
}
